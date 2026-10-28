import { describe, expect, it } from "vitest";

import {
  attemptsLeft,
  EMPTY_ATTEMPTS,
  formatLockRemaining,
  isLocked,
  MAX_OTP_ATTEMPTS,
  OTP_LOCK_MS,
  registerFailure,
  type OtpAttemptState,
} from "./otp-attempts";

/**
 * The rule the customer actually experiences: you get a handful of tries at one
 * code, then the code is burned and you must ask for a new one. It must never
 * lock someone out permanently, and it must not drift if the clock is odd.
 */

const NOW = Date.parse("2026-10-28T12:00:00.000Z");

function failTimes(times: number, from: OtpAttemptState = EMPTY_ATTEMPTS): OtpAttemptState {
  let state = from;
  for (let i = 0; i < times; i += 1) state = registerFailure(state, NOW);
  return state;
}

describe("registerFailure", () => {
  it("counts attempts without locking before the limit", () => {
    const state = failTimes(4);
    expect(state.failures).toBe(4);
    expect(isLocked(state, NOW)).toBe(false);
    expect(attemptsLeft(state, NOW)).toBe(1);
  });

  it("locks on the fifth wrong code", () => {
    const state = failTimes(MAX_OTP_ATTEMPTS);
    expect(state.failures).toBe(MAX_OTP_ATTEMPTS);
    expect(isLocked(state, NOW)).toBe(true);
    expect(state.lockedUntil).toBe(NOW + OTP_LOCK_MS);
    expect(attemptsLeft(state, NOW)).toBe(0);
  });

  it("holds the lock for one hour, not forever", () => {
    const state = failTimes(MAX_OTP_ATTEMPTS);
    expect(isLocked(state, NOW + OTP_LOCK_MS - 1)).toBe(true);
    expect(isLocked(state, NOW + OTP_LOCK_MS)).toBe(false);
  });

  it("does not extend the lock on further failures", () => {
    // Otherwise an attacker keeps the victim locked out indefinitely.
    const locked = failTimes(MAX_OTP_ATTEMPTS);
    const again = registerFailure(locked, NOW + 5 * 60_000);
    expect(again.lockedUntil).toBe(locked.lockedUntil);
  });
});

describe("attemptsLeft", () => {
  it("reports the full budget on a fresh code", () => {
    expect(attemptsLeft(EMPTY_ATTEMPTS, NOW)).toBe(MAX_OTP_ATTEMPTS);
  });

  it("never goes negative", () => {
    expect(attemptsLeft(failTimes(9), NOW)).toBe(0);
  });
});

describe("formatLockRemaining", () => {
  it("describes the wait in minutes", () => {
    const state = failTimes(MAX_OTP_ATTEMPTS);
    expect(formatLockRemaining(state, NOW)).toBe("60 min");
    expect(formatLockRemaining(state, NOW + 12 * 60_000)).toBe("48 min");
  });

  it("says nothing once the lock has passed", () => {
    const state = failTimes(MAX_OTP_ATTEMPTS);
    expect(formatLockRemaining(state, NOW + OTP_LOCK_MS)).toBe("");
  });

  it("avoids '0 min' in the final minute", () => {
    const state = failTimes(MAX_OTP_ATTEMPTS);
    expect(formatLockRemaining(state, NOW + OTP_LOCK_MS - 20_000)).toBe("under a minute");
  });
});
