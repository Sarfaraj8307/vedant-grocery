// @vitest-environment jsdom
import * as React from "react";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { OtpInput } from "./otp-input";

/**
 * The six OTP boxes, rendered for real.
 *
 * `lib/otp.test.ts` covers the digit arithmetic; this covers the part that only
 * exists once a DOM is involved — that six inputs appear, that focus moves as
 * digits land, that a pasted code is distributed, and that clearing the middle
 * digit leaves the others where they were. Those are the behaviours a customer
 * notices within seconds if they break, and none of them are visible to `tsc`
 * or `next build`.
 */

function boxes(): HTMLInputElement[] {
  return screen.getAllByLabelText(/^Digit \d of 6$/).map((el) => el as HTMLInputElement);
}

function values(): string[] {
  return boxes().map((box) => box.value);
}

afterEach(cleanup);

/** Renders with internal state, so focus/advance behaviour is exercised. */
function Harness({ onValue }: { onValue?: (value: string) => void }) {
  const [value, setValue] = React.useState("");
  return (
    <OtpInput
      value={value}
      autoFocus
      onChange={(next) => {
        setValue(next);
        onValue?.(next);
      }}
    />
  );
}

describe("OtpInput", () => {
  it("renders six labelled boxes", () => {
    render(<Harness />);
    expect(boxes()).toHaveLength(6);
    expect(boxes()[0]?.getAttribute("inputmode")).toBe("numeric");
    // Only the first box advertises itself for OS autofill, otherwise the code
    // can be offered six times.
    expect(boxes()[0]?.getAttribute("autocomplete")).toBe("one-time-code");
    expect(boxes()[1]?.getAttribute("autocomplete")).toBe("off");
  });

  it("moves focus to the next box as digits are typed", async () => {
    render(<Harness />);
    const [first, second] = boxes();

    fireEvent.change(first!, { target: { value: "1" } });
    expect(values()).toEqual(["1", "", "", "", "", ""]);
    await waitFor(() => expect(document.activeElement).toBe(second));
  });

  it("reports the joined code upwards", async () => {
    const onValue = vi.fn();
    render(<Harness onValue={onValue} />);

    fireEvent.change(boxes()[0]!, { target: { value: "4" } });
    await waitFor(() => expect(onValue).toHaveBeenLastCalledWith("4"));
  });

  it("distributes a pasted code across every box", () => {
    render(<Harness />);

    fireEvent.paste(boxes()[0]!, {
      clipboardData: { getData: () => "123456" },
    });

    expect(values()).toEqual(["1", "2", "3", "4", "5", "6"]);
  });

  it("fills from the first box when the paste lands in a later one", () => {
    render(<Harness />);

    fireEvent.paste(boxes()[3]!, {
      clipboardData: { getData: () => "123456" },
    });

    expect(values()).toEqual(["1", "2", "3", "4", "5", "6"]);
  });

  it("keeps the other digits in place when one is cleared", () => {
    render(<Harness />);
    fireEvent.paste(boxes()[0]!, { clipboardData: { getData: () => "123" } });

    fireEvent.change(boxes()[1]!, { target: { value: "" } });

    // 1 _ 3, not 1 3 _
    expect(values()).toEqual(["1", "", "3", "", "", ""]);
  });

  it("walks back and clears the previous digit on backspace", () => {
    render(<Harness />);
    fireEvent.paste(boxes()[0]!, { clipboardData: { getData: () => "12" } });
    // Focus sits on box 3, which is empty.
    fireEvent.keyDown(boxes()[2]!, { key: "Backspace" });

    expect(values()).toEqual(["1", "", "", "", "", ""]);
  });

  it("ignores non-digit input", () => {
    render(<Harness />);
    fireEvent.change(boxes()[0]!, { target: { value: "a" } });
    expect(values()).toEqual(["", "", "", "", "", ""]);
  });

  it("cannot be edited while a verification is in flight", () => {
    render(
      <OtpInput value="123456" onChange={() => {}} disabled />,
    );
    expect(boxes().every((box) => box.disabled)).toBe(true);
  });
});
