import { describe, expect, it } from "vitest";

import { friendlyAuthError, isEmailUnconfirmed, isExistingAccount } from "./auth-service";

/**
 * The unconfirmed-email branch decides whether the login page shows the
 * "Please confirm your email first" panel with a resend button.
 *
 * The subtle case: `signInWithPassword` returns the *friendly* message, not the
 * provider's, so matching only on "email not confirmed" would miss every real
 * failure and leave the customer with a bare error and no way to resend.
 */
describe("isEmailUnconfirmed", () => {
  it("recognises the provider error code", () => {
    expect(isEmailUnconfirmed("email_not_confirmed")).toBe(true);
  });

  it("recognises the provider's own message", () => {
    expect(isEmailUnconfirmed(undefined, "Email not confirmed")).toBe(true);
  });

  it("recognises the friendly copy the service returns", () => {
    expect(
      isEmailUnconfirmed(
        undefined,
        "That email hasn't been verified yet. Sign up again to get a fresh 6-digit code, then sign in.",
      ),
    ).toBe(true);
  });

  it("recognises the pre-OTP friendly copy still cached in a stale bundle", () => {
    expect(
      isEmailUnconfirmed(undefined, "Please confirm your email first — open the link we sent you"),
    ).toBe(true);
  });

  it("does not treat a wrong password as unconfirmed", () => {
    expect(
      isEmailUnconfirmed("invalid_credentials", "That email and password don't match."),
    ).toBe(false);
  });

  it("is false when nothing is supplied", () => {
    expect(isEmailUnconfirmed()).toBe(false);
    expect(isEmailUnconfirmed(undefined, "")).toBe(false);
  });
});

/**
 * `signInWithOtp({ shouldCreateUser: true })` succeeds for an address that
 * already has an account. If that case were not detected, the signup form would
 * apply the password typed into the signup form to the existing customer's
 * account — silently changing their credentials.
 *
 * The decision is a comparison between the account's `created_at` and the
 * moment the code was requested, with slack for clock skew.
 */
describe("isExistingAccount", () => {
  const requestedAt = Date.parse("2026-10-28T12:00:00.000Z");

  it("treats an account created long before the request as pre-existing", () => {
    expect(isExistingAccount("2026-03-01T09:30:00.000Z", requestedAt)).toBe(true);
  });

  it("treats an account created by this request as new", () => {
    expect(isExistingAccount("2026-10-28T12:00:02.000Z", requestedAt)).toBe(false);
  });

  it("allows two minutes of clock skew before calling it pre-existing", () => {
    expect(isExistingAccount("2026-10-28T11:59:30.000Z", requestedAt)).toBe(false);
    expect(isExistingAccount("2026-10-28T11:57:00.000Z", requestedAt)).toBe(true);
  });

  it("defaults to 'new' when either side is missing or unparseable", () => {
    expect(isExistingAccount(undefined, requestedAt)).toBe(false);
    expect(isExistingAccount(null, requestedAt)).toBe(false);
    expect(isExistingAccount("2026-03-01T09:30:00.000Z", undefined)).toBe(false);
    expect(isExistingAccount("not-a-date", requestedAt)).toBe(false);
  });
});

/**
 * Every message here is shown to a customer. Raw provider text ("Token has
 * expired or is invalid", "For security purposes, you can only request this
 * after 43 seconds") leaks implementation detail and reads like an error code.
 */
describe("friendlyAuthError — OTP copy", () => {
  it("collapses wrong and expired codes into one message", () => {
    // Supabase deliberately cannot distinguish them, and neither should we.
    const wrong = friendlyAuthError("Invalid OTP", "invalid_otp");
    const expired = friendlyAuthError("Token has expired or is invalid", "otp_expired");
    expect(wrong).toBe(expired);
    expect(wrong).toContain("invalid or has expired");
  });

  it("explains the resend cooldown instead of echoing the provider", () => {
    const message = friendlyAuthError(
      "For security purposes, you can only request this after 43 seconds.",
    );
    expect(message).toContain("wait a few seconds");
    expect(message).not.toContain("43");
  });

  it("recognises the hourly email rate limit", () => {
    expect(friendlyAuthError("email rate limit exceeded")).toContain("wait a few minutes");
  });

  it("tells an already-verified customer to sign in", () => {
    expect(friendlyAuthError("Email already confirmed")).toContain("already verified");
  });

  it("never leaks the provider's raw wording for an unknown failure", () => {
    expect(friendlyAuthError("upstream connect error or disconnect/reset")).toBe(
      "Something went wrong. Please try again in a moment.",
    );
  });
});
