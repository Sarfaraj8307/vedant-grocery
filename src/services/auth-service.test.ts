import { describe, expect, it } from "vitest";

import { isEmailUnconfirmed } from "./auth-service";

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
