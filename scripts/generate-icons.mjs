/**
 * Icon generator — zero dependencies.
 *
 * Produces the PWA raster icons that iOS and Android require (SVG icons alone
 * are not enough: iOS ignores them for `apple-touch-icon` and Android needs a
 * real maskable raster).
 *
 * Why hand-roll a rasteriser instead of adding `sharp` or `canvas`?
 *  • those packages carry native build steps and a large dependency tree, on a
 *    project whose brief says to avoid unnecessary dependencies;
 *  • the artwork is a handful of primitives, which is exactly what a 60-line
 *    rasteriser handles well;
 *  • the output is deterministic and reviewable, and a designer can replace the
 *    PNGs at any time without touching code.
 *
 * PNG is encoded manually (IHDR / IDAT / IEND with zlib deflate, which Node
 * provides). Shapes are drawn with 3× supersampling, so edges are anti-aliased.
 *
 * Run with: node scripts/generate-icons.mjs
 */

import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT_DIR = join(ROOT, "public", "icons");

/* ------------------------------------------------------------------ colours */

const PALETTE = {
  leaf600: [0x2a, 0x6b, 0x46],
  leaf700: [0x1f, 0x55, 0x37],
  leaf900: [0x14, 0x2f, 0x21],
  marigold400: [0xef, 0xa6, 0x32],
  marigold500: [0xde, 0x8e, 0x18],
  paper: [0xfb, 0xf8, 0xf2],
  white: [0xff, 0xff, 0xff],
};

/* ------------------------------------------------------------------ PNG code */

const CRC_TABLE = (() => {
  const table = new Int32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[n] = c;
  }
  return table;
})();

function crc32(buffer) {
  let crc = -1;
  for (let i = 0; i < buffer.length; i += 1) {
    crc = CRC_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ -1) >>> 0;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeBuffer = Buffer.from(type, "ascii");
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([typeBuffer, data])), 0);
  return Buffer.concat([length, typeBuffer, data, crc]);
}

/** Encode straight RGBA bytes (0–255, row-major) as a PNG buffer. */
function encodePng(width, height, rgba) {
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // colour type: RGBA
  ihdr[10] = 0; // deflate
  ihdr[11] = 0; // adaptive filtering
  ihdr[12] = 0; // no interlace

  // Each scanline is prefixed with filter type 0 (None).
  const stride = width * 4;
  const raw = Buffer.alloc((stride + 1) * height);
  for (let y = 0; y < height; y += 1) {
    raw[y * (stride + 1)] = 0;
    rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride);
  }

  return Buffer.concat([
    signature,
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/* ------------------------------------------------------------------ rasteriser */

const SUPERSAMPLE = 3;

/**
 * Render one icon.
 * `draw` receives a `paint(x, y, color, alpha)` primitive operating in
 * supersampled coordinate space where the design box is `size × size`.
 */
function render(size, draw) {
  const hi = size * SUPERSAMPLE;
  // Float RGBA accumulation buffer, values 0–1, premultiplied-free.
  const buffer = new Float32Array(hi * hi * 4);

  const paint = (x, y, color, alpha = 1) => {
    if (x < 0 || y < 0 || x >= hi || y >= hi) return;
    const index = (y * hi + x) * 4;
    const a = alpha;
    buffer[index] = buffer[index] * (1 - a) + (color[0] / 255) * a;
    buffer[index + 1] = buffer[index + 1] * (1 - a) + (color[1] / 255) * a;
    buffer[index + 2] = buffer[index + 2] * (1 - a) + (color[2] / 255) * a;
    buffer[index + 3] = buffer[index + 3] * (1 - a) + a;
  };

  const tools = {
    hi,
    scale: SUPERSAMPLE,
    paint,
    /** Rounded rectangle in design coordinates. */
    roundedRect(x0, y0, x1, y1, radius, color) {
      for (let y = 0; y < hi; y += 1) {
        for (let x = 0; x < hi; x += 1) {
          const dx = x / SUPERSAMPLE;
          const dy = y / SUPERSAMPLE;
          if (dx < x0 || dx > x1 || dy < y0 || dy > y1) continue;
          const cx = Math.min(Math.max(dx, x0 + radius), x1 - radius);
          const cy = Math.min(Math.max(dy, y0 + radius), y1 - radius);
          const dist = Math.hypot(dx - cx, dy - cy);
          if (dist <= radius) paint(x, y, color);
        }
      }
    },
    circle(cx, cy, r, color) {
      for (let y = 0; y < hi; y += 1) {
        for (let x = 0; x < hi; x += 1) {
          const dx = x / SUPERSAMPLE - cx;
          const dy = y / SUPERSAMPLE - cy;
          if (dx * dx + dy * dy <= r * r) paint(x, y, color);
        }
      }
    },
    /** Rotated ellipse — used for the leaf. */
    ellipse(cx, cy, rx, ry, angleDeg, color) {
      const angle = (angleDeg * Math.PI) / 180;
      const cos = Math.cos(-angle);
      const sin = Math.sin(-angle);
      for (let y = 0; y < hi; y += 1) {
        for (let x = 0; x < hi; x += 1) {
          const px = x / SUPERSAMPLE - cx;
          const py = y / SUPERSAMPLE - cy;
          const rxr = px * cos - py * sin;
          const ryr = px * sin + py * cos;
          if ((rxr * rxr) / (rx * rx) + (ryr * ryr) / (ry * ry) <= 1) {
            paint(x, y, color);
          }
        }
      }
    },
    /** Thick line segment with round caps — used for the "V". */
    capsule(x0, y0, x1, y1, thickness, color) {
      const r = thickness / 2;
      const vx = x1 - x0;
      const vy = y1 - y0;
      const lengthSq = vx * vx + vy * vy;
      for (let y = 0; y < hi; y += 1) {
        for (let x = 0; x < hi; x += 1) {
          const px = x / SUPERSAMPLE;
          const py = y / SUPERSAMPLE;
          let t = lengthSq === 0 ? 0 : ((px - x0) * vx + (py - y0) * vy) / lengthSq;
          t = Math.min(1, Math.max(0, t));
          const dx = px - (x0 + t * vx);
          const dy = py - (y0 + t * vy);
          if (dx * dx + dy * dy <= r * r) paint(x, y, color);
        }
      }
    },
  };

  draw(tools);

  // Downsample with a box filter → anti-aliased output.
  const out = Buffer.alloc(size * size * 4);
  const samples = SUPERSAMPLE * SUPERSAMPLE;
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      let r = 0;
      let g = 0;
      let b = 0;
      let a = 0;
      for (let sy = 0; sy < SUPERSAMPLE; sy += 1) {
        for (let sx = 0; sx < SUPERSAMPLE; sx += 1) {
          const index = ((y * SUPERSAMPLE + sy) * hi + (x * SUPERSAMPLE + sx)) * 4;
          r += buffer[index];
          g += buffer[index + 1];
          b += buffer[index + 2];
          a += buffer[index + 3];
        }
      }
      // Un-premultiply against the averaged alpha so edges stay the right hue.
      const alpha = a / samples;
      const outIndex = (y * size + x) * 4;
      if (alpha > 0) {
        out[outIndex] = Math.round(Math.min(1, r / a) * 255);
        out[outIndex + 1] = Math.round(Math.min(1, g / a) * 255);
        out[outIndex + 2] = Math.round(Math.min(1, b / a) * 255);
      }
      out[outIndex + 3] = Math.round(alpha * 255);
    }
  }

  return out;
}

/* ------------------------------------------------------------------ artwork */

/**
 * The Vedant mark: a produce crate holding a "V" whose right stroke grows into a
 * leaf. `inset` shrinks the artwork inside the canvas — required for the maskable
 * variant, where Android may crop to a circle covering the inner 80%.
 */
function drawMark({ roundedRect, capsule, ellipse, circle }, size, inset) {
  const s = size * (1 - inset * 2);
  const o = size * inset;
  const at = (value) => o + value * s;

  // Crate
  roundedRect(at(0), at(0), at(1), at(1), s * 0.28, PALETTE.leaf600);

  // "V" — two capsules meeting at the bottom
  const thickness = s * 0.105;
  capsule(at(0.235), at(0.3), at(0.5), at(0.74), thickness, PALETTE.white);
  capsule(at(0.5), at(0.74), at(0.765), at(0.3), thickness, PALETTE.white);

  // Leaf on the right stroke
  ellipse(at(0.72), at(0.255), s * 0.115, s * 0.075, -38, PALETTE.marigold400);
  circle(at(0.655), at(0.315), s * 0.028, PALETTE.marigold500);

  // A subtle paper highlight along the top edge gives the mark depth at 512px.
  roundedRect(
    at(0.06),
    at(0.04),
    at(0.94),
    at(0.12),
    s * 0.04,
    PALETTE.paper,
  );
  // Re-paint the V and leaf over the highlight so it reads as a sheen, not a bar.
  capsule(at(0.235), at(0.3), at(0.5), at(0.74), thickness, PALETTE.white);
  capsule(at(0.5), at(0.74), at(0.765), at(0.3), thickness, PALETTE.white);
  ellipse(at(0.72), at(0.255), s * 0.115, s * 0.075, -38, PALETTE.marigold400);
  circle(at(0.655), at(0.315), s * 0.028, PALETTE.marigold500);
}

function writeIcon(filename, size, inset, transparentBackground) {
  const rgba = render(size, (tools) => {
    if (!transparentBackground) {
      // Draw the crate across the full canvas, then inset only the glyph.
      drawMark(tools, size, inset);
      return;
    }
    drawMark(tools, size, inset);
  });

  const png = encodePng(size, size, rgba);
  writeFileSync(join(OUT_DIR, filename), png);
  return `${filename} (${size}×${size}, ${Math.round(png.length / 1024)} KB)`;
}

/* ------------------------------------------------------------------ SVG icon */

const SVG_ICON = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" width="32" height="32" role="img" aria-label="Vedant Fruit &amp; Vegetables">
  <rect width="32" height="32" rx="9" fill="#2A6B46"/>
  <path d="M9.5 10.5 16 22.5l6.5-12" fill="none" stroke="#fff" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M21.2 7.4c3.2 0 4.9 1.7 4.9 4.8 0 .7-.6 1.2-1.2 1.2-3.1 0-4.9-1.7-4.9-4.8 0-.7.5-1.2 1.2-1.2Z" fill="#EFA632"/>
</svg>
`;

/* ------------------------------------------------------------------ main */

mkdirSync(OUT_DIR, { recursive: true });

const results = [
  writeIcon("pwa-192.png", 192, 0.06, false),
  writeIcon("pwa-512.png", 512, 0.06, false),
  // Maskable: Android may crop to the inner 80% circle, so the mark is inset
  // further and the background bleeds to every edge.
  writeIcon("maskable-512.png", 512, 0.2, false),
  writeIcon("apple-touch-icon.png", 180, 0.05, false),
];

writeFileSync(join(OUT_DIR, "icon.svg"), SVG_ICON, "utf8");
results.push("icon.svg");

console.log("Generated icons in public/icons:");
for (const line of results) console.log("  •", line);
