"use client";

import Image from "next/image";
import { useState } from "react";
import { cn } from "@/lib/utils";

/**
 * The **only** place catalogue imagery is rendered.
 * See docs/IMAGE_STRATEGY.md.
 *
 * Two states, decided inside this component:
 *
 *  1. **Photography** — `imageUrl` present → `next/image` with responsive
 *     `sizes` inside a 1:1 wrapper, with the accent tint kept behind it so a
 *     letterboxed photo still sits on brand colour.
 *  2. **Glyph plinth** — `imageUrl` is `NULL` (the current production state for
 *     every row) → a designed composition: the row's own `accent` RGB becomes a
 *     soft radial tint, the catalogue glyph is set large, and an elliptical
 *     shadow grounds it.
 *
 * Because the branch lives here, populating `products.image_url` for one product
 * switches exactly that product to photography — no client change, no deploy.
 */

export type ProductImageSource = {
  imageUrl: string | null;
  glyph: string;
  /** Decimal RGB from the database, e.g. `16639968` → `#fde0c0`. */
  accent: number;
  name: string;
};

export function accentToHex(accent: number): string {
  const value = Number.isFinite(accent) ? Math.max(0, Math.trunc(accent)) : 0;
  return `#${(value & 0xffffff).toString(16).padStart(6, "0")}`;
}

/** Deterministic sizes string per slot so mobile never downloads desktop art. */
const SIZES = {
  card: "(max-width: 640px) 45vw, (max-width: 1024px) 30vw, 240px",
  detail: "(max-width: 768px) 92vw, 520px",
  thumb: "64px",
  tile: "(max-width: 640px) 34vw, 180px",
  rail: "(max-width: 640px) 40vw, 220px",
} as const;

export function ProductImage({
  source,
  size = "card",
  priority = false,
  className,
  glyphClassName,
}: {
  source: ProductImageSource;
  size?: keyof typeof SIZES;
  priority?: boolean;
  className?: string;
  glyphClassName?: string;
}) {
  const tint = accentToHex(source.accent);
  const [broken, setBroken] = useState(false);
  const showPhoto = Boolean(source.imageUrl) && !broken;

  return (
    <div
      className={cn(
        "tint-surface relative isolate aspect-square w-full overflow-hidden",
        className,
      )}
      // Only ever a background tint — never text colour.
      style={{ ["--tint" as string]: tint }}
    >
      {showPhoto && source.imageUrl ? (
        <Image
          src={source.imageUrl}
          alt={source.name}
          fill
          sizes={SIZES[size]}
          priority={priority}
          className="object-cover"
          onError={() => setBroken(true)}
        />
      ) : (
        <>
          {/* Grounding shadow: gives the glyph weight without a box. */}
          <span
            aria-hidden="true"
            className="absolute bottom-[13%] left-1/2 h-[7%] w-[46%] -translate-x-1/2 rounded-[50%] bg-leaf-900/12 blur-[6px]"
          />
          <span
            role="img"
            aria-label={`${source.name} — image coming soon`}
            className={cn(
              "absolute inset-0 grid place-items-center leading-none select-none",
              "text-[clamp(2.5rem,42cqw,5.5rem)]",
              glyphClassName,
            )}
            style={{ containerType: "inline-size" }}
          >
            {source.glyph || "🥬"}
          </span>
        </>
      )}
    </div>
  );
}
