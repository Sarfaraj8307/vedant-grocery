import fs from "node:fs";
import path from "node:path";

/**
 * Live-catalogue fixtures.
 *
 * Dynamic routes (`/categories/[id]`, `/products/[id]`, `/orders/[id]`) need
 * REAL ids. We read them from the live Supabase project using the anon key in
 * `.env.local` — the same key the browser app uses — rather than hard-coding
 * uuids that would silently rot when the catalogue is re-seeded.
 *
 * Values verified live on 2026-10-11 (3 categories, 24 products, 10 offers,
 * 18 delivery slots) and re-fetched at suite start.
 */

function readEnvLocal(): Record<string, string> {
  const env: Record<string, string> = {};
  try {
    const raw = fs.readFileSync(path.resolve(process.cwd(), ".env.local"), "utf8");
    for (const line of raw.split(/\r?\n/)) {
      const m = /^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/.exec(line);
      if (m) env[m[1]] = m[2].replace(/^["']|["']$/g, "");
    }
  } catch {
    /* fall through to defaults */
  }
  return env;
}

const ENV = readEnvLocal();
const SUPABASE_URL =
  process.env.NEXT_PUBLIC_SUPABASE_URL ??
  ENV.NEXT_PUBLIC_SUPABASE_URL ??
  "https://hyibaljxicojfoxbihdn.supabase.co";
const SUPABASE_KEY =
  process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ??
  ENV.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY ??
  "sb_publishable__HIasCDACEN5agfOg05vyA_60jCanfH";

async function rest<T>(pathAndQuery: string): Promise<T[]> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${pathAndQuery}`, {
    headers: { apikey: SUPABASE_KEY, Authorization: `Bearer ${SUPABASE_KEY}` },
  });
  if (!res.ok) throw new Error(`Supabase REST ${res.status} for ${pathAndQuery}`);
  return (await res.json()) as T[];
}

/**
 * Every available weight variant for a product, so a hydration test can seed a
 * cart line that is guaranteed to match whatever `defaultWeight()` picks on the
 * server. Returns an empty array for a base-price-only product.
 */
export async function availableWeightIds(productId: string): Promise<string[]> {
  try {
    const rows = await rest<{ id: string; is_available: boolean }>(
      `product_weights?select=id,is_available&product_id=eq.${productId}&is_available=is.true`,
    );
    return rows.map((r) => r.id);
  } catch {
    return [];
  }
}

export type CatalogueFixtures = {
  /** A known-good category id (Fruits). */
  categoryId: string;
  categoryName: string;
  /** A known-good in-stock product id whose name contains "Banana". */
  productId: string;
  productName: string;
  /** A product that appears on the home page RateBoard (has a live offer). */
  offerProductId: string;
  /** Total active products (used for "shelf" assertions). */
  productCount: number;
};

/** Fallbacks verified against the live project — used if the REST probe fails. */
const FALLBACK: CatalogueFixtures = {
  categoryId: "ac972251-0fbf-4330-951c-0eb741ab098e",
  categoryName: "Fruits",
  productId: "a79f19e1-5d89-4106-9426-e7d86aff10ac",
  productName: "Banana (Robusta)",
  offerProductId: "34aa1493-0a37-4bfd-bbd1-79ba415abc5f",
  productCount: 24,
};

export async function loadCatalogueFixtures(): Promise<CatalogueFixtures> {
  try {
    const categories = await rest<{ id: string; name: string }>(
      "categories?select=id,name&order=name",
    );
    const products = await rest<{ id: string; name: string }>(
      "products?select=id,name&is_active=eq.true&order=name",
    );
    const offers = await rest<{ product_id: string }>(
      "offers?select=product_id&is_active=eq.true",
    );

    const fruits =
      categories.find((c) => /fruit/i.test(c.name)) ?? categories[0];
    const banana =
      products.find((p) => /banana/i.test(p.name)) ?? products[0];
    const offerProductId = offers[0]?.product_id ?? FALLBACK.offerProductId;

    return {
      categoryId: fruits?.id ?? FALLBACK.categoryId,
      categoryName: fruits?.name ?? FALLBACK.categoryName,
      productId: banana?.id ?? FALLBACK.productId,
      productName: banana?.name ?? FALLBACK.productName,
      offerProductId,
      productCount: products.length || FALLBACK.productCount,
    };
  } catch {
    return FALLBACK;
  }
}

/** The cart key format used by the store (`productId::weightId`). */
export const CART_STORAGE_KEY = "vedant.cart.v1";
