
/**
 * Vedant Fruit & Vegetables — service worker.
 *
 * STRATEGY, AND WHY
 * -----------------
 * `navigation`   network-first (2.5s timeout) → cache → `/offline`.
 *                A grocery catalogue changes daily, so serving a stale page as
 *                the *primary* experience would show yesterday's prices. The
 *                cache exists to make the app open instantly and to survive a
 *                dropped connection, not to serve old data.
 * `static`       stale-while-revalidate for `/_next/static`, images, icons and
 *                fonts. These are content-hashed and immutable, so revalidation
 *                is free and correctness is guaranteed.
 * `supabase`     **never cached, never intercepted.** Responses contain a
 *                customer's own orders, addresses and basket. Caching them would
 *                (a) leak one customer's data to the next person on a shared
 *                device and (b) show stale prices at checkout. Requests to the
 *                API host are explicitly passed through untouched.
 * `other GET`    network-first with a cache fallback.
 *
 * The worker is versioned; a new deploy changes CACHE and the old cache is
 * deleted on activate. It only takes over when the client asks it to
 * (`SKIP_WAITING`), so an update never interrupts an in-progress checkout — the
 * page shows an "update available" affordance instead.
 */

const CACHE = "vedant-v1";
const OFFLINE_URL = "/offline";

/** Minimal shell. Kept tiny: the app is server-rendered, so there is little to
 *  precache and precaching HTML risks serving a stale catalogue. */
const PRECACHE = ["/offline", "/manifest.webmanifest", "/icons/icon.svg"];

self.addEventListener("install", (event) => {
  event.waitUntil(
    (async () => {
      const cache = await caches.open(CACHE);
      await cache.addAll(PRECACHE);
    })(),
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)));
      await self.clients.claim();
    })(),
  );
});

self.addEventListener("message", (event) => {
  if (event.data === "SKIP_WAITING") self.skipWaiting();
});

/** Hosts whose responses must never be cached. */
function isSupabaseRequest(url) {
  return /\.supabase\.(co|in)$/.test(url.hostname) || url.pathname.startsWith("/auth/v1");
}

function isStaticAsset(url) {
  return (
    url.pathname.startsWith("/_next/static/") ||
    url.pathname.startsWith("/icons/") ||
    url.pathname.startsWith("/images/") ||
    /\.(?:css|js|woff2?|png|jpg|jpeg|webp|avif|svg|ico)$/i.test(url.pathname)
  );
}

self.addEventListener("fetch", (event) => {
  const request = event.request;

  // Only GET is cacheable; POST/PATCH/DELETE (including place_order) always go
  // straight to the network.
  if (request.method !== "GET") return;

  const url = new URL(request.url);

  // Never touch personal data or auth traffic.
  if (isSupabaseRequest(url)) return;

  // Cross-origin assets we do not control: leave alone.
  if (url.origin !== self.location.origin) return;

  if (request.mode === "navigate") {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (isStaticAsset(url)) {
    event.respondWith(staleWhileRevalidate(request));
    return;
  }

  event.respondWith(networkFirstWithCacheFallback(request));
});

async function networkFirstNavigation(request) {
  const cache = await caches.open(CACHE);
  try {
    // A hung network must not leave the customer staring at a white screen.
    const response = await withTimeout(fetch(request), 2500);
    if (response && response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch {
    const cached = await cache.match(request);
    if (cached) return cached;
    const offline = await cache.match(OFFLINE_URL);
    if (offline) return offline;
    return new Response(
      "<!doctype html><meta charset=utf-8><title>Offline</title><body style=\"font-family:system-ui;padding:24px\"><h1>You are offline</h1><p>Please reconnect and try again.</p></body>",
      { status: 503, headers: { "Content-Type": "text/html; charset=utf-8" } },
    );
  }
}

async function staleWhileRevalidate(request) {
  const cache = await caches.open(CACHE);
  const cached = await cache.match(request);

  const network = fetch(request)
    .then((response) => {
      if (response && response.ok) cache.put(request, response.clone());
      return response;
    })
    .catch(() => undefined);

  return cached ?? (await network) ?? Response.error();
}

async function networkFirstWithCacheFallback(request) {
  const cache = await caches.open(CACHE);
  try {
    const response = await fetch(request);
    if (response && response.ok) cache.put(request, response.clone());
    return response;
  } catch {
    const cached = await cache.match(request);
    if (cached) return cached;
    return Response.error();
  }
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), ms)),
  ]);
}
