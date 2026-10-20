"use client";

import * as React from "react";
import { Slot } from "@radix-ui/react-slot";
import { cva, type VariantProps } from "class-variance-authority";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Button — see docs/DESIGN_SYSTEM.md §7.1.
 *
 * Deliberate properties:
 *  * press depth is a 1px translateY, never a scale, so text stays crisp;
 *  * the loading state keeps the label in the DOM (and announces it), so the
 *    button's width cannot change mid-click;
 *  * `danger` is reserved for destructive actions and is never used for offers.
 */
const buttonVariants = cva(
  [
    "relative inline-flex items-center justify-center gap-2 whitespace-nowrap",
    "font-medium select-none",
    "transition-[background-color,border-color,color,box-shadow,transform]",
    "duration-[var(--motion-fast)] ease-[var(--ease-standard)]",
    "focus-visible:outline-none",
    "disabled:pointer-events-none",
    "[&_svg]:pointer-events-none [&_svg]:shrink-0",
  ],
  {
    variants: {
      variant: {
        primary:
          "bg-leaf-600 text-white shadow-xs hover:bg-leaf-700 active:translate-y-px disabled:bg-leaf-300 disabled:text-white/80 disabled:shadow-none",
        secondary:
          "bg-surface text-leaf-700 border-[1.5px] border-leaf-600/35 hover:border-leaf-600/60 hover:bg-leaf-50 active:translate-y-px disabled:text-leaf-300 disabled:border-leaf-600/15",
        quiet:
          "bg-transparent text-ink hover:bg-surface-muted active:bg-surface-sunken disabled:text-ink-subtle",
        accent:
          "bg-marigold-400 text-leaf-900 shadow-xs hover:bg-marigold-500 active:translate-y-px disabled:bg-marigold-200",
        danger:
          "bg-danger text-white shadow-xs hover:brightness-95 active:translate-y-px disabled:brightness-100 disabled:opacity-55",
        outline:
          "bg-transparent text-ink border-[1.5px] border-line-strong hover:border-ink-subtle hover:bg-surface-muted active:translate-y-px",
        link: "bg-transparent text-leaf-700 underline-offset-4 hover:underline px-0 h-auto",
      },
      size: {
        sm: "h-8 rounded-sm px-3 text-small",
        md: "h-10 rounded-md px-4 text-body",
        lg: "h-12 rounded-md px-5 text-body-lg",
        /* Icon targets: 44px on touch, 36px on fine pointers (§12.4) */
        icon: "size-9 rounded-md [&_svg]:size-[18px]",
        iconLg: "size-11 rounded-md [&_svg]:size-5",
      },
      block: { true: "w-full", false: "" },
    },
    defaultVariants: { variant: "primary", size: "md", block: false },
  },
);

export type ButtonProps = React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
    loading?: boolean;
    /** Announced while loading, e.g. "Placing order…". */
    loadingLabel?: string;
  };

export function Button({
  className,
  variant,
  size,
  block,
  asChild = false,
  loading = false,
  loadingLabel = "Working…",
  disabled,
  children,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot : "button";

  if (asChild) {
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, block }), className)}
        {...props}
      >
        {children}
      </Comp>
    );
  }

  return (
    <Comp
      className={cn(buttonVariants({ variant, size, block }), className)}
      // A loading button must not be submittable twice, but must stay focusable
      // so screen readers can still reach the announced status.
      aria-busy={loading || undefined}
      aria-disabled={disabled || loading || undefined}
      disabled={disabled || loading}
      {...props}
    >
      <span
        className={cn(
          "inline-flex items-center gap-2",
          loading && "invisible",
        )}
      >
        {children}
      </span>
      {loading ? (
        <span className="absolute inset-0 inline-flex items-center justify-center gap-2">
          <Loader2 className="size-4 animate-spin" aria-hidden="true" />
          <span className="sr-only">{loadingLabel}</span>
        </span>
      ) : null}
    </Comp>
  );
}

export { buttonVariants };
