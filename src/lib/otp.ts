/**
 * Pure helpers for the 6-digit email OTP input.
 *
 * Kept out of the component so the fiddly parts — what happens when someone
 * pastes a code into the fourth box, or presses backspace in an empty one —
 * are unit-testable without a DOM. The component (`components/auth/otp-input`)
 * owns focus and refs; everything that can be decided from the digits alone
 * lives here.
 *
 * The code itself is never stored: it lives in component state for the length
 * of the verification request and is handed straight to Supabase Auth. There is
 * no OTP table, no localStorage copy, and nothing here is logged.
 */

/** Supabase's email OTP length. Six digits, digits only. */
export const OTP_LENGTH = 6;

/** Digits only, truncated to `OTP_LENGTH`. */
export function sanitiseOtp(raw: string): string {
  return raw.replace(/\D/g, "").slice(0, OTP_LENGTH);
}

/** Split a joined code into exactly `OTP_LENGTH` slots, padding with "". */
export function otpSlots(value: string): string[] {
  const clean = sanitiseOtp(value);
  return Array.from({ length: OTP_LENGTH }, (_, index) => clean[index] ?? "");
}

/** Join slots back into a string, without trailing blanks. */
export function otpValue(slots: readonly string[]): string {
  return slots.join("").replace(/\s/g, "");
}

/**
 * One slot's digit, or "" — never a multi-character string.
 *
 * Slots keep their own gaps: clearing the middle box of "123" must leave
 * `1 _ 3`, not compact to `13`. Rebuilding the array from `otpValue()` would
 * lose exactly that information, which is why this copies slot by slot.
 */
function digitAt(slots: readonly string[], index: number): string {
  const raw = slots[index] ?? "";
  return /\d/.test(raw) ? raw.replace(/\D/g, "").slice(-1) : "";
}

/** True when every slot holds a digit. */
export function isCompleteOtp(value: string): boolean {
  return sanitiseOtp(value).length === OTP_LENGTH;
}

/**
 * Apply whatever arrived in one box — a single keystroke, an autofilled code,
 * or a paste — and report which box should hold focus next.
 *
 * Three cases matter:
 *
 *  1. **One digit** (typing, or a keypad) — fills the current box and advances.
 *  2. **Empty** (the customer cleared a box) — clears it and keeps focus, so
 *     the caret does not jump away from the box being corrected.
 *  3. **Several digits** — a paste, or iOS/Android autofill of an emailed code.
 *     A full-length chunk always fills from the first box: a customer who taps
 *     box four and pastes six digits wants the whole code entered, not the last
 *     two digits of it dropped into boxes four and five.
 */
export function applyOtpInput(
  slots: readonly string[],
  index: number,
  incoming: string,
): { slots: string[]; focus: number } {
  const next = Array.from({ length: OTP_LENGTH }, (_, slot) => digitAt(slots, slot));
  const clean = sanitiseOtp(incoming);
  const start = Math.min(Math.max(index, 0), OTP_LENGTH - 1);

  if (clean.length === 0) {
    next[start] = "";
    return { slots: next, focus: start };
  }

  if (clean.length === 1) {
    next[start] = clean;
    return { slots: next, focus: Math.min(start + 1, OTP_LENGTH - 1) };
  }

  const from = clean.length >= OTP_LENGTH ? 0 : start;
  for (let offset = 0; offset < clean.length; offset += 1) {
    const target = from + offset;
    if (target >= OTP_LENGTH) break;
    next[target] = clean[offset]!;
  }

  return { slots: next, focus: Math.min(from + clean.length, OTP_LENGTH - 1) };
}
