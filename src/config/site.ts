/**
 * Site-level constants for metadata, SEO and structured data.
 * Kept separate from `store.ts` (business identity) so SEO copy can be tuned
 * without touching commerce configuration.
 */

export const site = {
  /** Canonical origin. Set `NEXT_PUBLIC_SITE_URL` to the real domain at launch. */
  url: (process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000").replace(
    /\/+$/,
    "",
  ),
  titleTemplate: "%s · Vedant Fruit & Vegetables",
  defaultTitle: "Vedant Fruit & Vegetables — The freshest fruit you've ever tasted",
  defaultDescription:
    "Fruit picked at the market this morning, weighed to your choice of 250 g, 500 g or 1 kg, and delivered the same day. Taste the difference.",
  keywords: [
    "Vedant Fruit and Vegetables",
    "fresh fruits delivery",
    "vegetables home delivery",
    "buy fruits online",
    "fresh vegetables online",
    "grocery delivery",
    "leafy greens delivery",
    "mandi fresh produce",
  ],
  locale: "en_IN",
} as const;

export function absoluteUrl(path = "/"): string {
  return `${site.url}${path.startsWith("/") ? path : `/${path}`}`;
}
