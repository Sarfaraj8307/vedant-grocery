import type { Metadata } from "next";

import { CheckoutClient } from "@/components/checkout/checkout-client";
import { requireUser } from "@/lib/auth";
import { getDeliverySlots, getSlotLoad } from "@/lib/catalogue";
import { listAddressesForUser } from "@/lib/addresses";
import { getStoreStatus } from "@/lib/store-status";

export const metadata: Metadata = {
  title: "Checkout",
  description: "Confirm your delivery address and slot, then place your order.",
  // A checkout page must never be indexed or cached.
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Checkout — a Server Component gate around a client flow.
 *
 * The address book and the slot list are fetched on the server with the
 * customer's own session, so RLS guarantees they can only ever see their own
 * addresses. The client then owns only the *selection* and the submission.
 */
export default async function CheckoutPage() {
  await requireUser("/checkout");

  const [addresses, slots, slotLoad, store] = await Promise.all([
    listAddressesForUser(),
    getDeliverySlots().catch(() => []),
    getSlotLoad().catch(() => ({})),
    getStoreStatus(),
  ]);

  return (
    <div className="shell py-6 sm:py-8">
      <header className="mb-5">
        <h1 className="font-display text-h1 text-ink">Checkout</h1>
        <p className="mt-1 max-w-[58ch] text-body text-ink-muted">
          Three quick things: where, when, and a final look at your basket. We
          confirm the exact amount when the order is placed.
        </p>
      </header>

      <CheckoutClient
        initialAddresses={addresses}
        slots={slots}
        slotLoad={slotLoad}
        acceptingOrders={store.acceptingOrders}
        closedReason={store.closedReason}
      />
    </div>
  );
}
