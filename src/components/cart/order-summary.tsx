"use client";

import { Check, Truck } from "lucide-react";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import type { TotalsPreview } from "@/lib/pricing";

/**
 * Order summary.
 *
 * PRESENTATION RULE (brief §16 — "do not hide charges"): every amount
 * `place_order` will write is visible *before* the customer commits — subtotal,
 * the discount already applied, the delivery charge and the payable total.
 *
 * The delivery line is the one place a small amount of extra UI earns its keep:
 * below the threshold it shows exactly how much more is needed to earn free
 * delivery, which is genuinely useful rather than a dark pattern (the customer
 * can see the rule and the arithmetic).
 */
export function OrderSummary({
  totals,
  itemCount,
  title = "Order summary",
  children,
  className,
  showProgress = true,
}: {
  totals: TotalsPreview;
  itemCount: number;
  title?: string;
  children?: React.ReactNode;
  className?: string;
  showProgress?: boolean;
}) {
  const qualifiesForFreeDelivery = totals.deliveryCharge === 0;
  const progressPercent = Math.min(
    100,
    Math.round(
      ((totals.subtotal) / Math.max(1, totals.freeDeliveryThreshold)) * 100,
    ),
  );

  return (
    <section
      aria-labelledby="order-summary-heading"
      className={cn(
        "rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5",
        className,
      )}
    >
      <h2 id="order-summary-heading" className="font-display text-h3 text-ink">
        {title}
      </h2>

      <dl className="mt-4 flex flex-col gap-2.5 text-body">
        <Row label={`Subtotal (${itemCount} ${itemCount === 1 ? "item" : "items"})`}>
          {formatMoney(totals.subtotal)}
        </Row>

        {totals.savings > 0 ? (
          <Row label="Offer savings" tone="saving">
            −{formatMoney(totals.savings)}
          </Row>
        ) : null}

        <Row label="Delivery">
          {qualifiesForFreeDelivery ? (
            <span className="text-success">Free</span>
          ) : (
            formatMoney(totals.deliveryCharge)
          )}
        </Row>

        <div className="mt-1 border-t border-line pt-3">
          <Row label="Total payable" emphasis>
            {formatMoney(totals.total)}
          </Row>
        </div>
      </dl>

      {showProgress ? (
        <div className="mt-4 rounded-md bg-surface-muted px-3 py-2.5">
          {qualifiesForFreeDelivery ? (
            <p className="flex items-center gap-2 text-small text-success">
              <Check className="size-4 shrink-0" aria-hidden="true" />
              You have earned free delivery on this order.
            </p>
          ) : (
            <>
              <p className="flex items-center gap-2 text-small text-ink-muted">
                <Truck className="size-4 shrink-0 text-leaf-600" aria-hidden="true" />
                Add {formatMoney(totals.amountToFreeDelivery)} more for free
                delivery
              </p>
              <div
                className="mt-2 h-1.5 overflow-hidden rounded-pill bg-surface-sunken"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progressPercent}
                aria-label="Progress towards free delivery"
              >
                <span
                  className="block h-full rounded-pill bg-leaf-600 transition-[width] duration-[var(--motion-slow)]"
                  style={{ width: `${Math.max(4, progressPercent)}%` }}
                />
              </div>
            </>
          )}
        </div>
      ) : null}

      {children ? <div className="mt-5">{children}</div> : null}

      <p className="mt-4 text-small text-ink-subtle">
        Final amount is confirmed by our system when the order is placed — if the
        market rate changed on an item, we will tell you before dispatch.
      </p>
    </section>
  );
}

function Row({
  label,
  children,
  emphasis = false,
  tone,
}: {
  label: string;
  children: React.ReactNode;
  emphasis?: boolean;
  tone?: "saving";
}) {
  return (
    <div className="flex items-baseline justify-between gap-4">
      <dt
        className={cn(
          "min-w-0",
          emphasis ? "text-body font-semibold text-ink" : "text-ink-muted",
        )}
      >
        {label}
      </dt>
      <dd
        data-numeric
        className={cn(
          "shrink-0 tabular-nums",
          emphasis
            ? "text-price font-semibold text-ink"
            : tone === "saving"
              ? "text-success"
              : "text-ink",
        )}
      >
        {children}
      </dd>
    </div>
  );
}
