/**
 * Attempt limiting for the emailed verification code.
 *
 * WHAT THIS IS AND IS NOT
 * -----------------------
 * This is the **app-level** half of the protection. It burns the current code
 * after a handful of wrong guesses and makes the customer request a fresh one,
 * which is what stops a person (or a script driving this page) from sitting on
 * one code and walking the keyspace.
 *
 * It is deliberately *not* claimed to be the security boundary. The browser
 * talks to Supabase Auth directly, so an attacker who skips this page can call
 * the same endpoint with curl and never touch this counter. The control that
 * actually bounds a scripted attack is Supabase's own per-user verification
 * rate limit plus the code's one-hour expiry — see `docs/AUTH_OTP.md`. This file
 * exists so honest customers get a clear, non-punitive stop instead of silently
 * hammering a dead code, and so the easy path is also the bounded one.
 *
 * The rule is "burn the code", not "lock the account": after the limit the
 * customer requests a new code and continues immediately. A hard one-hour
 * account lock would punish someone who simply mistyped, and would be trivially
 * bypassed anyway (it lives in their own browser).
 */

/** Wrong guesses allowed against a single code. */
export const MAX_OTP_ATTEMPTS = 5;

/** A lock never outlives the code itself. Supabase codes expire in an hour. */
export const OTP_LOCK_MS = 60 * 60 * 1000;

export type OtpAttemptState = {
  /** Wrong codes entered since the last successful verification or resend. */
  failures: number;
  /** Epoch ms until which no further attempt is accepted. 0 = not locked. */
  lockedUntil: number;
};

export const EMPTY_ATTEMPTS: OtpAttemptState = { failures: 0, lockedUntil: 0 };

/** Record one wrong code, locking once the limit is reached. */
export function registerFailure(
  state: OtpAttemptState,
  now: number,
  limit: number = MAX_OTP_ATTEMPTS,
): OtpAttemptState {
  // Already burned: keep the existing deadline. Recomputing it from `now` would
  // let every further attempt push the unlock further out, so a customer who
  // keeps tapping would never be let back in.
  if (isLocked(state, now)) return state;

  const failures = state.failures + 1;
  if (failures >= limit) {
    return { failures, lockedUntil: now + OTP_LOCK_MS };
  }
  return { failures, lockedUntil: 0 };
}

/** How many attempts remain before the code is burned. */
export function attemptsLeft(
  state: OtpAttemptState,
  now: number,
  limit: number = MAX_OTP_ATTEMPTS,
): number {
  if (isLocked(state, now)) return 0;
  return Math.max(0, limit - state.failures);
}

/** True while the current code is burned. An expired lock reads as unlocked. */
export function isLocked(state: OtpAttemptState, now: number): boolean {
  return state.lockedUntil > now;
}

/** Milliseconds left on the lock, rounded up to whole seconds. */
export function lockRemainingMs(state: OtpAttemptState, now: number): number {
  return Math.max(0, state.lockedUntil - now);
}

/** Human countdown, e.g. "48 min" / "under a minute". */
export function formatLockRemaining(state: OtpAttemptState, now: number): string {
  const remaining = lockRemainingMs(state, now);
  if (remaining <= 0) return "";
  const minutes = Math.ceil(remaining / 60_000);
  if (minutes <= 1) return "under a minute";
  return `${minutes} min`;
}

/* -------------------------------------------------------------------------- */
/* Storage                                                                    */
/* -------------------------------------------------------------------------- */

/**
 * One record per email, so signing up with a second address is not penalised
 * for the first one's mistakes. The address itself is not stored — only a
 * short hash — so a shared laptop does not leak who else signed up on it.
 */
const STORAGE_PREFIX = "vfm.otp.attempts.";

/** FNV-1a. Not cryptographic, and does not need to be: it is a bucket key. */
function hashEmail(email: string): string {
  let hash = 0x811c9dc5;
  const normalised = email.trim().toLowerCase();
  for (let i = 0; i < normalised.length; i += 1) {
    hash ^= normalised.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(36);
}

function storageKey(email: string): string {
  return `${STORAGE_PREFIX}${hashEmail(email)}`;
}

/**
 * Read the stored state. Every failure mode — no `window`, privacy mode,
 * corrupted JSON — degrades to "no attempts recorded" rather than throwing in
 * the middle of a form submit.
 */
export function readAttempts(email: string): OtpAttemptState {
  if (typeof window === "undefined") return EMPTY_ATTEMPTS;
  try {
    const raw = window.localStorage.getItem(storageKey(email));
    if (!raw) return EMPTY_ATTEMPTS;
    const parsed = JSON.parse(raw) as Partial<OtpAttemptState>;
    return {
      failures: Number.isFinite(parsed.failures) ? Number(parsed.failures) : 0,
      lockedUntil: Number.isFinite(parsed.lockedUntil) ? Number(parsed.lockedUntil) : 0,
    };
  } catch {
    return EMPTY_ATTEMPTS;
  }
}

export function writeAttempts(email: string, state: OtpAttemptState): void {
  if (typeof window === "undefined") return;
  try {
    if (state.failures === 0 && state.lockedUntil === 0) {
      window.localStorage.removeItem(storageKey(email));
    } else {
      window.localStorage.setItem(storageKey(email), JSON.stringify(state));
    }
  } catch {
    // Storage full or blocked — the limit degrades to per-page only.
  }
  notify(storageKey(email));
}

/** Forget the record: a new code was requested, or verification succeeded. */
export function clearAttempts(email: string): void {
  writeAttempts(email, EMPTY_ATTEMPTS);
}

/* -------------------------------------------------------------------------- */
/* External store                                                             */
/* -------------------------------------------------------------------------- */

/**
 * `useSyncExternalStore` glue.
 *
 * WHY NOT `useState` + `useEffect`: the lock has to survive a page reload, so
 * the first client render wants to read `localStorage` — but the server render
 * cannot, and reading it during render produces a hydration mismatch. Reading
 * it in an effect instead paints an unlocked form first, which is a flash of
 * "you can try again" over a code that is actually burned.
 *
 * This is the primitive that exists for exactly that: the server snapshot is
 * used for SSR and hydration, the client snapshot takes over immediately after.
 *
 * Snapshots are cached because `getSnapshot` must return a referentially stable
 * value; returning a fresh object every call would loop forever.
 */
const snapshots = new Map<string, OtpAttemptState>();
const listeners = new Map<string, Set<() => void>>();

function notify(key: string): void {
  snapshots.delete(key);
  const set = listeners.get(key);
  if (!set) return;
  for (const listener of set) listener();
}

export function attemptsSnapshot(email: string): OtpAttemptState {
  const key = storageKey(email);
  const cached = snapshots.get(key);
  if (cached) return cached;
  const value = readAttempts(email);
  snapshots.set(key, value);
  return value;
}

export function subscribeAttempts(email: string, onChange: () => void): () => void {
  const key = storageKey(email);
  const set = listeners.get(key) ?? new Set<() => void>();
  set.add(onChange);
  listeners.set(key, set);

  // Another tab burning the same code should lock this one too.
  const onStorage = (event: StorageEvent) => {
    if (event.key !== null && event.key !== key) return;
    snapshots.delete(key);
    onChange();
  };
  if (typeof window !== "undefined") {
    window.addEventListener("storage", onStorage);
  }

  return () => {
    set.delete(onChange);
    if (set.size === 0) listeners.delete(key);
    if (typeof window !== "undefined") {
      window.removeEventListener("storage", onStorage);
    }
  };
}
