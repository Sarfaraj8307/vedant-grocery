import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Input — DESIGN_SYSTEM §7.4.
 *
 * Two touch-critical details:
 *  * 44px tall on mobile, 40px from `sm` up (thumb-friendly without bloating
 *    desktop forms);
 *  * the error state adds a ring *as well as* a border colour, because a
 *    colour-only error is invisible to a colour-blind customer.
 */
export function Input({
  className,
  type = "text",
  invalid = false,
  ...props
}: React.ComponentProps<"input"> & { invalid?: boolean }) {
  return (
    <input
      type={type}
      aria-invalid={invalid || undefined}
      className={cn(
        "h-11 w-full rounded-sm border-[1.5px] border-line-strong bg-surface px-3.5",
        "text-body text-ink placeholder:text-ink-subtle",
        "shadow-xs transition-[border-color,box-shadow] duration-[var(--motion-fast)]",
        "focus:border-leaf-600 focus:outline-none focus-visible:outline-none",
        "focus:ring-2 focus:ring-leaf-600/20",
        "disabled:cursor-not-allowed disabled:bg-surface-muted disabled:text-ink-subtle",
        "read-only:bg-surface-muted",
        "file:mr-3 file:rounded-xs file:border-0 file:bg-surface-muted file:px-3 file:py-1.5 file:text-small",
        "sm:h-10",
        invalid && "border-danger focus:border-danger focus:ring-danger/20",
        className,
      )}
      {...props}
    />
  );
}

export function Textarea({
  className,
  invalid = false,
  rows = 4,
  ...props
}: React.ComponentProps<"textarea"> & { invalid?: boolean }) {
  return (
    <textarea
      rows={rows}
      aria-invalid={invalid || undefined}
      className={cn(
        "w-full resize-y rounded-sm border-[1.5px] border-line-strong bg-surface px-3.5 py-2.5",
        "text-body text-ink placeholder:text-ink-subtle shadow-xs",
        "transition-[border-color,box-shadow] duration-[var(--motion-fast)]",
        "focus:border-leaf-600 focus:outline-none focus-visible:outline-none focus:ring-2 focus:ring-leaf-600/20",
        "disabled:cursor-not-allowed disabled:bg-surface-muted",
        invalid && "border-danger focus:border-danger focus:ring-danger/20",
        className,
      )}
      {...props}
    />
  );
}

/**
 * Field wrapper: label + control + hint/error with **reserved space** for the
 * message so validation never shifts the layout under a thumb
 * (DESIGN_SYSTEM §7.4).
 */
export function Field({
  label,
  htmlFor,
  error,
  hint,
  required,
  children,
  className,
}: {
  label: string;
  htmlFor: string;
  error?: string;
  hint?: string;
  required?: boolean;
  children: React.ReactNode;
  className?: string;
}) {
  const messageId = `${htmlFor}-message`;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <label htmlFor={htmlFor} className="text-small font-medium text-ink">
        {label}
        {required ? (
          <span className="ml-0.5 text-beet-600" aria-hidden="true">
            *
          </span>
        ) : null}
        {required ? <span className="sr-only"> (required)</span> : null}
      </label>
      {children}
      <p
        id={messageId}
        className={cn(
          "min-h-4 text-small",
          error ? "text-danger" : "text-ink-subtle",
        )}
        // Only errors are announced; hints are already visible.
        role={error ? "alert" : undefined}
      >
        {error ?? hint ?? ""}
      </p>
    </div>
  );
}
