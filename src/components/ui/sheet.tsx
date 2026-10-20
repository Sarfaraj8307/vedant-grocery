"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Sheet / Dialog — DESIGN_SYSTEM §7.7.
 *
 * One primitive, three presentations:
 *  * `bottom` — mobile bottom sheet (radius xl, drag handle, safe-area aware)
 *  * `right`  — desktop side sheet
 *  * `center` — centred dialog at the same radius
 *
 * `side="auto"` picks `bottom` under 1024px and `right` above it, which is the
 * behaviour the cart and checkout use so mobile and desktop each get a
 * thumb-reachable / pointer-appropriate surface.
 *
 * Accessibility is inherited from Radix Dialog: focus trap, `Esc` to close,
 * `aria-modal`, background `inert`, and focus restored to the trigger.
 */

const SheetRoot = DialogPrimitive.Root;
const SheetTrigger = DialogPrimitive.Trigger;
const SheetClose = DialogPrimitive.Close;

function SheetOverlay({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Overlay>) {
  return <DialogPrimitive.Overlay className={cn("ui-overlay", className)} {...props} />;
}

export type SheetSide = "auto" | "bottom" | "right" | "center";

export function SheetContent({
  className,
  children,
  side = "auto",
  showClose = true,
  title,
  description,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Content> & {
  side?: SheetSide;
  showClose?: boolean;
  /** Accessible name. Visually hidden unless `showHeader` styling is used. */
  title: string;
  description?: string;
}) {
  // `auto` cannot be expressed in CSS alone without duplicating markup, so the
  // presentation resolves to concrete classes and CSS media queries decide
  // which one applies.
  const sideClasses =
    side === "center"
      ? "left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-[min(32rem,calc(100vw-2rem))] max-h-[88vh] rounded-xl"
      : side === "right"
        ? "ui-sheet-right"
        : side === "bottom"
          ? "ui-sheet-bottom"
          : // auto: bottom under lg, side sheet from lg up
            "ui-sheet-bottom lg:ui-sheet-right";

  return (
    <DialogPrimitive.Portal>
      <SheetOverlay />
      <DialogPrimitive.Content
        className={cn("ui-dialog-content", sideClasses, className)}
        {...props}
      >
        {/* Required for screen readers; visually hidden by default. */}
        <DialogPrimitive.Title className="sr-only">{title}</DialogPrimitive.Title>
        {description ? (
          <DialogPrimitive.Description className="sr-only">
            {description}
          </DialogPrimitive.Description>
        ) : null}

        {/* Drag handle is decorative: the sheet is dismissed by Esc, the close
            button, or the overlay — never by a gesture the keyboard can't do. */}
        {side !== "center" && side !== "right" ? (
          <div className="flex justify-center pt-2.5 lg:hidden">
            <span
              aria-hidden="true"
              className="h-1 w-9 rounded-full bg-line-strong"
            />
          </div>
        ) : null}

        {showClose ? (
          <DialogPrimitive.Close
            className={cn(
              "absolute right-3 top-3 grid size-9 place-items-center rounded-md",
              "text-ink-muted transition-colors hover:bg-surface-muted hover:text-ink",
              "focus-visible:outline-none",
            )}
          >
            <X className="size-[18px]" aria-hidden="true" />
            <span className="sr-only">Close</span>
          </DialogPrimitive.Close>
        ) : null}

        {children}
      </DialogPrimitive.Content>
    </DialogPrimitive.Portal>
  );
}

/** Sheet header with a real title (use instead of the sr-only default). */
export function SheetHeader({
  title,
  description,
  className,
}: {
  title: string;
  description?: string;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col gap-1 px-5 pb-3 pt-4 pr-12", className)}>
      <h2 className="font-display text-h3 text-ink">{title}</h2>
      {description ? (
        <p className="text-small text-ink-muted">{description}</p>
      ) : null}
    </div>
  );
}

export function SheetBody({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn("min-h-0 flex-1 overflow-y-auto overscroll-contain px-5", className)}
      {...props}
    />
  );
}

export function SheetFooter({
  className,
  ...props
}: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "flex flex-col gap-2 border-t border-line px-5 pt-4 pb-safe sm:flex-row sm:justify-end",
        className,
      )}
      {...props}
    />
  );
}

export {
  SheetRoot,
  SheetTrigger,
  SheetClose,
  DialogPrimitive as SheetPrimitive,
};
