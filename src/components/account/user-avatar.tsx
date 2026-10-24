"use client";

import * as React from "react";

import { initials } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * The customer's picture, with initials as the honest fallback.
 *
 * Google returns a real photo; email signup returns nothing. Rather than
 * rendering an empty circle, the initials carry the identity — and if the photo
 * URL 404s or the browser blocks it, `onError` swaps back to initials instead of
 * leaving a broken-image glyph in the header.
 */
export function UserAvatar({
  name,
  url,
  size = 32,
  className,
}: {
  name: string;
  url?: string | null;
  size?: number;
  className?: string;
}) {
  const [failed, setFailed] = React.useState(false);
  const showImage = Boolean(url) && !failed;

  return (
    <span
      aria-hidden="true"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.36) }}
      className={cn(
        "grid shrink-0 place-items-center overflow-hidden rounded-full bg-leaf-100 font-semibold leading-none text-leaf-700",
        className,
      )}
    >
      {showImage ? (
        /*
         * A plain <img>, not next/image: the host is whatever the identity
         * provider returns (Google serves from lh3.googleusercontent.com), and
         * allow-listing every provider's CDN in next.config for a 32px avatar
         * is more configuration than it is worth. `referrerPolicy` keeps the
         * current page URL out of Google's logs.
         */
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={url ?? undefined}
          alt=""
          width={size}
          height={size}
          referrerPolicy="no-referrer"
          loading="lazy"
          decoding="async"
          className="size-full object-cover"
          onError={() => setFailed(true)}
        />
      ) : (
        initials(...name.split(/\s+/))
      )}
    </span>
  );
}
