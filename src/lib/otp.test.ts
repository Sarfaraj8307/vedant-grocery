import { describe, expect, it } from "vitest";

import {
  applyOtpInput,
  isCompleteOtp,
  OTP_LENGTH,
  otpSlots,
  otpValue,
  sanitiseOtp,
} from "./otp";

/**
 * The OTP boxes are only as good as their digit arithmetic.
 *
 * Everything here is the behaviour a customer notices immediately when it is
 * wrong: a pasted code landing in the wrong boxes, backspace refusing to clear
 * the previous digit, a code with a space in it being rejected. None of it
 * needs a DOM, which is why it lives in a plain module instead of the component.
 */

describe("sanitiseOtp", () => {
  it("keeps digits and drops everything else", () => {
    expect(sanitiseOtp("12 34-56")).toBe("123456");
    expect(sanitiseOtp("code: 123456")).toBe("123456");
  });

  it("truncates anything longer than six digits", () => {
    expect(sanitiseOtp("1234567890")).toBe("123456");
  });

  it("returns an empty string when there are no digits", () => {
    expect(sanitiseOtp("")).toBe("");
    expect(sanitiseOtp("abcdef")).toBe("");
  });
});

describe("otpSlots / otpValue", () => {
  it("pads a partial code out to six slots", () => {
    expect(otpSlots("12")).toEqual(["1", "2", "", "", "", ""]);
  });

  it("round-trips a full code", () => {
    expect(otpValue(otpSlots("123456"))).toBe("123456");
  });

  it("always produces exactly six slots", () => {
    expect(otpSlots("")).toHaveLength(OTP_LENGTH);
    expect(otpSlots("1234567890")).toHaveLength(OTP_LENGTH);
  });
});

describe("isCompleteOtp", () => {
  it("is true only for six digits", () => {
    expect(isCompleteOtp("123456")).toBe(true);
    expect(isCompleteOtp("12345")).toBe(false);
    expect(isCompleteOtp("")).toBe(false);
    expect(isCompleteOtp("12345a")).toBe(false);
  });

  it("ignores separators that arrive with a pasted code", () => {
    expect(isCompleteOtp("123 456")).toBe(true);
  });
});

describe("applyOtpInput — typing", () => {
  it("fills the box and advances focus", () => {
    const result = applyOtpInput(otpSlots(""), 0, "7");
    expect(otpValue(result.slots)).toBe("7");
    expect(result.focus).toBe(1);
  });

  it("stays on the last box instead of running past it", () => {
    const result = applyOtpInput(otpSlots("12345"), 5, "6");
    expect(otpValue(result.slots)).toBe("123456");
    expect(result.focus).toBe(5);
  });

  it("clears a box and keeps focus when the value is emptied", () => {
    const result = applyOtpInput(otpSlots("123"), 1, "");
    expect(otpValue(result.slots)).toBe("13");
    expect(result.focus).toBe(1);
  });

  it("leaves the neighbouring digits where they were when one is cleared", () => {
    // The slots must not compact to the left. A customer correcting the middle
    // digit of 1-2-3 expects 1-_-3, not 1-3-_.
    const result = applyOtpInput(otpSlots("123"), 1, "");
    expect(result.slots).toEqual(["1", "", "3", "", "", ""]);
  });

  it("drops a non-digit keystroke without moving focus", () => {
    const result = applyOtpInput(otpSlots("12"), 2, "x");
    expect(otpValue(result.slots)).toBe("12");
    expect(result.focus).toBe(2);
  });
});

describe("applyOtpInput — paste and autofill", () => {
  it("fills every box when a full code is pasted into the first", () => {
    const result = applyOtpInput(otpSlots(""), 0, "123456");
    expect(otpValue(result.slots)).toBe("123456");
    expect(result.focus).toBe(5);
  });

  it("fills from the first box even when pasted into a later box", () => {
    // Tapping box four and pasting the whole code should enter the whole code,
    // not drop its first three digits.
    const result = applyOtpInput(otpSlots(""), 3, "123456");
    expect(otpValue(result.slots)).toBe("123456");
    expect(result.focus).toBe(5);
  });

  it("overwrites the existing code rather than appending to it", () => {
    const result = applyOtpInput(otpSlots("999999"), 0, "123456");
    expect(otpValue(result.slots)).toBe("123456");
  });

  it("handles a code that arrives with spaces or a newline", () => {
    const result = applyOtpInput(otpSlots(""), 0, " 123 456\n");
    expect(otpValue(result.slots)).toBe("123456");
  });

  it("places a partial chunk at the box it was pasted into", () => {
    const result = applyOtpInput(otpSlots("12"), 2, "34");
    expect(otpValue(result.slots)).toBe("1234");
    expect(result.focus).toBe(4);
  });
});
