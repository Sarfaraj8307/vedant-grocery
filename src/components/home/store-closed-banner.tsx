import { Clock } from "lucide-react";

import { getStoreStatus, ORDERS_CLOSED_MESSAGE } from "@/lib/store-status";

/**
 * The "we're not accepting orders right now" notice (§9 / §36).
 *
 * Renders **nothing** when the store is accepting orders, so a normal home page
 * is byte-identical to before. When closed it states the fact, gives the
 * operator's reason if there is one, and — importantly — says browsing still
 * works. Closing orders must not read as "the shop is gone".
 *
 * This is a server component: the status is read once per request through
 * `getStoreStatus()` (memoised with React `cache`), never in the browser. The
 * copy is deliberately calm — this is a normal operational state, not an error.
 */
export async function StoreClosedBanner() {
  const store = await getStoreStatus();
  if (store.acceptingOrders) return null;

  return (
    <div
      role="status"
      aria-live="polite"
      className="border-b border-warning/25 bg-warning-bg"
    >
      <div className="shell flex items-start gap-3 py-3">
        <Clock
          aria-hidden="true"
          className="mt-0.5 size-4 shrink-0 text-warning"
          strokeWidth={2}
        />
        <div className="min-w-0">
          <p className="text-sm font-medium text-ink">{ORDERS_CLOSED_MESSAGE}</p>
          {store.closedReason ? (
            <p className="mt-0.5 text-sm text-ink-muted">{store.closedReason}</p>
          ) : null}
        </div>
      </div>
    </div>
  );
}
