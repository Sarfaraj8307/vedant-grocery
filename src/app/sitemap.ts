import type { MetadataRoute } from "next";

import { getCatalogue, getCategories } from "@/lib/catalogue";
import { absoluteUrl } from "@/config/site";

/**
 * Sitemap.
 *
 * Only genuinely public, indexable content is listed: the home page, the browse
 * page, each category and each product. Private routes are excluded entirely
 * (they are also `Disallow`ed in robots.txt and `noindex`ed in metadata).
 *
 * Regenerated hourly rather than at build time, so adding a product in the admin
 * app appears without a redeploy. A catalogue failure degrades to the static
 * routes instead of breaking the sitemap — a broken sitemap is worse than an
 * incomplete one.
 */
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const staticRoutes: MetadataRoute.Sitemap = [
    { url: absoluteUrl("/"), lastModified: now, changeFrequency: "daily", priority: 1 },
    {
      url: absoluteUrl("/categories"),
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.9,
    },
    {
      url: absoluteUrl("/offers"),
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.8,
    },
    {
      url: absoluteUrl("/about"),
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
    },
    {
      url: absoluteUrl("/contact"),
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.5,
    },
    {
      url: absoluteUrl("/privacy"),
      lastModified: now,
      changeFrequency: "yearly",
      priority: 0.2,
    },
    {
      url: absoluteUrl("/terms"),
      lastModified: now,
      changeFrequency: "yearly",
      priority: 0.2,
    },
  ];

  try {
    const [categories, catalogue] = await Promise.all([
      getCategories(),
      getCatalogue(),
    ]);

    const categoryRoutes: MetadataRoute.Sitemap = categories.map((category) => ({
      url: absoluteUrl(`/categories/${category.id}`),
      lastModified: new Date(category.updated_at),
      changeFrequency: "daily",
      priority: 0.8,
    }));

    const productRoutes: MetadataRoute.Sitemap = catalogue.map((product) => ({
      url: absoluteUrl(`/products/${product.id}`),
      lastModified: new Date(product.updated_at),
      changeFrequency: "daily",
      priority: 0.7,
    }));

    return [...staticRoutes, ...categoryRoutes, ...productRoutes];
  } catch {
    return staticRoutes;
  }
}
