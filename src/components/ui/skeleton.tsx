import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Skeleton — DESIGN_SYSTEM §7.8.
 *
 * A slow opacity pulse between two warm neutral steps. No diagonal gradient
 * shimmer (the canonical "AI demo" tell) and no motion under
 * `prefers-reduced-motion` (handled globally in globals.css).
 *
 * Skeletons must be shaped like the content that replaces them, so consumed
 * via the composed helpers below rather than bare boxes wherever possible.
 */
export function Skeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      aria-hidden="true"
      className={cn(
        "animate-[var(--animate-shimmer)] rounded-sm bg-surface-muted",
        className,
      )}
      {...props}
    />
  );
}

/** A single line of text. Width is varied so stacked lines read as prose. */
export function SkeletonText({
  lines = 1,
  className,
  widths = ["100%", "82%", "64%"],
}: {
  lines?: number;
  className?: string;
  widths?: string[];
}) {
  return (
    <div className={cn("flex flex-col gap-2", className)}>
      {Array.from({ length: lines }).map((_, index) => (
        <Skeleton
          key={index}
          className="h-3.5 rounded-full"
          // Inline style is intentional: widths are data, not design tokens.
          style={{ width: widths[index % widths.length] }}
        />
      ))}
    </div>
  );
}

/**
 * Product-card skeleton. Geometry is locked to the real card (1:1 media,
 * two text lines, a price row) so the swap causes zero layout shift.
 */
export function SkeletonProductCard({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "flex flex-col overflow-hidden rounded-lg border border-line bg-surface shadow-xs",
        className,
      )}
    >
      <Skeleton className="aspect-square w-full rounded-none" />
      <div className="flex flex-col gap-2 p-2.5">
        <Skeleton className="h-3.5 w-[78%] rounded-full" />
        <Skeleton className="h-2.5 w-[46%] rounded-full" />
        <div className="mt-0.5 flex items-center justify-between">
          <Skeleton className="h-4 w-14 rounded-full" />
          <Skeleton className="h-7 w-[74px] rounded-full" />
        </div>
      </div>
    </div>
  );
}

export function SkeletonProductGrid({ count = 8 }: { count?: number }) {
  return (
    <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
      {Array.from({ length: count }).map((_, index) => (
        <SkeletonProductCard key={index} />
      ))}
    </div>
  );
}
