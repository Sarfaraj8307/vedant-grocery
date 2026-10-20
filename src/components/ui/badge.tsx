import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/lib/utils";

/**
 * Badge — DESIGN_SYSTEM §7.5.
 * `caption` type, pill radius. Semantic variants pair a tinted background with
 * a *darker* text tone of the same hue so contrast holds at 11px.
 */
const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-caption whitespace-nowrap",
  {
    variants: {
      variant: {
        neutral: "bg-surface-muted text-ink-muted",
        leaf: "bg-leaf-100 text-leaf-700",
        marigold: "bg-marigold-100 text-marigold-700",
        /* Offers only — never errors. */
        offer: "bg-beet-100 text-beet-600",
        success: "bg-success-bg text-success",
        warning: "bg-warning-bg text-warning",
        danger: "bg-danger-bg text-danger",
        info: "bg-info-bg text-info",
        outline: "border border-line-strong bg-transparent text-ink-muted",
        solid: "bg-leaf-600 text-white",
      },
      size: {
        sm: "px-1.5 py-0 text-[10px]",
        md: "px-2 py-0.5",
        lg: "px-2.5 py-1 text-[11px]",
      },
    },
    defaultVariants: { variant: "neutral", size: "md" },
  },
);

export function Badge({
  className,
  variant,
  size,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return (
    <span className={cn(badgeVariants({ variant, size }), className)} {...props} />
  );
}

export { badgeVariants };
