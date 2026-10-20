"use client";

import * as React from "react";
import { useCartStore, type CartLine } from "@/stores/cart-store";
import { useCartSession } from "@/components/cart/cart-provider";
import { previewTotals, type TotalsPreview } from "@/lib/pricing";

/**
 * The cart hook every component uses.
 *
 * Combines the persisted local store (state) with the session bridge (side
 * effects) so a component never has to know whether the customer is signed in.
 *
 * `useCartStore` is subscribed with the raw store and values are projected with
 * `useMemo`, rather than using an object-returning selector — an object selector
 * would create a new reference every render and loop under Zustand v5.
 */
export type UseCart = {
  lines: CartLine[];
  count: number;
  isEmpty: boolean;
  hydrating: boolean;
  syncError: string | null;
  totals: TotalsPreview;
  quantityOf: (productId: string, weightId: string | null) => number;
  add: ReturnType<typeof useCartSession>["add"];
  setQuantity: ReturnType<typeof useCartSession>["setQuantity"];
  increment: ReturnType<typeof useCartSession>["increment"];
  decrement: ReturnType<typeof useCartSession>["decrement"];
  remove: ReturnType<typeof useCartSession>["remove"];
  clear: ReturnType<typeof useCartSession>["clear"];
  dismissSyncError: () => void;
};

/**
 * Stable empty cart lines reference, returned by `useCart` until the component
 * has mounted on the client. Using a single frozen reference avoids re-render
 * loops and keeps the first render identical to the server's.
 */
const EMPTY_LINES: Record<string, CartLine> = Object.freeze({});

/**
 * Returns `false` during SSR and the client's very first render, then `true`
 * once the persisted (localStorage) cart has finished rehydrating on the
 * client. Anything rendered while it is `false` is guaranteed to match the
 * server's HTML, so the persisted cart — which only exists on the client — is
 * hidden until after hydration and reappears as a post-hydration re-render
 * rather than a React hydration mismatch.
 *
 * Implemented with `useSyncExternalStore` against the persist middleware's own
 * hydration signal (`hasHydrated` / `onFinishHydration`) rather than a
 * `useState` flipped in an effect, so it stays lint-clean and reads the
 * authoritative "rehydration complete" state.
 */
export function useCartHydrated(): boolean {
  return React.useSyncExternalStore(
    (onStoreChange) => useCartStore.persist.onFinishHydration(onStoreChange),
    () => useCartStore.persist.hasHydrated(),
    () => false,
  );
}

export function useCart(): UseCart {
  const session = useCartSession();
  const hydrated = useCartHydrated();
  // Until the persisted cart has rehydrated, `lines` is masked to empty so the
  // first render matches the server (which has no localStorage). After
  // rehydration the real lines are exposed — never during hydration.
  const storeLines = useCartStore((state) =>
    hydrated ? state.lines : EMPTY_LINES,
  );
  const rawQuantityOf = useCartStore((state) => state.quantityOf);

  const lines = React.useMemo(() => Object.values(storeLines), [storeLines]);

  const count = React.useMemo(
    () => lines.reduce((sum, line) => sum + line.quantity, 0),
    [lines],
  );

  const totals = React.useMemo(
    () =>
      previewTotals(
        lines.map((line) => ({
          unitPrice: line.unitPrice,
          listPrice: line.listPrice,
          quantity: line.quantity,
        })),
      ),
    [lines],
  );

  return {
    lines,
    count,
    isEmpty: lines.length === 0,
    hydrating: session.hydrating,
    syncError: session.syncError,
    totals,
    quantityOf: rawQuantityOf,
    add: session.add,
    setQuantity: session.setQuantity,
    increment: session.increment,
    decrement: session.decrement,
    remove: session.remove,
    clear: session.clear,
    dismissSyncError: session.dismissSyncError,
  };
}

/**
 * Quantity for one product/weight pair.
 * Subscribes narrowly so only the stepper that changed re-renders — important
 * on a grid of 40+ cards. Returns 0 until the persisted cart has rehydrated so
 * the first render matches the server (no hydration mismatch).
 */
export function useCartQuantity(
  productId: string,
  weightId: string | null,
): number {
  const hydrated = useCartHydrated();
  const quantity = useCartStore((state) => state.quantityOf(productId, weightId));
  return hydrated ? quantity : 0;
}
