import type { MetadataRoute } from "next";

import { absoluteUrl } from "@/config/site";

/**
 * robots.txt.
 *
 * Private, per-customer routes are disallowed as well as marked `noindex` in
 * their metadata. Belt and braces: a `noindex` meta tag is authoritative, and the
 * `Disallow` stops a crawler from wasting budget discovering it.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: [
          "/account",
          "/account/",
          "/checkout",
          "/cart",
          "/orders",
          "/orders/",
          "/order/",
          "/support",
          "/support/",
          "/notifications",
          "/login",
          "/signup",
          "/forgot-password",
          "/auth/",
          "/search",
          "/offline",
        ],
      },
    ],
    sitemap: absoluteUrl("/sitemap.xml"),
    host: absoluteUrl("/"),
  };
}
