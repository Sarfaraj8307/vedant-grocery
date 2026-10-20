import type { MetadataRoute } from "next";

import { store } from "@/config/store";

/**
 * PWA manifest.
 *
 * `display: "standalone"` with `start_url: "/"` so a launch from the home screen
 * opens the shop, not a browser tab. Both `any` and `maskable` icons are declared
 * — without a maskable icon Android crops the mark and it looks broken on the
 * launcher, which is the single most visible way a PWA reads as amateur.
 *
 * `theme_color` is the warm paper background, not the brand green: the status bar
 * sits directly above the paper-coloured header, so a green bar would create a
 * hard seam.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: `${store.name} — fresh produce delivered`,
    short_name: "Vedant",
    description: store.shortDescription,
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait-primary",
    background_color: "#FBF8F2",
    theme_color: "#FBF8F2",
    lang: "en-IN",
    dir: "ltr",
    categories: ["food", "shopping", "lifestyle"],
    icons: [
      {
        src: "/icons/icon.svg",
        sizes: "any",
        type: "image/svg+xml",
        purpose: "any",
      },
      {
        src: "/icons/pwa-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/pwa-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/maskable-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
    shortcuts: [
      {
        name: "Browse produce",
        short_name: "Browse",
        url: "/categories",
        description: "See everything in stock today",
      },
      {
        name: "Today's offers",
        short_name: "Offers",
        url: "/offers",
        description: "Live prices from this morning's market",
      },
      {
        name: "Your orders",
        short_name: "Orders",
        url: "/orders",
        description: "Track a delivery or reorder",
      },
    ],
  };
}
