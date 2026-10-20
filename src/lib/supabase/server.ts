import { cookies } from "next/headers";
import { createServerClient } from "@supabase/ssr";
import { publicEnv } from "@/lib/env";

/**
 * Server Supabase client bound to the incoming request's cookies.
 *
 * This is the client used by Server Components, Server Actions and Route
 * Handlers. It carries the *customer's* session — never a privileged one — so
 * the same RLS policies apply on the server as in the browser. There is no
 * service-role client anywhere in this codebase (see docs/BACKEND_CONTRACT.md).
 */
export async function getSupabaseServerClient() {
  const cookieStore = await cookies();
  const { supabaseUrl, supabaseKey } = publicEnv();

  return createServerClient(supabaseUrl, supabaseKey, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(cookiesToSet) {
        try {
          for (const { name, value, options } of cookiesToSet) {
            cookieStore.set(name, value, options);
          }
        } catch {
          // Called from a Server Component render, where cookies are
          // read-only. Session refresh is handled by `proxy.ts`, so this is
          // safe to ignore rather than crash the render.
        }
      },
    },
  });
}
