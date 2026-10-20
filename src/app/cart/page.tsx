"use client";

import Link from "next/link";
import { AlertCircle, ArrowRight, Trash2 } from "lucide-react";

import { CartLineRow } from "@/components/cart/cart-line-row";
import { OrderSummary } from "@/components/cart/order-summary";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { Skeleton } from "@/components/ui/skeleton";
import { useCart } from "@/hooks/use-cart";

/**
 * Basket page.
 *
 * A Client Component because the basket is local-first state (see
 * `stores/cart-store.ts`). Totals come from `previewTotals`, which uses the same
 * integer-paise arithmetic and the same delivery rules as the database.
 *
 * Two-column from `lg`: lines on the left, a sticky summary on the right so the
 * total and the checkout button never scroll out of reach.
 */
export default function CartPage() {
  const {
    lines,
    count,
    isEmpty,
    hydrating,
    syncError,
    totals,
    clear,
    dismissSyncError,
  } = useCart();

  return (
    <div className="shell py-6 sm:py-8">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-h1 text-ink">Your basket</h1>
          <p className="mt-1 text-small text-ink-muted">
            {isEmpty
              ? "Nothing here yet."
              : `${count} ${count === 1 ? "item" : "items"} · we will weigh everything fresh before dispatch`}
          </p>
        </div>
        {!isEmpty ? (
          <Button
            variant="quiet"
            size="sm"
            onClick={clear}
            className="shrink-0 text-danger hover:bg-danger-bg"
          >
            <Trash2 className="size-4" aria-hidden="true" />
            Clear
          </Button>
        ) : null}
      </div>

      {syncError ? (
        <div
          role="alert"
          className="mt-4 flex items-start gap-2.5 rounded-md border border-danger-bg bg-danger-bg px-3.5 py-3 text-small text-danger"
        >
          <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <p className="flex-1">
            {syncError} Your basket is saved on this device, but we could not
            save it to your account.
          </p>
          <button
            type="button"
            onClick={dismissSyncError}
            className="shrink-0 rounded-xs font-medium underline underline-offset-2"
          >
            Dismiss
          </button>
        </div>
      ) : null}

      {hydrating && isEmpty ? (
        <CartSkeleton />
      ) : isEmpty ? (
        <EmptyState
          className="mt-6 rounded-lg border border-line bg-surface shadow-xs"
          illustration={<BasketIllustration />}
          title="Your basket is empty"
          description="Add a few things and we will weigh, pack and deliver them. Free delivery on orders over ₹500."
          action={
            <div className="flex flex-wrap justify-center gap-2">
              <Button asChild>
                <Link href="/categories">
                  Browse today&apos;s picks
                  <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link href="/offers">See offers</Link>
              </Button>
            </div>
          }
        />
      ) : (
        <div className="mt-5 grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-8">
          <ul className="divide-y divide-line rounded-lg border border-line bg-surface px-4 shadow-xs">
            {lines.map((line) => (
              <CartLineRow
                key={`${line.productId}::${line.weightId ?? "base"}`}
                line={line}
              />
            ))}
          </ul>

          <div className="lg:sticky lg:top-[calc(var(--header-h)+16px)]">
            <OrderSummary totals={totals} itemCount={count}>
              <Button asChild size="lg" block>
                <Link href="/checkout">
                  Proceed to checkout
                  <ArrowRight className="size-4" aria-hidden="true" />
                </Link>
              </Button>
              <Button asChild variant="quiet" size="sm" block className="mt-2">
                <Link href="/categories">Continue shopping</Link>
              </Button>
            </OrderSummary>
          </div>
        </div>
      )}
    </div>
  );
}

function CartSkeleton() {
  return (
    <div className="mt-5 grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
      <div className="flex flex-col gap-4 rounded-lg border border-line bg-surface p-4">
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={index} className="flex gap-3 py-2">
            <Skeleton className="size-16 shrink-0 rounded-md" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-3.5 w-2/3 rounded-full" />
              <Skeleton className="h-3 w-1/3 rounded-full" />
              <Skeleton className="h-7 w-28 rounded-pill" />
            </div>
          </div>
        ))}
      </div>
      <Skeleton className="h-56 rounded-lg" />
    </div>
  );
}

/** Drawn, brand-palette empty-state illustration (never a stock blob). */
function BasketIllustration() {
  return (
    <svg viewBox="0 0 120 96" className="h-24 w-28" aria-hidden="true">
      <ellipse cx="60" cy="86" rx="34" ry="5" fill="#EDE7DB" />
      <path d="M26 44h68l-7 38H33z" fill="#DFEEE2" />
      <path
        d="M26 44h68l-7 38H33z"
        fill="none"
        stroke="#2A6B46"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M41 44V32a19 19 0 0 1 38 0v12"
        fill="none"
        stroke="#2A6B46"
        strokeWidth="3"
        strokeLinecap="round"
      />
      <circle cx="50" cy="62" r="7" fill="#EFA632" />
      <circle cx="70" cy="66" r="6" fill="#A93A63" />
      <circle cx="62" cy="56" r="5" fill="#5FA277" />
    </svg>
  );
}
