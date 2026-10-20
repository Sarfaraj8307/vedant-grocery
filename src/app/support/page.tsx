import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft, ChevronRight, HeadphonesIcon } from "lucide-react";

import { ComplaintForm } from "@/components/support/complaint-form";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { requireUser } from "@/lib/auth";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { COMPLAINT_STATUS_META, formatDateTime } from "@/lib/format";
import { store } from "@/config/store";
import type { ComplaintRow, OrderRow } from "@/types/database";

export const metadata: Metadata = {
  title: "Help & support",
  description:
    "Tell us about a missing item, a quality problem or anything else about your order.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Support inbox.
 *
 * This page requires sign-in because `complaints` is scoped to the
 * authenticated customer by RLS. The public `/contact` page covers everything a
 * signed-out visitor needs (including how to reach the shop), so nobody hits a
 * dead end.
 */
export default async function SupportPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  await requireUser("/support");
  const query = await searchParams;
  const defaultOrderId =
    typeof query.order === "string" ? query.order : undefined;

  const supabase = await getSupabaseServerClient();
  const [{ data: complaintData }, { data: orderData }] = await Promise.all([
    supabase.from("complaints").select("*").order("created_at", { ascending: false }),
    supabase
      .from("orders")
      .select("id, placed_at, final_total")
      .order("placed_at", { ascending: false })
      .limit(20),
  ]);

  const complaints = (complaintData ?? []) as ComplaintRow[];
  const orders = (orderData ?? []) as Pick<
    OrderRow,
    "id" | "placed_at" | "final_total"
  >[];

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <div>
        <Link
          href="/account"
          className="inline-flex items-center gap-1.5 text-small text-ink-muted transition-colors hover:text-ink"
        >
          <ArrowLeft className="size-3.5" aria-hidden="true" />
          Your account
        </Link>
        <h1 className="mt-3 font-display text-h1 text-ink">Help &amp; support</h1>
        <p className="mt-1 max-w-[60ch] text-body text-ink-muted">
          Missing item, wrong weight, or something that did not look fresh? Tell us
          and we will put it right. We reply on this page and, where you have asked
          for it, {store.name} may also call you.
        </p>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
        {/* Threads */}
        <section aria-labelledby="threads-heading">
          <h2 id="threads-heading" className="font-display text-h3 text-ink">
            Your messages
          </h2>

          {complaints.length === 0 ? (
            <EmptyState
              className="mt-3 rounded-lg border border-line bg-surface shadow-xs"
              illustration={
                <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
                  <HeadphonesIcon
                    className="size-7 text-leaf-500"
                    aria-hidden="true"
                  />
                </span>
              }
              title="No messages yet"
              description="Use the form to tell us about a problem. We keep the whole conversation in one place so nothing gets lost."
            />
          ) : (
            <ul className="mt-3 flex flex-col gap-2">
              {complaints.map((complaint) => {
                const meta = COMPLAINT_STATUS_META[complaint.status];
                return (
                  <li key={complaint.id}>
                    <Link
                      href={`/support/${complaint.id}`}
                      className="flex items-start gap-3 rounded-lg border border-line bg-surface p-4 shadow-xs transition-[box-shadow,border-color] duration-[var(--motion-base)] hover:border-line-strong hover:shadow-sm"
                    >
                      <span className="min-w-0 flex-1">
                        <span className="flex flex-wrap items-center gap-2">
                          <Badge
                            variant={
                              complaint.status === "RESOLVED"
                                ? "success"
                                : complaint.status === "OPEN"
                                  ? "info"
                                  : "neutral"
                            }
                            size="sm"
                          >
                            {meta?.label ?? complaint.status}
                          </Badge>
                          <time
                            dateTime={complaint.created_at}
                            className="text-small text-ink-subtle"
                          >
                            {formatDateTime(complaint.created_at)}
                          </time>
                        </span>
                        <span className="mt-1.5 block text-body font-medium text-ink">
                          {complaint.subject}
                        </span>
                        <span className="mt-0.5 line-clamp-2 block text-small text-ink-muted">
                          {complaint.description}
                        </span>
                        {complaint.resolution ? (
                          <span className="mt-1.5 block rounded-xs bg-success-bg px-2 py-1 text-small text-success">
                            {complaint.resolution}
                          </span>
                        ) : null}
                      </span>
                      <ChevronRight
                        className="mt-1 size-4 shrink-0 text-ink-subtle"
                        aria-hidden="true"
                      />
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {/* New message */}
        <section
          aria-labelledby="new-message-heading"
          className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5 lg:sticky lg:top-[calc(var(--header-h)+16px)]"
        >
          <h2 id="new-message-heading" className="font-display text-h3 text-ink">
            Send us a message
          </h2>
          <p className="mt-1 mb-4 text-small text-ink-muted">
            We usually reply the same day.
          </p>
          <ComplaintForm orders={orders} defaultOrderId={defaultOrderId} />
        </section>
      </div>

      <p className="text-small text-ink-subtle">
        Prefer a different route?{" "}
        <Link href="/contact" className="text-leaf-700 underline underline-offset-2">
          See all the ways to reach us
        </Link>
        .{" "}
        <Button asChild variant="link" size="sm">
          <Link href="/orders">Back to your orders</Link>
        </Button>
      </p>
    </div>
  );
}
