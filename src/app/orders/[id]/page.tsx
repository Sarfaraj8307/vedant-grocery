import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AlertCircle, ArrowLeft, HeadphonesIcon, Receipt } from "lucide-react";

import { OrderStatusBadge } from "@/components/orders/order-status-badge";
import { ReorderButton } from "@/components/orders/reorder-button";
import { CancelOrderPanel } from "@/components/orders/cancel-order-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/misc";
import { requireUser } from "@/lib/auth";
import { LiveOrderRefresh } from "@/components/orders/live-order-refresh";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import {
  ORDER_STATUS_FLOW,
  ORDER_STATUS_META,
  formatDateTime,
  formatSlotWindow,
} from "@/lib/format";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import type {
  AddressRow,
  DeliverySlotRow,
  OrderItemRow,
  OrderRow,
  OrderStatus,
  OrderStatusHistoryRow,
} from "@/types/database";

export const metadata: Metadata = {
  title: "Order details",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

type OrderDetail = OrderRow & {
  order_items: OrderItemRow[];
  order_status_history: OrderStatusHistoryRow[];
  address: AddressRow | null;
  delivery_slot: DeliverySlotRow | null;
};

/*
 * `delivery_slots` is embedded via an EXPLICIT foreign key.
 *
 * `orders` carries two FKs to `delivery_slots` — `orders_slot_id_fkey` (legacy
 * `slot_id`) and `orders_delivery_slot_id_fkey` (the app-contract
 * `delivery_slot_id`). Without the hint PostgREST cannot choose and returns
 * `300 PGRST201 "Could not embed because more than one relationship was found"`,
 * which made this page `notFound()` for every order. We name the app-contract
 * relationship, matching what `place_order` writes.
 */
const ORDER_DETAIL_SELECT = `
  *,
  order_items ( * ),
  order_status_history ( * ),
  address:addresses ( * ),
  delivery_slot:delivery_slots!orders_delivery_slot_id_fkey ( * )
`;

/**
 * Order detail + tracking.
 *
 * RLS scopes the read to the customer's own order, so opening another customer's
 * id returns nothing and this page renders `notFound()` — the security decision
 * is the database's, not this file's.
 *
 * Two things get special care:
 *  • **Adjustments.** If the store rejected items, the customer sees a calm
 *    "your bill was revised" panel listing each rejected line with the store's
 *    own reason, plus the recalculated total. It is never styled as an error.
 *  • **Honesty about progress.** The tracker only renders stages the order has
 *    actually reached; there is no fake "your order is on its way!" for an order
 *    still sitting unaccepted.
 */
export default async function OrderDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const user = await requireUser("/orders");
  const [{ id }, query] = await Promise.all([params, searchParams]);
  const justPlaced = query.placed === "1";

  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("orders")
    .select(ORDER_DETAIL_SELECT)
    .eq("id", id)
    .maybeSingle();

  if (!data) notFound();

  const order = data as unknown as OrderDetail;
  const items = [...(order.order_items ?? [])].sort((a, b) =>
    a.created_at.localeCompare(b.created_at),
  );
  const history = [...(order.order_status_history ?? [])].sort((a, b) =>
    a.created_at.localeCompare(b.created_at),
  );

  const rejected = items.filter((item) => item.rejected);
  const hasAdjustment = order.adjustment_amount < 0 || rejected.length > 0;
  const meta = ORDER_STATUS_META[order.status];

  /**
   * Who cancelled this order, and why.
   *
   * `order_status_history.changed_by` is the only record of the actor: it is the
   * customer's own id when they cancelled from the site, and a staff id when the
   * store did it. Without this the customer just saw a grey "Cancelled" badge
   * with no explanation, which reads as if something went wrong on their side.
   */
  const cancellation = (() => {
    if (order.status !== "CANCELLED") return null;
    const event = history.find((entry) => entry.new_status === "CANCELLED");
    return {
      byCustomer: event ? event.changed_by === order.user_id : false,
      reason: order.cancel_reason ?? event?.reason ?? null,
      at: order.cancelled_at ?? event?.created_at ?? null,
    };
  })();

  return (
    <div className="shell flex flex-col gap-5 py-6 sm:py-8">
      <LiveOrderRefresh userId={user.id} />
      <div>
        <Link
          href="/orders"
          className="inline-flex items-center gap-1.5 text-small text-ink-muted transition-colors hover:text-ink"
        >
          <ArrowLeft className="size-3.5" aria-hidden="true" />
          All orders
        </Link>
      </div>

      {justPlaced ? (
        <div className="rounded-lg border-[1.5px] border-leaf-600/35 bg-leaf-50 p-4 sm:p-5">
          <h1 className="font-display text-h2 text-leaf-800">
            Thank you — your order is in
          </h1>
          <p className="mt-1.5 max-w-[58ch] text-body text-leaf-800/85">
            The shop has received it and will confirm shortly. You will be able to
            follow every step on this page.
          </p>
        </div>
      ) : null}

      {/* Status */}
      <section className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <OrderStatusBadge status={order.status} size="lg" />
              {hasAdjustment ? (
                <Badge variant="marigold">Bill revised</Badge>
              ) : null}
            </div>
            <p className="mt-2.5 max-w-[52ch] text-body text-ink-muted">
              {meta?.description}
            </p>
          </div>
          <div className="text-right">
            <p className="text-caption text-ink-subtle">Order placed</p>
            <p className="text-small text-ink">
              {formatDateTime(order.placed_at)}
            </p>
          </div>
        </div>

        {order.status !== "CANCELLED" ? (
          <Tracker status={order.status} history={history} />
        ) : cancellation ? (
          <div
            role="status"
            className="mt-4 rounded-md border border-line bg-surface-muted px-3.5 py-3"
          >
            <p className="text-small font-medium text-ink">
              {cancellation.byCustomer
                ? "You cancelled this order"
                : "This order was cancelled by the store"}
            </p>
            {cancellation.reason ? (
              <p className="mt-0.5 text-small text-ink-muted">
                Reason: {cancellation.reason}
              </p>
            ) : null}
            {cancellation.at ? (
              <p className="mt-0.5 text-caption text-ink-subtle">
                {formatDateTime(cancellation.at)}
              </p>
            ) : null}
            {!cancellation.byCustomer ? (
              <p className="mt-1.5 text-small text-ink-muted">
                If you were not expecting this,{" "}
                <Link href="/contact" className="underline underline-offset-2">
                  contact us
                </Link>{" "}
                and we will sort it out.
              </p>
            ) : null}
          </div>
        ) : null}
      </section>

      {/* Adjustment (never styled as an error) */}
      {hasAdjustment ? (
        <section className="rounded-lg border border-marigold-200 bg-marigold-50 p-4 sm:p-5">
          <h2 className="font-display text-h3 text-marigold-700">
            Your bill was revised
          </h2>
          <p className="mt-1 text-small text-marigold-700/90">
            Some items could not be supplied at the weight or quantity you asked
            for, so we adjusted your total down. You only pay for what you receive.
          </p>

          {rejected.length > 0 ? (
            <ul className="mt-3 flex flex-col gap-2">
              {rejected.map((item) => (
                <li
                  key={item.id}
                  className="flex items-start justify-between gap-3 rounded-md border border-marigold-200 bg-surface px-3 py-2.5 text-small"
                >
                  <span className="min-w-0">
                    <span className="block font-medium text-ink">
                      {item.product_name}
                      {item.weight_label ? ` · ${item.weight_label}` : ""}
                    </span>
                    <span className="mt-0.5 block text-ink-muted">
                      Reason given: {item.rejection_reason ?? "not supplied"}
                    </span>
                  </span>
                  <span
                    className="shrink-0 text-ink-muted line-through tabular-nums"
                    data-numeric
                  >
                    {formatMoney(item.line_total)}
                  </span>
                </li>
              ))}
            </ul>
          ) : null}

          <dl className="mt-3 flex flex-col gap-1.5 border-t border-marigold-200 pt-3 text-small">
            <div className="flex justify-between">
              <dt className="text-marigold-700/90">Original total</dt>
              <dd data-numeric className="tabular-nums text-ink">
                {formatMoney(order.original_total)}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-marigold-700/90">
                Adjustment
                {order.adjustment_reason ? ` (${order.adjustment_reason})` : ""}
              </dt>
              <dd data-numeric className="tabular-nums text-success">
                −{formatMoney(Math.abs(order.adjustment_amount))}
              </dd>
            </div>
            <div className="flex justify-between text-body font-semibold">
              <dt className="text-ink">You pay</dt>
              <dd data-numeric className="tabular-nums text-ink">
                {formatMoney(order.final_total)}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      {/* Items + totals */}
      <section className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
        <div className="rounded-lg border border-line bg-surface shadow-xs">
          <h2 className="border-b border-line px-4 py-3 font-display text-h3 text-ink">
            What you ordered
          </h2>
          <ul className="divide-y divide-line px-4">
            {items.map((item) => (
              <li key={item.id} className="flex items-start justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="text-body text-ink">
                    {item.product_name}
                    {item.rejected ? (
                      <span className="ml-2 align-middle">
                        <Badge variant="marigold" size="sm">
                          Not supplied
                        </Badge>
                      </span>
                    ) : null}
                  </p>
                  <p className="mt-0.5 text-small text-ink-subtle">
                    {item.weight_label ? `${item.weight_label} · ` : ""}
                    {item.qty} × {formatMoney(item.unit_price)}
                  </p>
                </div>
                <p
                  data-numeric
                  className={cn(
                    "shrink-0 text-body tabular-nums",
                    item.rejected ? "text-ink-subtle line-through" : "text-ink",
                  )}
                >
                  {formatMoney(item.line_total)}
                </p>
              </li>
            ))}
          </ul>
          <div className="flex flex-wrap items-center gap-2 px-4 pb-4">
            <ReorderButton items={items} redirectToCart={false} className="mt-1" />
            <div className="mt-1">
              <CancelOrderPanel orderId={order.id} status={order.status} />
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-5">
          <dl className="rounded-lg border border-line bg-surface p-4 text-body shadow-xs">
            <h2 className="font-display text-h3 text-ink">Bill</h2>
            <div className="mt-3 flex flex-col gap-2 text-small">
              <Row label="Items">
                {formatMoney(order.original_total - order.delivery_charge)}
              </Row>
              <Row label="Delivery">
                {order.delivery_charge === 0
                  ? "Free"
                  : formatMoney(order.delivery_charge)}
              </Row>
              {order.adjustment_amount < 0 ? (
                <Row label="Adjustment">
                  <span className="text-success">
                    −{formatMoney(Math.abs(order.adjustment_amount))}
                  </span>
                </Row>
              ) : null}
              <Separator className="my-1" />
              <div className="flex items-baseline justify-between">
                <dt className="font-semibold text-ink">Total</dt>
                <dd data-numeric className="text-price font-semibold tabular-nums text-ink">
                  {formatMoney(order.final_total)}
                </dd>
              </div>
            </div>
          </dl>

          {order.address ? (
            <div className="rounded-lg border border-line bg-surface p-4 shadow-xs">
              <h2 className="font-display text-h3 text-ink">Delivering to</h2>
              <p className="mt-2 text-small font-medium text-ink">
                {order.address.label}
              </p>
              <p className="text-small text-ink-muted">
                {order.address.full_name} · {order.address.phone}
              </p>
              <p className="mt-1 text-small text-ink-muted">
                {order.address.house_number} {order.address.street}
                {order.address.landmark ? `, ${order.address.landmark}` : ""}
              </p>
              <p className="text-small text-ink-muted">
                {order.address.city}, {order.address.state}{" "}
                {order.address.postal_code}
              </p>
            </div>
          ) : null}

          {order.delivery_slot ? (
            <div className="rounded-lg border border-line bg-surface p-4 shadow-xs">
              <h2 className="font-display text-h3 text-ink">Delivery slot</h2>
              <p className="mt-2 text-small text-ink">
                {formatSlotWindow(
                  order.delivery_slot.starts_at,
                  order.delivery_slot.ends_at,
                )}
              </p>
            </div>
          ) : null}

          <div className="rounded-lg border border-line bg-surface p-4 shadow-xs">
            <h2 className="font-display text-h3 text-ink">
              Something not right?
            </h2>
            <p className="mt-1.5 text-small text-ink-muted">
              Tell us about a missing or poor-quality item and we will make it
              right.
            </p>
            <Button asChild variant="outline" size="sm" className="mt-3">
              <Link href={`/support?order=${order.id}`}>
                <HeadphonesIcon className="size-3.5" aria-hidden="true" />
                Raise a problem
              </Link>
            </Button>
          </div>
        </div>
      </section>

      {/* History */}
      <section className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <h2 className="flex items-center gap-2 font-display text-h3 text-ink">
          <Receipt className="size-4 text-ink-subtle" aria-hidden="true" />
          Order history
        </h2>
        <ol className="mt-3 flex flex-col gap-3">
          {history.map((entry) => (
            <li key={entry.id} className="flex gap-3">
              <span
                aria-hidden="true"
                className="mt-1.5 size-2 shrink-0 rounded-full bg-leaf-400"
              />
              <div className="min-w-0">
                <p className="text-body text-ink">
                  {ORDER_STATUS_META[entry.new_status]?.label ?? entry.new_status}
                  {entry.reason ? (
                    <span className="text-ink-muted"> — {entry.reason}</span>
                  ) : null}
                </p>
                <p className="text-small text-ink-subtle">
                  {formatDateTime(entry.created_at)}
                </p>
              </div>
            </li>
          ))}
          {history.length === 0 ? (
            <li className="text-small text-ink-subtle">
              No status updates recorded yet.
            </li>
          ) : null}
        </ol>
      </section>

      {order.status === "PENDING" ? (
        <p className="flex items-start gap-2 rounded-md border border-info-bg bg-info-bg px-3.5 py-3 text-small text-info">
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            We are waiting for the shop to confirm this order. If you need it
            urgently,{" "}
            <Link href="/contact" className="underline underline-offset-2">
              get in touch
            </Link>
            .
          </span>
        </p>
      ) : null}
    </div>
  );
}

/**
 * Progress tracker.
 *
 * Only completed and the single next stage are marked; everything after that is
 * neutral. Cancelled orders never render a tracker at all.
 */
function Tracker({
  status,
  history,
}: {
  status: OrderStatus;
  history: OrderStatusHistoryRow[];
}) {
  const reachedAt = new Map<OrderStatus, string>();
  for (const entry of history) {
    reachedAt.set(entry.new_status, entry.created_at);
  }
  reachedAt.set(status, reachedAt.get(status) ?? "");

  const currentIndex = ORDER_STATUS_FLOW.indexOf(status);

  return (
    <ol className="mt-5 grid gap-3 sm:grid-cols-3 lg:grid-cols-6">
      {ORDER_STATUS_FLOW.map((stage, index) => {
        const done = index <= currentIndex;
        const isCurrent = index === currentIndex;
        const at = reachedAt.get(stage);

        return (
          <li key={stage} className="flex items-start gap-2.5 sm:flex-col sm:gap-2">
            <span
              aria-hidden="true"
              className={cn(
                "mt-0.5 grid size-6 shrink-0 place-items-center rounded-full text-[10px] font-semibold",
                done ? "bg-leaf-600 text-white" : "bg-surface-muted text-ink-subtle",
                isCurrent && "ring-2 ring-leaf-600/25",
              )}
            >
              {done ? "✓" : index + 1}
            </span>
            <span className="min-w-0">
              <span
                className={cn(
                  "block text-small",
                  isCurrent ? "font-semibold text-ink" : done ? "text-ink" : "text-ink-subtle",
                )}
              >
                {ORDER_STATUS_META[stage].label}
              </span>
              {at ? (
                <span className="block text-[11px] text-ink-subtle">
                  {formatDateTime(at)}
                </span>
              ) : null}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function Row({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt className="text-ink-muted">{label}</dt>
      <dd data-numeric className="shrink-0 tabular-nums text-ink">
        {children}
      </dd>
    </div>
  );
}
