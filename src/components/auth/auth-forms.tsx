"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Check, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/menu";
import { Field, Input } from "@/components/ui/input";
import { OrDivider } from "@/components/auth/auth-shell";
import { authInputClass, authSubmitClass } from "@/components/auth/auth-card";
import { OtpInput } from "@/components/auth/otp-input";
import { PasswordInput } from "@/components/auth/password-input";
import { isCompleteOtp } from "@/lib/otp";
import {
  attemptsLeft,
  attemptsSnapshot,
  clearAttempts,
  EMPTY_ATTEMPTS,
  formatLockRemaining,
  isLocked,
  registerFailure,
  subscribeAttempts,
  writeAttempts,
} from "@/lib/otp-attempts";
import {
  forgotPasswordSchema,
  resetPasswordSchema,
  signInSchema,
  signUpSchema,
  usernameSchema,
  type ForgotPasswordInput,
  type ResetPasswordInput,
  type SignInInput,
  type SignUpInput,
} from "@/lib/validation";
import {
  checkUsername,
  isEmailUnconfirmed,
  resendSignupOtp,
  sendRecoveryOtp,
  sendSignupOtp,
  signInWithGoogle,
  signInWithPassword,
  updatePassword,
  verifyRecoveryOtp,
  verifySignupOtp,
} from "@/services/auth-service";

/**
 * Auth forms.
 *
 * Every form is a real Supabase Auth call — there is no simulated success path.
 * Provider errors are mapped to actionable copy by `friendlyAuthError`, and an
 * unavailable provider (e.g. Google not yet enabled on the project) produces an
 * explicit message rather than a silent no-op.
 */

/** Shared Google button. */
function GoogleButton({ next, label }: { next: string; label: string }) {
  const [working, setWorking] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  return (
    <div>
      <Button
        type="button"
        variant="outline"
        block
        loading={working}
        loadingLabel="Opening Google…"
        onClick={async () => {
          setWorking(true);
          setError(null);
          const result = await signInWithGoogle(next);
          if (!result.ok) {
            setError(result.error);
            setWorking(false);
          }
          // On success the browser is already navigating to Google.
        }}
      >
        <GoogleGlyph />
        {label}
      </Button>
      {error ? (
        <p role="alert" className="mt-2 text-small text-danger">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function GoogleGlyph() {
  return (
    <svg viewBox="0 0 18 18" className="size-[18px]" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62Z"
      />
      <path
        fill="#34A853"
        d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.8.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.7H.96v2.33A9 9 0 0 0 9 18Z"
      />
      <path
        fill="#FBBC05"
        d="M3.97 10.72a5.4 5.4 0 0 1 0-3.44V4.95H.96a9 9 0 0 0 0 8.1l3.01-2.33Z"
      />
      <path
        fill="#EA4335"
        d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.9 11.43 0 9 0A9 9 0 0 0 .96 4.95l3.01 2.33C4.68 5.16 6.66 3.58 9 3.58Z"
      />
    </svg>
  );
}

/** Where to send the customer after a successful auth. */
function safeNext(raw: string | null): string {
  if (!raw) return "/";
  // Only same-origin relative paths — an open redirect via ?next= is a real,
  // commonly-exploited bug and costs nothing to prevent.
  if (!raw.startsWith("/") || raw.startsWith("//")) return "/";
  return raw;
}

/* ========================================================================== */
/* Sign in                                                                    */
/* ========================================================================== */

export function LoginForm({ googleEnabled = false }: { googleEnabled?: boolean }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(searchParams.get("next"));
  const confirmed = searchParams.get("confirmed") === "1";

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<SignInInput>({ resolver: zodResolver(signInSchema) });

  /** Set only when the provider rejected the sign-in for an unconfirmed address. */
  const [unconfirmedEmail, setUnconfirmedEmail] = React.useState<string | null>(null);

  async function onSubmit(values: SignInInput) {
    setUnconfirmedEmail(null);

    const result = await signInWithPassword(values);
    if (!result.ok) {
      setError("root", { message: result.error });
      if (isEmailUnconfirmed(result.code, result.error)) {
        setUnconfirmedEmail(values.email);
      }
      return;
    }
    router.push(next);
    router.refresh();
  }

  return (
    <div>
      {confirmed ? (
        <p className="mb-4 flex items-start gap-2 rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success">
          <Check className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Your email is confirmed. Please sign in to continue.
        </p>
      ) : null}

      {/* Rendered only when the Supabase project actually has Google enabled
          (probed server-side). A sign-in button that always errors would be a
          fake control, which this project does not ship. */}
      {googleEnabled ? (
        <>
          <GoogleButton next={next} label="Log in with Google" />
          <OrDivider />
        </>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {/* Suppressed when the dedicated unconfirmed panel below is shown —
            two copies of the same message reads as a bug. */}
        {errors.root && !unconfirmedEmail ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {errors.root.message}
          </p>
        ) : null}

        <Field label="Email" htmlFor="email" error={errors.email?.message} required>
          <Input
            id="email"
            type="email"
            inputMode="email"
            autoComplete="email"
            autoFocus
            placeholder="you@example.com"
            invalid={Boolean(errors.email)}
            className={authInputClass}
            {...register("email")}
          />
        </Field>

        <Field
          label="Password"
          htmlFor="password"
          error={errors.password?.message}
          required
        >
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="Your password"
            invalid={Boolean(errors.password)}
            className={authInputClass}
            {...register("password")}
          />
        </Field>

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass}
          loading={isSubmitting}
          loadingLabel="Signing in…"
        >
          Sign in
        </Button>
      </form>

      {/*
        An unconfirmed customer has no working password yet — the signup flow
        only applies it *after* the code is verified — so "resend the
        confirmation link" would be a dead end: the email now carries a code and
        this page has nowhere to type it. Send them to the sign-up page, which
        re-issues a code and lets them finish.
      */}
      {unconfirmedEmail ? (
        <div
          role="alert"
          className="mt-4 rounded-md border border-marigold-200 bg-marigold-50 px-3 py-3"
        >
          <p className="text-small font-medium text-ink">Please verify your email first</p>
          <p className="mt-1 text-small text-ink-muted">
            We sent a 6-digit code to{" "}
            <span className="font-medium text-ink">{unconfirmedEmail}</span>. Enter it on
            the sign-up page to finish setting up your account, then sign in here.
          </p>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="mt-2.5"
            onClick={() => router.push("/signup")}
          >
            Verify my email
          </Button>
        </div>
      ) : null}

      <Link
        href="/forgot-password"
        className="mt-4 block text-center text-small text-ink-muted underline-offset-2 hover:text-ink hover:underline"
      >
        Request a New Password
      </Link>
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* OTP step — shared by signup and password recovery                          */
/* -------------------------------------------------------------------------- */

/**
 * Seconds before a replacement code may be requested.
 *
 * Supabase's own mailer is rate-limited, so a customer who taps "resend" three
 * times in ten seconds gets a "you can only request this after 47 seconds"
 * error instead of a code. Holding the button for 30 seconds prevents that
 * conversation entirely and is long enough for the first email to arrive.
 */
const RESEND_COOLDOWN_SECONDS = 30;

function OtpStep({
  email,
  submitLabel = "Verify code",
  onSubmit,
  onResend,
  onChangeEmail,
  error,
  verifying = false,
  resending = false,
}: {
  email: string;
  submitLabel?: string;
  /** Resolve `true` only when the code was accepted. */
  onSubmit: (token: string) => Promise<boolean>;
  /** Resolve `true` only when a new code was actually sent. */
  onResend: () => Promise<boolean>;
  onChangeEmail: () => void;
  error?: string | null;
  verifying?: boolean;
  resending?: boolean;
}) {
  const [token, setToken] = React.useState("");
  const [cooldown, setCooldown] = React.useState(RESEND_COOLDOWN_SECONDS);
  const [now, setNow] = React.useState(() => Date.now());
  // Guards against a double submit from a double click or an impatient
  // Enter key while the first request is still in flight.
  const submitting = React.useRef(false);

  // The record follows the address, so switching email starts from a clean
  // slate and a second account is never punished for the first one. Read
  // through an external store so a reload shows the real state without a
  // hydration mismatch (see lib/otp-attempts).
  const attempts = React.useSyncExternalStore(
    React.useCallback((onChange: () => void) => subscribeAttempts(email, onChange), [email]),
    React.useCallback(() => attemptsSnapshot(email), [email]),
    React.useCallback(() => EMPTY_ATTEMPTS, []),
  );

  // While the code is burned, keep the clock moving so the countdown is honest
  // and the form unlocks by itself the moment the lock expires.
  React.useEffect(() => {
    if (!isLocked(attempts, now)) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [attempts, now]);

  React.useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown((left) => left - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const locked = isLocked(attempts, now);
  const remaining = attemptsLeft(attempts, now);
  const complete = isCompleteOtp(token);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!complete || verifying || submitting.current || locked) return;
    submitting.current = true;
    try {
      const accepted = await onSubmit(token);
      if (accepted) {
        clearAttempts(email);
        return;
      }
      // Wrong code: burn one attempt and empty the boxes so the next try
      // starts clean rather than sitting on six known-wrong digits.
      writeAttempts(email, registerFailure(attempts, Date.now()));
      setNow(Date.now());
      setToken("");
    } finally {
      submitting.current = false;
    }
  }

  async function handleResend() {
    if (resending || cooldown > 0) return;
    const sent = await onResend();
    if (!sent) return;
    // A fresh code is a fresh budget — that is the whole point of "burn the
    // code, not the account".
    clearAttempts(email);
    setToken("");
    setNow(Date.now());
    setCooldown(RESEND_COOLDOWN_SECONDS);
  }

  return (
    <div>
      <h2 className="font-display text-h2 text-ink">Verify your email</h2>
      <p className="mt-1.5 text-small text-ink-muted">
        We&rsquo;ve sent a verification email to your email address.
      </p>

      <div className="mt-4 rounded-xl border border-line bg-surface-muted px-3.5 py-3 text-small">
        <p className="text-ink-muted">Sent to</p>
        <p className="mt-0.5 break-all font-medium text-ink">{email}</p>
      </div>

      {locked ? (
        <div
          role="alert"
          className="mt-3 rounded-md border border-marigold-200 bg-marigold-50 px-3 py-2.5 text-small"
        >
          <p className="font-medium text-ink">Too many incorrect codes</p>
          <p className="mt-1 text-ink-muted">
            This code can no longer be used. Request a new one to continue
            {formatLockRemaining(attempts, now)
              ? ` — or wait ${formatLockRemaining(attempts, now)}.`
              : "."}
          </p>
        </div>
      ) : error ? (
        <p
          role="alert"
          className="mt-3 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {error}
        </p>
      ) : null}

      <form className="mt-5" onSubmit={handleSubmit} noValidate>
        <p id="otp-instructions" className="mb-3 text-center text-small text-ink-muted">
          {locked
            ? "Request a new code above to try again."
            : "Enter the 6-digit code from that email. It expires in about an hour."}
        </p>

        <OtpInput
          value={token}
          onChange={setToken}
          disabled={verifying || locked}
          invalid={Boolean(error) && !locked}
          describedBy="otp-instructions"
          autoFocus
        />

        {!locked && remaining <= 2 ? (
          <p className="mt-2 text-center text-small text-marigold-700" role="status">
            {remaining === 1 ? "1 attempt left" : `${remaining} attempts left`}
          </p>
        ) : null}

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass + " mt-5"}
          loading={verifying}
          loadingLabel="Verifying…"
          disabled={!complete || locked}
        >
          {submitLabel}
        </Button>
      </form>

      <div className="mt-4 flex flex-col items-center gap-2 text-small">
        <p className="text-ink-muted">
          Didn&rsquo;t get it?{" "}
          {cooldown > 0 && !locked ? (
            <span className="text-ink-subtle">Resend in {cooldown}s</span>
          ) : (
            <button
              type="button"
              onClick={handleResend}
              disabled={resending}
              className="font-medium text-leaf-700 underline-offset-2 hover:underline disabled:cursor-not-allowed disabled:text-ink-subtle"
            >
              {resending ? "Sending…" : "Send a new code"}
            </button>
          )}
        </p>
        <button
          type="button"
          onClick={onChangeEmail}
          disabled={verifying}
          className="text-ink-muted underline-offset-2 hover:text-ink hover:underline disabled:cursor-not-allowed disabled:text-ink-subtle"
        >
          Change email address
        </button>
        {/*
          The Supabase email template on the free tier renders a link and no
          code (templates are locked until a custom SMTP provider is set up —
          see docs/AUTH_OTP.md). Without this line those customers sit on a form
          with nothing to type, so the link is offered as the fallback rather
          than pretending it does not exist.
        */}
        <p className="mt-1 text-center text-ink-subtle">
          Email shows a button instead of a code? Open it — that verifies you straight
          away.
        </p>
      </div>
    </div>
  );
}

/* ========================================================================== */
/* Sign up                                                                    */
/* ========================================================================== */

export function SignUpForm({
  googleEnabled = false,
  nextPath,
}: {
  googleEnabled?: boolean;
  /** Resolved server-side so the form does not depend on the query string. */
  nextPath?: string;
}) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const next = safeNext(nextPath ?? searchParams.get("next"));

  /** Result of the last completed lookup, tagged with the name it was for. */
  const [lookup, setLookup] = React.useState<{
    for: string;
    available: boolean;
  } | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SignUpInput>({
    resolver: zodResolver(signUpSchema),
    // `acceptedTerms` is a `z.literal(true)`, so it must start unset — the form
    // cannot be submitted without an explicit tick.
    defaultValues: { acceptedTerms: false as unknown as true },
  });

  // `useWatch` rather than `form.watch()`: the latter returns an unmemoizable
  // function, which makes the React Compiler skip this whole component.
  const usernameWatch = useWatch({ control, name: "username" });
  const usernameValue = (usernameWatch ?? "").trim().toLowerCase();
  const usernameSyntaxValid =
    usernameValue.length > 0 && usernameSchema.safeParse(usernameValue).success;

  /**
   * Live username availability via `check_username_available()`.
   *
   * Debounced to one request per 400ms of typing, and only fired once the name is
   * syntactically valid — there is no point asking the server about `"a"`.
   * The in-flight state is *derived* rather than stored, so nothing sets state
   * synchronously during a render or effect body; only the resolved promise does.
   */
  React.useEffect(() => {
    if (!usernameSyntaxValid) return;

    let cancelled = false;
    const timer = window.setTimeout(() => {
      void checkUsername(usernameValue).then((available) => {
        if (cancelled) return;
        setLookup({ for: usernameValue, available });
      });
    }, 400);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [usernameValue, usernameSyntaxValid]);

  const usernameState: "idle" | "checking" | "free" | "taken" | "invalid" =
    usernameValue.length === 0
      ? "idle"
      : !usernameSyntaxValid
        ? "invalid"
        : lookup?.for === usernameValue
          ? lookup.available
            ? "free"
            : "taken"
          : "checking";

  // --- Email OTP sign-up state ---
  // Step 1: profile + password form. Step 2: enter the emailed code. Only after
  // the code is verified do we apply the chosen password to the new account.
  const [step, setStep] = React.useState<"form" | "otp">("form");
  const [otpEmail, setOtpEmail] = React.useState("");
  const [otpPassword, setOtpPassword] = React.useState("");
  const [otpMeta, setOtpMeta] = React.useState({
    firstName: "",
    lastName: "",
    username: "",
  });
  /** When the pending code was requested — used to spot an existing account. */
  const [otpRequestedAt, setOtpRequestedAt] = React.useState<number | undefined>();
  const [otpError, setOtpError] = React.useState<string | null>(null);
  const [sendingOtp, setSendingOtp] = React.useState(false);
  const [verifyingOtp, setVerifyingOtp] = React.useState(false);
  const [resending, setResending] = React.useState(false);

  async function onSubmit(values: SignUpInput) {
    // Re-check server truth at submit time to close the race between the last
    // debounced lookup and the submit click. If the name was taken in the
    // meantime, block the submit with an actionable message rather than letting
    // the database 23505 bubble as a generic error.
    const submitName = values.username.trim().toLowerCase();
    if (usernameState === "taken") {
      const stillTaken = !(await checkUsername(submitName));
      if (stillTaken) {
        setError("username", { message: "That username is already taken" });
        return;
      }
    } else if (usernameState === "checking") {
      const available = await checkUsername(submitName);
      if (!available) {
        setError("username", { message: "That username is already taken" });
        return;
      }
    }

    setSendingOtp(true);
    setOtpError(null);
    const result = await sendSignupOtp({
      email: values.email,
      firstName: values.firstName,
      lastName: values.lastName ?? "",
      username: values.username,
    });
    setSendingOtp(false);

    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }

    setOtpEmail(values.email);
    setOtpPassword(values.password);
    setOtpMeta({
      firstName: values.firstName,
      lastName: values.lastName ?? "",
      username: values.username,
    });
    setOtpRequestedAt(result.data.sentAt);
    setStep("otp");
  }

  async function confirmOtp(token: string): Promise<boolean> {
    setVerifyingOtp(true);
    setOtpError(null);

    const result = await verifySignupOtp({
      email: otpEmail,
      token,
      requestedAt: otpRequestedAt,
    });
    if (!result.ok) {
      setOtpError(result.error);
      setVerifyingOtp(false);
      return false;
    }

    // The address already had an account, so this code was a sign-in rather
    // than a sign-up. The customer is authenticated now; writing the password
    // from the signup form over it would silently change their credentials.
    if (result.data.existingAccount) {
      router.push(next);
      router.refresh();
      return true;
    }

    // A brand-new account: apply the password chosen on the form so they can
    // also sign in with email + password later.
    const pw = await updatePassword(otpPassword);
    if (!pw.ok) {
      // They are authenticated either way. Trapping them on the code screen
      // would be worse than landing them in the app, where the account page
      // can set a password; recovery covers the gap if they never do.
      console.error("[auth] could not set the signup password:", pw.error);
    }

    router.push(next);
    router.refresh();
    return true;
  }

  async function resendOtp(): Promise<boolean> {
    setResending(true);
    setOtpError(null);
    const result = await resendSignupOtp({ email: otpEmail, ...otpMeta });
    setResending(false);
    if (!result.ok) {
      setOtpError(result.error);
      return false;
    }
    setOtpRequestedAt(result.data.sentAt);
    return true;
  }

  if (step === "otp") {
    return (
      <OtpStep
        email={otpEmail}
        submitLabel="Verify & continue"
        onSubmit={confirmOtp}
        onResend={resendOtp}
        onChangeEmail={() => {
          setStep("form");
          setOtpError(null);
        }}
        error={otpError}
        verifying={verifyingOtp}
        resending={resending}
      />
    );
  }

  return (
    <div>
      {googleEnabled ? (
        <>
          <GoogleButton next={next} label="Sign up with Google" />
          <OrDivider />
        </>
      ) : null}

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        {errors.root ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {errors.root.message}
          </p>
        ) : null}

        <div className="grid gap-x-3 sm:grid-cols-2">
          <Field
            label="First name"
            htmlFor="firstName"
            error={errors.firstName?.message}
            required
          >
            <Input
              id="firstName"
              autoComplete="given-name"
              autoFocus
              placeholder="Asha"
              invalid={Boolean(errors.firstName)}
              className={authInputClass}
              {...register("firstName")}
            />
          </Field>
          <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}>
            <Input
              id="lastName"
              autoComplete="family-name"
              placeholder="Kulkarni"
              invalid={Boolean(errors.lastName)}
              className={authInputClass}
              {...register("lastName")}
            />
          </Field>
        </div>

        <Field label="Email" htmlFor="signup-email" error={errors.email?.message} required>
          <Input
            id="signup-email"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="you@example.com"
            invalid={Boolean(errors.email)}
            className={authInputClass}
            {...register("email")}
          />
        </Field>

        <Field
          label="Username"
          htmlFor="username"
          error={errors.username?.message}
          hint="Lowercase letters, numbers, underscores."
          required
          className="mb-2"
        >
          <div className="relative">
            <Input
              id="username"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="asha_k"
              invalid={Boolean(errors.username) || usernameState === "taken"}
              className={authInputClass}
              {...register("username")}
            />
            <UsernameStatus state={usernameState} />
          </div>
        </Field>

        <Field
          label="Password"
          htmlFor="signup-password"
          error={errors.password?.message}
          hint="At least 8 characters."
          required
          className="mb-2"
        >
          <PasswordInput
            id="signup-password"
            autoComplete="new-password"
            placeholder="Choose a password"
            invalid={Boolean(errors.password)}
            className={authInputClass}
            {...register("password")}
          />
        </Field>

        <div className="mb-4">
          <label className="flex cursor-pointer items-start gap-2.5">
            {/* Radix Checkbox is not a native input, so it is wired through
                react-hook-form's Controller rather than `register`. */}
            <Controller
              name="acceptedTerms"
              control={control}
              render={({ field }) => (
                <Checkbox
                  className="mt-0.5"
                  aria-describedby="terms-note"
                  checked={field.value === true}
                  onCheckedChange={(checked) => field.onChange(checked === true)}
                  onBlur={field.onBlur}
                  ref={field.ref}
                />
              )}
            />
            <span id="terms-note" className="text-small text-ink-muted">
              I agree to the{" "}
              <Link href="/terms" className="text-leaf-700 underline underline-offset-2">
                terms
              </Link>{" "}
              and{" "}
              <Link href="/privacy" className="text-leaf-700 underline underline-offset-2">
                privacy policy
              </Link>
              .
            </span>
          </label>
          {errors.acceptedTerms ? (
            <p role="alert" className="mt-1 text-small text-danger">
              {errors.acceptedTerms.message}
            </p>
          ) : null}
        </div>

        <Button
          type="submit"
          block
          size="lg"
          className={authSubmitClass}
          loading={isSubmitting || sendingOtp}
          loadingLabel="Sending code…"
        >
          Create account
        </Button>
      </form>
    </div>
  );
}

function UsernameStatus({
  state,
}: {
  state: "idle" | "checking" | "free" | "taken" | "invalid";
}) {
  if (state === "idle") return null;

  const config = {
    checking: { text: "Checking…", className: "text-ink-subtle", Icon: null },
    free: {
      text: "Available",
      className: "text-success",
      Icon: Check,
    },
    taken: { text: "Already taken", className: "text-danger", Icon: X },
    invalid: { text: "3–24 chars: a–z, 0–9, _", className: "text-ink-subtle", Icon: null },
  }[state];

  const Icon = config.Icon;

  return (
    <span
      aria-live="polite"
      className={`pointer-events-none absolute right-3 top-1/2 flex -translate-y-1/2 items-center gap-1 text-small ${config.className}`}
    >
      {Icon ? <Icon className="size-3.5" strokeWidth={3} aria-hidden="true" /> : null}
      {config.text}
    </span>
  );
}

/* ========================================================================== */
/* Forgot password                                                            */
/* ========================================================================== */

export function ForgotPasswordForm() {
  const router = useRouter();
  const [step, setStep] = React.useState<"form" | "otp" | "new-password">("form");
  const [email, setEmail] = React.useState("");
  const [otpError, setOtpError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [resending, setResending] = React.useState(false);
  const [done, setDone] = React.useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ForgotPasswordInput>({ resolver: zodResolver(forgotPasswordSchema) });

  const resetForm = useForm<ResetPasswordInput>({ resolver: zodResolver(resetPasswordSchema) });

  async function requestCode(values: ForgotPasswordInput) {
    setBusy(true);
    setOtpError(null);
    const result = await sendRecoveryOtp(values.email);
    setBusy(false);
    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    setEmail(values.email);
    setStep("otp");
  }

  async function confirmOtp(token: string): Promise<boolean> {
    setBusy(true);
    setOtpError(null);
    const result = await verifyRecoveryOtp({ email, token });
    setBusy(false);
    if (!result.ok) {
      setOtpError(result.error);
      return false;
    }
    setStep("new-password");
    return true;
  }

  async function resend(): Promise<boolean> {
    setResending(true);
    setOtpError(null);
    const result = await sendRecoveryOtp(email);
    setResending(false);
    if (!result.ok) {
      setOtpError(result.error);
      return false;
    }
    return true;
  }

  async function submitNewPassword(values: ResetPasswordInput) {
    setBusy(true);
    const result = await updatePassword(values.password);
    setBusy(false);
    if (!result.ok) {
      resetForm.setError("root", { message: result.error });
      return;
    }
    setDone(true);
    window.setTimeout(() => router.push("/login"), 1500);
  }

  if (step === "otp") {
    return (
      <OtpStep
        email={email}
        submitLabel="Verify & continue"
        onSubmit={confirmOtp}
        onResend={resend}
        onChangeEmail={() => setStep("form")}
        error={otpError}
        verifying={busy}
        resending={resending}
      />
    );
  }

  if (step === "new-password") {
    if (done) {
      return (
        <p
          role="status"
          className="rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success"
        >
          Your password has been updated. Taking you to sign in…
        </p>
      );
    }

    return (
      <form onSubmit={resetForm.handleSubmit(submitNewPassword)} noValidate>
        {resetForm.formState.errors.root ? (
          <p
            role="alert"
            className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
          >
            {resetForm.formState.errors.root.message}
          </p>
        ) : null}

        <p className="mb-4 text-small text-ink-muted">
          We have verified your email. Choose a new password.
        </p>

        <Field
          label="New password"
          htmlFor="fp-new-password"
          error={resetForm.formState.errors.password?.message}
          hint="At least 8 characters."
          required
        >
          <Input
            id="fp-new-password"
            type="password"
            autoComplete="new-password"
            autoFocus
            invalid={Boolean(resetForm.formState.errors.password)}
            {...resetForm.register("password")}
          />
        </Field>

        <Field
          label="Confirm new password"
          htmlFor="fp-confirm-password"
          error={resetForm.formState.errors.confirmPassword?.message}
          required
        >
          <Input
            id="fp-confirm-password"
            type="password"
            autoComplete="new-password"
            invalid={Boolean(resetForm.formState.errors.confirmPassword)}
            {...resetForm.register("confirmPassword")}
          />
        </Field>

        <Button
          type="submit"
          block
          size="lg"
          loading={busy}
          loadingLabel="Updating…"
        >
          Update password
        </Button>
      </form>
    );
  }

  return (
    <form onSubmit={handleSubmit(requestCode)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <Field label="Email" htmlFor="reset-email" error={errors.email?.message} required>
        <Input
          id="reset-email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoFocus
          invalid={Boolean(errors.email)}
          {...register("email")}
        />
      </Field>

      <Button type="submit" block size="lg" loading={isSubmitting || busy} loadingLabel="Sending code…">
        Send code
      </Button>
    </form>
  );
}

/* ========================================================================== */
/* Reset password (post-recovery)                                             */
/* ========================================================================== */

export function ResetPasswordForm() {
  const router = useRouter();
  const [done, setDone] = React.useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ResetPasswordInput>({ resolver: zodResolver(resetPasswordSchema) });

  async function onSubmit(values: ResetPasswordInput) {
    const result = await updatePassword(values.password);
    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    setDone(true);
    window.setTimeout(() => {
      router.push("/account");
      router.refresh();
    }, 1500);
  }

  if (done) {
    return (
      <p
        role="status"
        className="rounded-md border border-success-bg bg-success-bg px-3 py-2.5 text-small text-success"
      >
        Your password has been updated. Taking you to your account…
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <Field
        label="New password"
        htmlFor="new-password"
        error={errors.password?.message}
        hint="At least 8 characters."
        required
      >
        <Input
          id="new-password"
          type="password"
          autoComplete="new-password"
          autoFocus
          invalid={Boolean(errors.password)}
          {...register("password")}
        />
      </Field>

      <Field
        label="Confirm new password"
        htmlFor="confirm-password"
        error={errors.confirmPassword?.message}
        required
      >
        <Input
          id="confirm-password"
          type="password"
          autoComplete="new-password"
          invalid={Boolean(errors.confirmPassword)}
          {...register("confirmPassword")}
        />
      </Field>

      <Button type="submit" block size="lg" loading={isSubmitting} loadingLabel="Updating…">
        Update password
      </Button>
    </form>
  );
}
