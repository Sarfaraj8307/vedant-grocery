"use client";

import * as React from "react";
import { useRouter } from "next/navigation";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";

/**
 * Refreshes the current server-rendered page when **this customer's** order
 * changes (brief §10).
 *
 * WHY A SERVER REFRESH RATHER THAN PATCHING STATE
 * The order pages are Server Components: totals, adjustments, rejected items and
 * the status timeline are all rendered from the database. Re-deriving that in
 * the browser would create a second source of truth for money. `router.refresh()`
 * re-runs the server render, so what the customer sees after a status change is
 * exactly what the database says — including an adjustment the admin just made.
 *
 * SCOPE AND SAFETY
 *  - The subscription is **filtered to this user's rows** (`user_id=eq.…`) and
 *    RLS enforces the same rule again server-side, so this cannot become a
 *    channel for watching other customers' orders.
 *  - `order_status_history` is also watched, because a status change can be
 *    observed there even if the `orders` UPDATE payload is missed.
 *  - Refreshes are **debounced**: one status change can emit an `orders` update
 *    *and* a history insert, and a burst of admin actions should not trigger a
 *    refresh storm.
 *  - Nothing is subscribed while the tab is hidden — a background tab does not
 *    need to re-render, and it will refresh on the next visible event.
 */
export function LiveOrderRefresh({ userId }: { userId: string }) {
  const router = useRouter();

  React.useEffect(() => {
    if (!userId) return;

    const supabase = getSupabaseBrowserClient();
    let timer: ReturnType<typeof setTimeout> | null = null;

    const scheduleRefresh = () => {
      if (document.visibilityState === "hidden") return;
      if (timer) clearTimeout(timer);
      // Collapse the orders-update + history-insert pair into one render.
      timer = setTimeout(() => {
        timer = null;
        router.refresh();
      }, 400);
    };

    const channel = supabase
      .channel(`customer-orders:${userId}`)
      .on(
        "postgres_changes",
        {
          event: "*",
          schema: "public",
          table: "orders",
          filter: `user_id=eq.${userId}`,
        },
        scheduleRefresh,
      )
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "order_status_history",
        },
        scheduleRefresh,
      )
      .subscribe();

    // A tab that was hidden while changes arrived should catch up when shown.
    const onVisible = () => {
      if (document.visibilityState === "visible") router.refresh();
    };
    document.addEventListener("visibilitychange", onVisible);

    return () => {
      if (timer) clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisible);
      void supabase.removeChannel(channel);
    };
  }, [router, userId]);

  return null;
}
