"use client";

import { getSupabaseBrowserClient } from "@/lib/supabase/client";
import type { ComplaintMessageRow, ComplaintRow } from "@/types/database";

/**
 * Customer support threads.
 *
 * Security notes (verified against the live policies — docs/BACKEND_CONTRACT.md §4):
 *  • INSERT on `complaints` requires `user_id = auth.uid()`, `status = 'OPEN'`,
 *    `resolved_by/resolved_at IS NULL`, and — when an order is attached — that
 *    the order belongs to the caller. Attaching someone else's order is rejected
 *    by the database.
 *  • INSERT on `complaint_messages` requires `author_role = 'CUSTOMER'` **and**
 *    ownership of the parent complaint. A customer cannot impersonate an admin
 *    reply.
 *  • UPDATE on `complaints` is admin-only, so a customer cannot mark their own
 *    complaint resolved.
 *
 * The client therefore never sends `author_role`/`user_id` as an authority — the
 * payload is checked against the policy regardless.
 */

export type SupportResult<T> = { ok: true; data: T } | { ok: false; error: string };

export async function createComplaint(input: {
  subject: string;
  description: string;
  orderId?: string | null;
}): Promise<SupportResult<string>> {
  const supabase = getSupabaseBrowserClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) return { ok: false, error: "Please sign in to send us a message." };

  const { data, error } = await supabase
    .from("complaints")
    .insert({
      subject: input.subject.trim(),
      description: input.description.trim(),
      order_id: input.orderId || null,
    })
    .select("id")
    .single();

  if (error || !data) {
    // Surface the real database error (RLS denial, constraint violation, etc.)
    // so the failure is diagnosable instead of an opaque generic message.
    return {
      ok: false,
      error: error
        ? `We could not send that message (${error.code ?? "error"}). Please try again in a moment.`
        : "We could not send that message. Please try again in a moment.",
    };
  }
  return { ok: true, data: (data as { id: string }).id };
}

export async function listComplaints(): Promise<ComplaintRow[]> {
  const supabase = getSupabaseBrowserClient();
  const { data } = await supabase
    .from("complaints")
    .select("*")
    .order("created_at", { ascending: false });
  return (data ?? []) as ComplaintRow[];
}

export async function getComplaint(
  id: string,
): Promise<{ complaint: ComplaintRow; messages: ComplaintMessageRow[] } | null> {
  const supabase = getSupabaseBrowserClient();
  const { data } = await supabase
    .from("complaints")
    .select(
      `*, complaint_messages ( id, complaint_id, author_id, author_role, body, created_at )`,
    )
    .eq("id", id)
    .maybeSingle();

  if (!data) return null;
  const row = data as unknown as ComplaintRow & {
    complaint_messages: ComplaintMessageRow[] | null;
  };
  return {
    complaint: row,
    messages: [...(row.complaint_messages ?? [])].sort((a, b) =>
      a.created_at.localeCompare(b.created_at),
    ),
  };
}

/** Follow-up message on an existing thread. */
export async function replyToComplaint(
  complaintId: string,
  body: string,
): Promise<SupportResult<ComplaintMessageRow>> {
  const supabase = getSupabaseBrowserClient();
  const { data, error } = await supabase
    .from("complaint_messages")
    .insert({
      complaint_id: complaintId,
      body: body.trim(),
      author_role: "CUSTOMER",
    })
    .select("*")
    .single();

  if (error || !data) {
    return {
      ok: false,
      error: error
        ? `We could not send that reply (${error.code ?? "error"}). Please try again.`
        : "We could not send that reply. Please try again.",
    };
  }
  return { ok: true, data: data as ComplaintMessageRow };
}
