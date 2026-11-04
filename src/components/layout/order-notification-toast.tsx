"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";

type OutboxRow = {
  id?: number | string;
  user_id?: string | null;
  audience?: string;
  type?: string;
  title?: string;
  body?: string;
  created_at?: string;
};

/**
 * Live "over the browser" order notifications.
 *
 * Listens to this customer's `notification_outbox` rows via Supabase Realtime
 * and surfaces each new one as a toast the moment it lands. The store's backend
 * writes a row here whenever the admin advances an order (confirmed → packed →
 * being delivered), so the customer sees the update live without a page reload.
 *
 * SAFETY
 *  - The subscription is scoped to `audience=eq.customer`. RLS on
 *    `notification_outbox` already restricts delivery to the subscriber's own
 *    rows (`user_id = auth.uid()`); the extra `user_id` check below is a
 *    belt-and-braces guard, not the primary control.
 *  - Only INSERTs are watched, so a fresh load reports no backlog of old toasts.
 */
export function OrderNotificationToast({ userId }: { userId: string }) {
  const router = useRouter();

  React.useEffect(() => {
    if (!userId) return;

    const supabase = getSupabaseBrowserClient();
    const channel = supabase
      .channel(`customer-notifications:${userId}`)
      .on(
        "postgres_changes",
        {
          event: "INSERT",
          schema: "public",
          table: "notification_outbox",
          filter: "audience=eq.customer",
        },
        (payload: { new: OutboxRow }) => {
          const row = payload.new;
          if (row.user_id !== userId) return;

          const title = row.title?.trim() || "New update";
          const body = row.body?.trim();

          toast(title, {
            description: body || undefined,
            action: {
              label: "Open",
              onClick: () => router.push("/notifications"),
            },
          });
        },
      )
      .subscribe();

    return () => {
      void supabase.removeChannel(channel);
    };
  }, [router, userId]);

  return null;
}
