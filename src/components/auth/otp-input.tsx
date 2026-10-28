"use client";

import * as React from "react";

import { applyOtpInput, OTP_LENGTH, otpSlots, otpValue } from "@/lib/otp";
import { cn } from "@/lib/utils";

/**
 * Six single-digit boxes for the emailed verification code.
 *
 * WHY SIX BOXES INSTEAD OF ONE FIELD
 * ----------------------------------
 * A single `maxLength=6` input works, but it hides how much of the code has
 * been entered and makes a typo in the third digit hard to spot. Six boxes show
 * progress at a glance and let a customer correct one digit without retyping
 * the whole code.
 *
 * The cost of that choice is that the fiddly behaviour has to be built:
 * auto-advance, backspace-to-previous, arrow keys, and paste. All of the digit
 * arithmetic lives in `lib/otp` (unit-tested); this component only owns focus.
 *
 * The code is never persisted — it is component state handed straight to
 * Supabase Auth on submit.
 */
export function OtpInput({
  value,
  onChange,
  disabled = false,
  invalid = false,
  autoFocus = false,
  id = "otp",
  describedBy,
  label = "Verification code",
}: {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  invalid?: boolean;
  autoFocus?: boolean;
  id?: string;
  describedBy?: string;
  label?: string;
}) {
  const inputs = React.useRef<Array<HTMLInputElement | null>>([]);
  const pendingFocus = React.useRef<number | null>(null);

  // The six slots are owned here, not by the parent, because a joined string
  // cannot represent a gap: "1_3" and "13" both stringify to "13". The parent
  // only ever needs the joined value to hand to Supabase.
  const [slots, setSlots] = React.useState<string[]>(() => otpSlots(value));
  /** The last value this component handed upwards. */
  const emitted = React.useRef(value);

  // Reset only when the parent pushes a value we did not emit — that is a
  // deliberate clear, not an echo of our own change.
  React.useEffect(() => {
    if (value === emitted.current) return;
    emitted.current = value;
    setSlots(otpSlots(value));
  }, [value]);

  // Focus is applied after paint so the target input exists in the DOM.
  // Runs on every render; `pendingFocus` is cleared the moment it is honoured.
  React.useEffect(() => {
    const target = pendingFocus.current;
    if (target === null) return;
    pendingFocus.current = null;
    const element = inputs.current[target];
    if (!element) return;
    element.focus();
    element.select();
  });

  React.useEffect(() => {
    if (!autoFocus) return;
    const element = inputs.current[0];
    element?.focus();
  }, [autoFocus]);

  // A rejected code re-enables the boxes; without this the caret is left
  // nowhere and the customer has to tap back in to correct a digit.
  const wasDisabled = React.useRef(disabled);
  React.useEffect(() => {
    const previously = wasDisabled.current;
    wasDisabled.current = disabled;
    if (previously && !disabled) inputs.current[0]?.focus();
  }, [disabled]);

  function commit(slotIndex: number, incoming: string) {
    const next = applyOtpInput(slots, slotIndex, incoming);
    const nextJoined = otpValue(next.slots);
    pendingFocus.current = next.focus;
    emitted.current = nextJoined;
    setSlots(next.slots);
    onChange(nextJoined);
  }

  return (
    <div
      role="group"
      aria-label={label}
      aria-describedby={describedBy}
      className="flex items-center justify-center gap-1.5 sm:gap-2"
    >
      {Array.from({ length: OTP_LENGTH }, (_, index) => (
        <input
          key={index}
          ref={(element) => {
            inputs.current[index] = element;
          }}
          id={index === 0 ? id : `${id}-${index}`}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          // iOS and Android offer the code straight from the notification when
          // this is on the first box; the paste handler catches the rest.
          autoComplete={index === 0 ? "one-time-code" : "off"}
          autoCorrect="off"
          spellCheck={false}
          maxLength={1}
          disabled={disabled}
          aria-label={`Digit ${index + 1} of ${OTP_LENGTH}`}
          aria-invalid={invalid || undefined}
          value={slots[index] ?? ""}
          onChange={(event) => commit(index, event.target.value)}
          onFocus={(event) => event.currentTarget.select()}
          // Tapping a filled box must replace its digit, not leave the caret
          // after it — with maxLength=1 an unselected box would ignore the key.
          onClick={(event) => event.currentTarget.select()}
          onPaste={(event) => {
            event.preventDefault();
            commit(index, event.clipboardData.getData("text"));
          }}
          onKeyDown={(event) => {
            if (event.key === "Backspace") {
              // In an empty box, backspace should walk back and clear the
              // previous digit — not sit there doing nothing.
              if ((slots[index] ?? "") === "" && index > 0) {
                event.preventDefault();
                commit(index - 1, "");
              }
              return;
            }
            if (event.key === "ArrowLeft" && index > 0) {
              event.preventDefault();
              pendingFocus.current = index - 1;
              inputs.current[index - 1]?.focus();
              return;
            }
            if (event.key === "ArrowRight" && index < OTP_LENGTH - 1) {
              event.preventDefault();
              inputs.current[index + 1]?.focus();
            }
          }}
          className={cn(
            "h-12 w-[2.75rem] rounded-xl border-[1.5px] bg-surface-muted text-center",
            "text-body-lg font-medium tabular-nums text-ink",
            "transition-[border-color,box-shadow,background-color] duration-[var(--motion-fast)]",
            "focus:border-leaf-600 focus:bg-surface focus:outline-none focus:ring-2 focus:ring-leaf-600/20",
            "disabled:cursor-not-allowed disabled:opacity-60",
            invalid
              ? "border-danger focus:border-danger focus:ring-danger/20"
              : "border-transparent",
          )}
        />
      ))}
    </div>
  );
}
