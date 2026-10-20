import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, ReceiptText } from "lucide-react";

import { OrderStatusBadge } from "@/components/orders/order-status-badge";
import { ReorderButton } from "@/components/orders/reorder-button";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { requireUser } from "@/lib/auth";
import { LiveOrderRefresh } from "@/components/orders/live-order-refresh";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { formatDateTime, pluralise } from "@/lib/format";
import { formatMoney } from "@/lib/money";
import type { OrderItemRow, OrderRow } from "@/types/database";

/** An order plus just enough of its lines to rebuild the basket. */
type OrderSummaryRow = OrderRow & {
  order_items: Pick<
    OrderItemRow,
    "product_id" | "product_name" | "weight_label" | "qty" | "rejected"
  >[];
};

/** Only the fields "order again" needs — keeps the list payload small. */
const ORDER_LIST_SELECT = `
  *,
  order_items ( product_id, product_name, weight_label, qty, rejected )
`;

export const metadata: Metadata = {
  title: "Your orders",
  description: "Track current orders and see everything you have ordered before.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Order history.
 *
 * RLS (`orders_read_self_or_admin`) scopes this to the signed-in customer's own
 * orders. There is no `user_id` filter in the query on purpose: adding one would
 * imply the filter is the protection, when it is the policy that protects.
 */
export default async function OrdersPage() {
  const user = await requireUser("/orders");

  const supabase = await getSupabaseServerClient();
  const { data } = await supabase
    .from("orders")
    .select(ORDER_LIST_SELECT)
    .order("placed_at", { ascending: false })
    .limit(50);

  const orders = (data ?? []) as unknown as OrderSummaryRow[];
  const active = orders.filter(
    (order) => !["DELIVERED", "CANCELLED"].includes(order.status),
  );
  const past = orders.filter((order) =>
    ["DELIVERED", "CANCELLED"].includes(order.status),
  );

  if (orders.length === 0) {
    return (
      <div className="shell py-6 sm:py-8">
        <h1 className="font-display text-h1 text-ink">Your orders</h1>
        <EmptyState
          className="mt-5 rounded-lg border border-line bg-surface shadow-xs"
          illustration={
            <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
              <ReceiptText className="size-7 text-leaf-500" aria-hidden="true" />
            </span>
          }
          title="No orders yet"
          description="When you place your first order it will appear here, with its status and every item you bought."
          action={
            <Button asChild>
              <Link href="/categories">
                Start shopping
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </Button>
          }
        />
      </div>
    );
  }

  return (
    <div className="shell flex flex-col gap-7 py-6 sm:py-8">
      <LiveOrderRefresh userId={user.id} />
      <header>
        <h1 className="font-display text-h1 text-ink">Your orders</h1>
        <p className="mt-1 text-small text-ink-muted">
          {pluralise(orders.length, "order")} placed with us
        </p>
      </header>

      {active.length > 0 ? (
        <section aria-labelledby="active-orders">
          <h2 id="active-orders" className="font-display text-h3 text-ink">
            In progress
          </h2>
          <ul className="mt-3 flex flex-col gap-3">
            {active.map((order) => (
              <OrderCard key={order.id} order={order} highlight />
            ))}
          </ul>
        </section>
      ) : null}

      {past.length > 0 ? (
        <section aria-labelledby="past-orders">
          <h2 id="past-orders" className="font-display text-h3 text-ink">
            Past orders
          </h2>
          <ul className="mt-3 flex flex-col gap-3">
            {past.map((order) => (
              <OrderCard key={order.id} order={order} />
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}

function OrderCard({
  order,
  highlight = false,
}: {
  order: OrderSummaryRow;
  highlight?: boolean;
}) {
  const adjusted = order.adjustment_amount < 0;
  const items = order.order_items ?? [];

  return (
    <li
      className={
        highlight
          ? "rounded-lg border-[1.5px] border-leaf-600/35 bg-surface p-4 shadow-xs"
          : "rounded-lg border border-line bg-surface p-4 shadow-xs"
      }
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <OrderStatusBadge status={order.status} />
            {adjusted ? (
              <span className="text-small text-marigold-600">
                Bill revised
              </span>
            ) : null}
          </div>
          <p className="mt-2 text-body text-ink">
            {formatDateTime(order.placed_at)}
          </p>
          <p className="mt-0.5 text-small text-ink-subtle" data-numeric>
            {formatMoney(order.final_total)}
            {order.delivery_charge > 0
              ? ` · includes ${formatMoney(order.delivery_charge)} delivery`
              : " · free delivery"}
          </p>
        </div>

        <div className="flex shrink-0 flex-wrap gap-2">
          <Button asChild variant="secondary" size="sm">
            <Link href={`/orders/${order.id}`}>
              View details
              <ArrowRight className="size-3.5" aria-hidden="true" />
            </Link>
          </Button>
          <ReorderButton items={items} />
        </div>
      </div>
    </li>
  );
}
