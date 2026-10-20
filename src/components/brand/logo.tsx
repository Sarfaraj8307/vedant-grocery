import { cn } from "@/lib/utils";

/**
 * Vedant brand mark — original artwork.
 *
 * Concept: a produce crate (the rounded square) holding a "V" for **Vedant**
 * whose right stroke grows into a leaf. The leaf is marigold so the mark carries
 * both brand colours without using a gradient.
 *
 * Drawn as inline SVG rather than a raster asset: it stays crisp at every DPI,
 * costs no request, weighs ~600 bytes, and can inherit the current text colour
 * (`tone="current"`) so it works on paper and on a leaf-filled panel.
 */
export function BrandMark({
  className,
  tone = "brand",
  title,
}: {
  className?: string;
  tone?: "brand" | "current" | "inverse";
  title?: string;
}) {
  const crateFill =
    tone === "brand"
      ? "var(--color-leaf-600)"
      : tone === "inverse"
        ? "var(--color-surface)"
        : "currentColor";
  const stroke =
    tone === "brand" ? "#ffffff" : tone === "inverse" ? "var(--color-leaf-700)" : "var(--color-paper)";

  return (
    <svg
      viewBox="0 0 32 32"
      role={title ? "img" : "presentation"}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      className={cn("size-8 shrink-0", className)}
    >
      <rect width="32" height="32" rx="9" fill={crateFill} />
      <path
        d="M9.5 10.5 16 22.5l6.5-12"
        fill="none"
        stroke={stroke}
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M21.2 7.4c3.2 0 4.9 1.7 4.9 4.8 0 .7-.6 1.2-1.2 1.2-3.1 0-4.9-1.7-4.9-4.8 0-.7.5-1.2 1.2-1.2Z"
        fill="var(--color-marigold-400)"
      />
    </svg>
  );
}

/**
 * Full lockup: mark + wordmark.
 * `compact` drops the second line for tight headers and the PWA splash.
 */
export function BrandLogo({
  className,
  compact = false,
  tone = "brand",
  showTagline = true,
}: {
  className?: string;
  compact?: boolean;
  tone?: "brand" | "inverse";
  showTagline?: boolean;
}) {
  return (
    <span className={cn("flex items-center gap-2.5", className)}>
      <BrandMark tone={tone === "inverse" ? "inverse" : "brand"} className="size-9" />
      <span className="flex min-w-0 flex-col leading-none">
        <span
          className={cn(
            "font-display text-[1.0625rem] font-semibold tracking-[-0.02em]",
            tone === "inverse" ? "text-white" : "text-ink",
          )}
        >
          Vedant
        </span>
        {!compact && showTagline ? (
          <span
            className={cn(
              "mt-0.5 text-[10px] font-medium uppercase tracking-[0.12em]",
              tone === "inverse" ? "text-white/75" : "text-ink-subtle",
            )}
          >
            Fruit &amp; Vegetables
          </span>
        ) : null}
      </span>
    </span>
  );
}
