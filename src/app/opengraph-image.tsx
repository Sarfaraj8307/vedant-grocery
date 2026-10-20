import { ImageResponse } from "next/og";

import { store } from "@/config/store";

/**
 * Open Graph / social card, generated with Next's built-in `ImageResponse`
 * (Satori) rather than shipped as a static PNG.
 *
 * Why generated: the card carries live brand copy, scales to whatever the
 * designer changes in `store.ts`, and stays in the repository as *code* rather
 * than a binary nobody can edit. No font is passed in, so Satori uses its
 * bundled sans — which keeps this route dependency-free and fast.
 *
 * Layout is deliberately flexbox-only: Satori implements a subset of CSS, and
 * grid/absolute positioning inside it is unreliable.
 */
export const alt = `${store.name} — fresh produce, weighed the way you want it`;
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpengraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          backgroundColor: "#FBF8F2",
          padding: "64px 72px",
          fontFamily: "sans-serif",
        }}
      >
        {/* Brand lockup */}
        <div style={{ display: "flex", alignItems: "center", gap: 20 }}>
          <div
            style={{
              width: 76,
              height: 76,
              borderRadius: 22,
              backgroundColor: "#2A6B46",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "#ffffff",
              fontSize: 42,
              fontWeight: 700,
            }}
          >
            V
          </div>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <div style={{ fontSize: 34, fontWeight: 700, color: "#14211A" }}>
              Vedant
            </div>
            <div
              style={{
                fontSize: 17,
                letterSpacing: 3,
                color: "#8A968F",
                textTransform: "uppercase",
              }}
            >
              Fruit &amp; Vegetables
            </div>
          </div>
        </div>

        {/* Headline */}
        <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
          <div
            style={{
              fontSize: 66,
              lineHeight: 1.05,
              letterSpacing: -2,
              color: "#14211A",
              maxWidth: 900,
            }}
          >
            Today&apos;s produce, weighed the way you want it.
          </div>
          <div style={{ fontSize: 26, color: "#55635B", maxWidth: 820 }}>
            Choose 250 g, 500 g or 2 kg. We weigh, pack and deliver the same day.
          </div>
        </div>

        {/* Footer strip */}
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <div
            style={{
              height: 8,
              width: 120,
              borderRadius: 999,
              backgroundColor: "#DE8E18",
            }}
          />
          <div style={{ fontSize: 22, color: "#55635B" }}>
            Free delivery over &#8377;500 &nbsp;·&nbsp; Local same-day slots
          </div>
        </div>
      </div>
    ),
    { ...size },
  );
}
