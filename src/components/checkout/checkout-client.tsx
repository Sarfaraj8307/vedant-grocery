"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AlertCircle, ArrowRight, Check, ShieldCheck } from "lucide-react";

import { AddressBook } from "@/components/checkout/address-book";
import { SlotPicker } from "@/components/checkout/slot-picker";
import { OrderSummary } from "@/components/cart/order-summary";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { useCart } from "@/hooks/use-cart";
import { useCartSession } from "@/components/cart/cart-provider";
import { newRequestId } from "@/services/order-service";
import {
  submitOrder,
  verifyAddressServiceability,
} from "@/app/checkout/actions";
import type { ServiceabilityVerdict } from "@/lib/serviceability";
import { formatSlotWindow } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { AddressRow, DeliverySlotRow } from "@/types/database";

/**
 * Checkout.
 *
 * Three decisions worth stating:
 *
 * 1. **One page, not a wizard.** Address, slot and summary are all visible at
 *    once. A grocery basket is small and revisiting a step to change one field
 *    is the most common checkout frustration. On mobile they stack in the order
 *    the customer thinks; on desktop the summary is sticky alongside.
 *
 * 2. **The server is the only authority.** This component sends `request_id`,
 *    `address_id` and `delivery_slot_id` and nothing else. Prices, availability,
 *    delivery charge and the final total are all computed by `place_order`
 *    inside a transaction — the numbers shown here are labelled as an estimate
 *    and the confirmation screen shows what the database actually wrote.
 *
 * 3. **Retry is idempotent.** `request_id` is generated once per checkout session
 *    and reused for every attempt, so a lost response cannot produce a duplicate
 *    order. It is only replaced after a successful order.
 */
export function CheckoutClient({
  initialAddresses,
  slots,
  slotLoad,
  acceptingOrders = true,
  closedReason = null,
}: {
  initialAddresses: AddressRow[];
  slots: DeliverySlotRow[];
  slotLoad: Record<string, number>;
  /**
   * Whether the store is taking new orders (§9/§36). Read on the server and
   * passed down so the customer is told before they fill anything in — but this
   * is a **UX gate only**: `submitOrder` re-checks and `place_order` enforces
   * `STORE_CLOSED` itself. Never trust this prop as the authority.
   */
  acceptingOrders?: boolean;
  closedReason?: string | null;
}) {
  const router = useRouter();
  const { count, isEmpty, hydrating, totals } = useCart();
  const { clear } = useCartSession();

  const [addresses, setAddresses] = React.useState<AddressRow[]>(initialAddresses);
  const [selectedAddressId, setSelectedAddressId] = React.useState<string | null>(
    initialAddresses.find((a) => a.is_default)?.id ?? initialAddresses[0]?.id ?? null,
  );
  const [selectedSlotId, setSelectedSlotId] = React.useState<string | null>(null);
  const [placing, setPlacing] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  // Stable for the whole checkout session — see note 3 above.
  const requestIdRef = React.useRef<string>(newRequestId());

  // Serviceability is decided on the SERVER: the action re-reads the address
  // from the database, so a tampered client cannot claim a Pune pincode for an
  // address we cannot reach. The verdict is stored WITH the address it belongs
  // to, so a stale result from a previous selection can never be shown, and
  // "still checking" is derived rather than stored (no setState in the effect
  // body — the result only lands in the async callback).
  const [check, setCheck] = React.useState<{
    addressId: string;
    verdict: ServiceabilityVerdict;
  } | null>(null);

  React.useEffect(() => {
    if (!selectedAddressId) return;
    let cancelled = false;

    void verifyAddressServiceability(selectedAddressId)
      .then((result) => {
        if (!cancelled) setCheck({ addressId: selectedAddressId, verdict: result });
      })
      .catch(() => {
        if (!cancelled) {
          setCheck({
            addressId: selectedAddressId,
            verdict: {
              serviceable: false,
              code: "OUT_OF_AREA",
              reason: "We could not check delivery just now. Please try again.",
            },
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedAddressId]);

  const verdict =
    check && check.addressId === selectedAddressId ? check.verdict : null;
  const checkingService = Boolean(selectedAddressId) && verdict === null;

  const selectedAddress = addresses.find((a) => a.id === selectedAddressId) ?? null;
  const selectedSlot = slots.find((s) => s.id === selectedSlotId) ?? null;

  const readyToPlace =
    acceptingOrders &&
    Boolean(selectedAddressId) &&
    Boolean(selectedSlotId) &&
    !isEmpty &&
    !hydrating &&
    !checkingService &&
    verdict?.serviceable === true;

  async function handlePlaceOrder() {
    setError(null);

    if (!selectedAddressId) {
      setError("Choose a delivery address to continue.");
      return;
    }
    if (!selectedSlotId) {
      setError("Choose a delivery slot to continue.");
      return;
    }

    setPlacing(true);
    try {
      const result = await submitOrder({
        requestId: requestIdRef.current,
        addressId: selectedAddressId,
        deliverySlotId: selectedSlotId,
      });

      if (!result.ok) {
        setError(result.error);
        setPlacing(false);
        return;
      }

      // place_order already deleted the server rows; this clears the local copy.
      clear();
      // A fresh id for the next order this session.
      requestIdRef.current = newRequestId();
      // `/orders/{id}` — PLURAL. This was `/order/{id}`, which has no route, so
      // every successful order landed on the 404 page ("we couldn't find that
      // page") even though `place_order` had already committed the order. The
      // customer saw a failure and the order existed — the worst combination.
      router.push(`/orders/${result.orderId}?placed=1`);
    } catch {
      setError("We could not place your order. Please try again.");
      setPlacing(false);
    }
  }

  if (isEmpty && !hydrating) {
    return (
      <EmptyState
        className="rounded-lg border border-line bg-surface shadow-xs"
        illustration={
          <svg viewBox="0 0 96 96" className="h-24 w-24" aria-hidden="true">
            <circle cx="48" cy="48" r="34" fill="#DFEEE2" />
            <path
              d="M34 40h28l-4 26H38z"
              fill="none"
              stroke="#2A6B46"
              strokeWidth="3.5"
              strokeLinejoin="round"
            />
            <path
              d="M41 40v-6a7 7 0 0 1 14 0v6"
              fill="none"
              stroke="#2A6B46"
              strokeWidth="3.5"
              strokeLinecap="round"
            />
          </svg>
        }
        title="Your basket is empty"
        description="Add a few items before checking out — we will weigh everything fresh before it leaves the shop."
        action={
          <Button asChild>
            <Link href="/categories">
              Browse produce
              <ArrowRight className="size-4" aria-hidden="true" />
            </Link>
          </Button>
        }
      />
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start lg:gap-8">
      <div className="flex flex-col gap-5">
        {/* 1 — Address */}
        <Step
          step={1}
          title="Where should we deliver?"
          done={Boolean(selectedAddress) && verdict?.serviceable === true}
        >
          <AddressBook
            addresses={addresses}
            selectedId={selectedAddressId}
            onSelect={(address) => setSelectedAddressId(address.id)}
            onAddressesChange={(next) => {
              setAddresses(next);
              // Keep a valid selection if the selected address was removed.
              if (!next.some((a) => a.id === selectedAddressId)) {
                setSelectedAddressId(
                  next.find((a) => a.is_default)?.id ?? next[0]?.id ?? null,
                );
              }
            }}
          />

          {/* Store not accepting orders (§9/§36). Shown above the address book
              so the customer learns it before making any choices. The button is
              disabled below, and `place_order` would refuse anyway. */}
          {!acceptingOrders ? (
            <p
              role="status"
              className="mt-3 flex items-start gap-2 rounded-md border border-marigold-200 bg-marigold-50 px-3 py-2.5 text-small text-marigold-700"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              <span>
                We&apos;re currently not accepting new orders. You can still
                browse the store and your basket is saved.
                {closedReason ? ` ${closedReason}` : ""}
              </span>
            </p>
          ) : null}

          {/* Server-verified serviceability. Shown before the customer fills a
              basket's worth of decisions, not at the final tap. */}
          {selectedAddress ? (
            <p
              role="status"
              className={cn(
                "mt-3 flex items-start gap-2 rounded-md border px-3 py-2.5 text-small",
                verdict?.serviceable
                  ? "border-leaf-600/30 bg-leaf-50 text-leaf-700"
                  : "border-marigold-200 bg-marigold-50 text-marigold-700",
              )}
            >
              {checkingService ? (
                "Checking that we deliver to this address…"
              ) : verdict?.serviceable ? (
                <>
                  <Check className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                  We deliver to {verdict.areaLabel}.
                </>
              ) : (
                <>
                  <AlertCircle
                    className="mt-0.5 size-4 shrink-0"
                    aria-hidden="true"
                  />
                  {verdict?.reason ?? "We could not check this address."}
                </>
              )}
            </p>
          ) : null}
        </Step>

        {/* 2 — Slot */}
        <Step step={2} title="When should we bring it?" done={Boolean(selectedSlot)}>
          <SlotPicker
            slots={slots}
            load={slotLoad}
            selectedId={selectedSlotId}
            onSelect={(slot) => setSelectedSlotId(slot.id)}
          />
        </Step>

        {/* 3 — Review */}
        <Step step={3} title="Check everything over" done={readyToPlace}>
          <p className="text-small text-ink-muted">
            {count} {count === 1 ? "item" : "items"} in your basket. Weights and
            prices are for the exact packs you chose.
          </p>
        </Step>
      </div>

      {/* Summary */}
      <div className="lg:sticky lg:top-[calc(var(--header-h)+16px)]">
        <OrderSummary totals={totals} itemCount={count} title="Estimated total">
          {selectedAddress ? (
            <div className="mb-4 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-small">
              <p className="text-caption text-ink-subtle">Delivering to</p>
              <p className="mt-1 font-medium text-ink">{selectedAddress.label}</p>
              <p className="text-ink-muted">
                {selectedAddress.house_number} {selectedAddress.street},{" "}
                {selectedAddress.city} {selectedAddress.postal_code}
              </p>
            </div>
          ) : null}

          {selectedSlot ? (
            <div className="mb-4 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-small">
              <p className="text-caption text-ink-subtle">Delivery slot</p>
              <p className="mt-1 text-ink">
                {formatSlotWindow(selectedSlot.starts_at, selectedSlot.ends_at)}
              </p>
            </div>
          ) : null}

          {error ? (
            <p
              role="alert"
              className="mb-3 flex items-start gap-2 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
            >
              <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              {error}
            </p>
          ) : null}

          <Button
            size="lg"
            block
            disabled={!readyToPlace}
            loading={placing}
            loadingLabel="Placing your order…"
            onClick={() => void handlePlaceOrder()}
          >
            {placing ? "Placing order…" : "Place order"}
          </Button>

          <p className="mt-3 flex items-start gap-1.5 text-small text-ink-subtle">
            <ShieldCheck className="mt-0.5 size-3.5 shrink-0 text-leaf-600" aria-hidden="true" />
            No payment is collected here. You pay when the order is delivered or
            as arranged with the shop.
          </p>
        </OrderSummary>
      </div>
    </div>
  );
}

function Step({
  step,
  title,
  done,
  children,
}: {
  step: number;
  title: string;
  done: boolean;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
      <div className="flex items-center gap-3">
        <span
          className={cn(
            "grid size-7 shrink-0 place-items-center rounded-full text-small font-semibold",
            done ? "bg-leaf-600 text-white" : "bg-surface-muted text-ink-muted",
          )}
          aria-hidden="true"
        >
          {done ? <Check className="size-4" strokeWidth={3} /> : step}
        </span>
        <h2 className="font-display text-h3 text-ink">{title}</h2>
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}
