import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, CheckCircle2 } from "lucide-react";

import { ComplaintReplyBox } from "@/components/support/complaint-form";
import { Badge } from "@/components/ui/badge";
import { requireUser } from "@/lib/auth";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { COMPLAINT_STATUS_META, formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ComplaintMessageRow, ComplaintRow } from "@/types/database";

export const metadata: Metadata = {
  title: "Your message",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

type Thread = ComplaintRow & { complaint_messages: ComplaintMessageRow[] | null };

/**
 * A single support thread.
 *
 * RLS (`complaints_read_self_or_admin` and
 * `complaint_messages_read_owner_or_admin`) makes another customer's thread
 * unreachable, so a guessed id renders `notFound()`.
 *
 * Admin replies are rendered on the opposite side of the thread and labelled
 * "The shop" — a customer must never see an admin message styled as if they wrote
 * it themselves.
 */
export default async function SupportThreadPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  await requireUser("/support");
  const { id } = await params;

  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("complaints")
    .select(
      `*, complaint_messages ( id, complaint_id, author_id, author_role, body, created_at )`,
    )
    .eq("id", id)
    .maybeSingle();

  if (!data) notFound();

  const thread = data as unknown as Thread;
  const messages = [...(thread.complaint_messages ?? [])].sort((a, b) =>
    a.created_at.localeCompare(b.created_at),
  );
  const meta = COMPLAINT_STATUS_META[thread.status];
  const closed = thread.status === "CLOSED";

  return (
    <div className="shell flex flex-col gap-5 py-6 sm:py-8">
      <div>
        <Link
          href="/support"
          className="inline-flex items-center gap-1.5 text-small text-ink-muted transition-colors hover:text-ink"
        >
          <ArrowLeft className="size-3.5" aria-hidden="true" />
          All messages
        </Link>
      </div>

      <header className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <div className="flex flex-wrap items-center gap-2">
          <Badge
            variant={
              thread.status === "RESOLVED"
                ? "success"
                : thread.status === "OPEN"
                  ? "info"
                  : "neutral"
            }
          >
            {meta?.label ?? thread.status}
          </Badge>
          <time
            dateTime={thread.created_at}
            className="text-small text-ink-subtle"
          >
            Opened {formatDateTime(thread.created_at)}
          </time>
        </div>
        <h1 className="mt-2.5 font-display text-h2 text-ink">{thread.subject}</h1>
        <p className="mt-2 max-w-[68ch] whitespace-pre-line text-body text-ink-muted">
          {thread.description}
        </p>
        {meta?.description ? (
          <p className="mt-2 text-small text-ink-subtle">{meta.description}</p>
        ) : null}
      </header>

      {thread.resolution ? (
        <p className="flex items-start gap-2 rounded-md border border-success-bg bg-success-bg px-4 py-3 text-small text-success">
          <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            <strong className="font-semibold">Resolved.</strong> {thread.resolution}
          </span>
        </p>
      ) : null}

      <section
        aria-label="Conversation"
        className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5"
      >
        <h2 className="font-display text-h3 text-ink">Conversation</h2>

        <ol className="mt-4 flex flex-col gap-4">
          {messages.map((message) => {
            const fromShop = message.author_role === "ADMIN";
            return (
              <li
                key={message.id}
                className={cn("flex", fromShop ? "justify-start" : "justify-end")}
              >
                <div
                  className={cn(
                    "max-w-[min(46ch,100%)] rounded-lg border px-3.5 py-2.5",
                    fromShop
                      ? "border-leaf-600/30 bg-leaf-50"
                      : "border-line bg-surface-muted",
                  )}
                >
                  <p className="text-caption text-ink-subtle">
                    {fromShop ? "The shop" : "You"}
                  </p>
                  <p className="mt-1 whitespace-pre-line text-body text-ink">
                    {message.body}
                  </p>
                  <time
                    dateTime={message.created_at}
                    className="mt-1 block text-[11px] text-ink-subtle"
                  >
                    {formatDateTime(message.created_at)}
                  </time>
                </div>
              </li>
            );
          })}
          {messages.length === 0 ? (
            <li className="text-small text-ink-subtle">
              No replies yet — your message is with the shop.
            </li>
          ) : null}
        </ol>

        {closed ? (
          <p className="mt-4 border-t border-line pt-4 text-small text-ink-subtle">
            This conversation is closed. If you still need help,{" "}
            <Link
              href="/support"
              className="text-leaf-700 underline underline-offset-2"
            >
              start a new message
            </Link>
            .
          </p>
        ) : (
          <div className="mt-4">
            <ComplaintReplyBox complaintId={thread.id} />
          </div>
        )}
      </section>
    </div>
  );
}
