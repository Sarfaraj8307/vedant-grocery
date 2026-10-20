"use client";

import { createBrowserClient } from "@supabase/ssr";
import { publicEnv } from "@/lib/env";

/**
 * Browser Supabase client.
 *
 * Uses the publishable (anon) key only. Every request is subject to Postgres
 * RLS, so this client is safe to ship to the public — that is the whole point
 * of the architecture: there is no privileged path in this codebase.
 *
 * Memoised because `createBrowserClient` sets up an auth/realtime listener; a
 * new instance per render would leak subscriptions.
 */
let browserClient: ReturnType<typeof createBrowserClient> | null = null;

export function getSupabaseBrowserClient() {
  if (browserClient) return browserClient;
  const { supabaseUrl, supabaseKey } = publicEnv();
  browserClient = createBrowserClient(supabaseUrl, supabaseKey);
  return browserClient;
}
