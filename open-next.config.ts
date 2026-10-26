import { defineCloudflareConfig } from "@opennextjs/cloudflare";

/**
 * OpenNext for Cloudflare Workers.
 *
 * Builds the Next.js app into a single Worker (`opennextjs-cloudflare build`)
 * so the site runs on Cloudflare's edge runtime instead of Vercel. The
 * `proxy.ts` middleware is mapped automatically by the adapter.
 */
export default defineCloudflareConfig();
