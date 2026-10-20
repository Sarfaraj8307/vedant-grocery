import * as React from "react";
import * as SeparatorPrimitive from "@radix-ui/react-separator";
import { cn } from "@/lib/utils";

/* -------------------------------------------------------------------------- */
/* Separator                                                                  */
/* -------------------------------------------------------------------------- */

export function Separator({
  className,
  orientation = "horizontal",
  decorative = true,
  ...props
}: React.ComponentProps<typeof SeparatorPrimitive.Root>) {
  return (
    <SeparatorPrimitive.Root
      decorative={decorative}
      orientation={orientation}
      className={cn(
        "shrink-0 bg-line",
        orientation === "horizontal" ? "h-px w-full" : "h-full w-px",
        className,
      )}
      {...props}
    />
  );
}

/* -------------------------------------------------------------------------- */
/* Surface — the one card container                                           */
/* -------------------------------------------------------------------------- */

const surfaceTones = {
  default: "bg-surface border-line shadow-xs",
  flat: "bg-surface border-line",
  muted: "bg-surface-muted border-line",
  /** Warm paper panel used by the "today's rate" board. */
  board: "bg-marigold-50 border-marigold-200",
} as const;

export function Surface({
  className,
  tone = "default",
  ...props
}: React.ComponentProps<"div"> & {
  tone?: keyof typeof surfaceTones;
}) {
  return (
    <div className={cn("rounded-lg border", surfaceTones[tone], className)} {...props} />
  );
}

/* -------------------------------------------------------------------------- */
/* Section heading — repeated on nearly every page                            */
/* -------------------------------------------------------------------------- */

export function SectionHeading({
  title,
  subtitle,
  action,
  className,
  as: Comp = "h2",
  id,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
  className?: string;
  as?: "h1" | "h2" | "h3";
  id?: string;
}) {
  return (
    <div
      /*
       * `flex-wrap` is the mobile behaviour that matters here: at 360px a long
       * section title plus an action button do not fit on one line, and letting
       * the title shrink to ~160px produced a three-line heading with a single
       * orphaned word ("Everything / we / have today"). Wrapping moves the action
       * to its own line instead, which is what a designed page does.
       */
      className={cn(
        "flex flex-wrap items-end justify-between gap-x-4 gap-y-2 pb-3 sm:pb-4",
        className,
      )}
    >
      <div className="min-w-0">
        <Comp
          id={id}
          className={cn(
            "font-display text-ink",
            Comp === "h1" ? "text-h1" : Comp === "h3" ? "text-h3" : "text-h2",
          )}
        >
          {title}
        </Comp>
        {subtitle ? (
          <p className="mt-1 max-w-[62ch] text-small text-ink-muted">{subtitle}</p>
        ) : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Empty state — always exactly one primary action (§7.9)                     */
/* -------------------------------------------------------------------------- */

export function EmptyState({
  illustration,
  title,
  description,
  action,
  className,
}: {
  illustration: React.ReactNode;
  title: string;
  description: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center px-6 py-12 text-center",
        className,
      )}
    >
      <div className="mb-5" aria-hidden="true">
        {illustration}
      </div>
      <h3 className="font-display text-h3 text-ink">{title}</h3>
      <p className="mt-1.5 max-w-[38ch] text-small text-ink-muted">
        {description}
      </p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}
