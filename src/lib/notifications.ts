import "server-only";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import type { NotificationRow } from "@/types/database";

/**
 * In-app notifications.
 *
 * `notifications` has SELECT and UPDATE policies for the owner and **no INSERT
 * policy at all** (verified — docs/BACKEND_CONTRACT.md §4). That is the correct
 * design: only the backend or the future admin app can create a notification,
 * and the customer can only read their own and mark them read.
 *
 * Consequence for this app: the list is real but will be empty until the admin
 * side starts writing rows. The UI says so honestly rather than inventing
 * placeholder messages.
 */
export async function listNotifications(limit = 50): Promise<NotificationRow[]> {
  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("notification_outbox")
    .select("id, user_id, title, body, read_at, created_at")
    .eq("audience", "customer")
    .order("created_at", { ascending: false })
    .limit(limit);

  return (data ?? []).map((row) => ({ ...row, id: String(row.id) })) as NotificationRow[];
}

export function countUnread(notifications: readonly NotificationRow[]): number {
  return notifications.filter((notification) => notification.read_at === null).length;
}
