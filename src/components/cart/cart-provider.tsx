"use client";

import * as React from "react";
import { toast } from "sonner";
import { useCartStore, type CartLine } from "@/stores/cart-store";
import {
  CartServiceError,
  fetchRemoteCart,
  persistLine,
  replaceRemoteCart,
  type RemoteCartLine,
} from "@/services/cart-service";
import { cartKey } from "@/lib/pricing";
import { pluralise } from "@/lib/format";

/**
 * CartSession — the bridge between the local cart and the server cart.
 *
 * Two modes:
 *  • **Signed out** — the cart lives only in `localStorage`. Nothing is written
 *    to the database (RLS would refuse it anyway; `cart_items` requires
 *    `authenticated`).
 *  • **Signed in** — every mutation is mirrored to `cart_items`, because
 *    `public.place_order` reads the server cart and nothing else. The local
 *    copy exists purely so the UI is instant.
 *
 * The merge on sign-in takes the **maximum** quantity per line. That is the only
 * policy that never silently loses a customer's item: a basket built before
 * signing in survives, and a basket already on the server is never reduced.
 */

type CartSessionValue = {
  userId: string | null;
  /** True until the signed-in cart has been reconciled at least once. */
  hydrating: boolean;
  add: (line: Omit<CartLine, "addedAt">, options?: { silent?: boolean }) => void;
  setQuantity: (
    productId: string,
    weightId: string | null,
    quantity: number,
  ) => void;
  increment: (productId: string, weightId: string | null) => void;
  decrement: (productId: string, weightId: string | null) => void;
  remove: (productId: string, weightId: string | null) => void;
  clear: () => void;
  syncError: string | null;
  dismissSyncError: () => void;
};

const CartSessionContext = React.createContext<CartSessionValue | null>(null);

export function CartProvider({
  userId,
  children,
}: {
  userId: string | null;
  children: React.ReactNode;
}) {
  const [hydrating, setHydrating] = React.useState(Boolean(userId));
  const syncedFor = React.useRef<string | null>(null);

  const setSyncing = useCartStore((s) => s.setSyncing);
  const setSyncError = useCartStore((s) => s.setSyncError);
  const syncError = useCartStore((s) => s.syncError);

  /* ---------------------------------------------------------------------- */
  /* Rehydrate the persisted (localStorage) cart AFTER mount.               */
  /*                                                                         */
  /* `useCartStore` is created with `skipHydration: true` so the client's    */
  /* first render matches the server's empty-cart render — no hydration      */
  /* mismatch. We rehydrate here, after mount, so a returning customer's     */
  /* basket reappears via a post-mount re-render (correct UX, never a        */
  /* mismatch).                                                             */
  /*                                                                         */
  /* This MUST be declared before the `reconcile` effect below: for signed-  */
  /* in users the rehydrated `lines` must be present when `reconcile` reads  */
  /* `useCartStore.getState().lines`. Because `localStorage` is synchronous, */
  /* `rehydrate()` updates state synchronously within this effect, so the    */
  /* ordering is correct.                                                    */
  /* ---------------------------------------------------------------------- */
  React.useEffect(() => {
    // Rehydrate the persisted (localStorage) cart after mount. `useCartStore`
    // is created with `skipHydration: true`, so this is the ONLY place the
    // persisted basket is read. Components gate their rendering on a
    // `useIsMounted` flag (see `use-cart.ts`), so the populated cart only
    // appears as a post-mount re-render — never during hydration, which would
    // otherwise produce a server/client HTML mismatch.
    if (typeof window === "undefined") return;
    void useCartStore.persist.rehydrate();
  }, []);

  /* ---------------------------------------------------------------------- */
  /* Reconcile the local cart with the server cart once per sign-in         */
  /* ---------------------------------------------------------------------- */
  React.useEffect(() => {
    let cancelled = false;

    async function reconcile() {
      if (!userId) {
        setHydrating(false);
        syncedFor.current = null;
        return;
      }
      if (syncedFor.current === userId) return;

      setHydrating(true);
      setSyncing(true);
      const local = Object.values(useCartStore.getState().lines);

      try {
        const remote = await fetchRemoteCart();

        // Merge: maximum quantity per line key.
        const merged = new Map<string, RemoteCartLine>();
        for (const line of remote) {
          merged.set(cartKey(line.product_id, line.weight_id), {
            product_id: line.product_id,
            weight_id: line.weight_id,
            quantity: line.quantity,
          });
        }
        for (const line of local) {
          const key = cartKey(line.productId, line.weightId);
          const existing = merged.get(key);
          merged.set(key, {
            product_id: line.productId,
            weight_id: line.weightId,
            quantity: Math.max(existing?.quantity ?? 0, line.quantity),
          });
        }

        const mergedLines = [...merged.values()];
        const remoteOnly = remote.length;
        const needsWrite =
          mergedLines.length > 0 &&
          // Only write when the merge actually changed the server state.
          (local.length > 0 || remoteOnly !== mergedLines.length);

        if (needsWrite) {
          await replaceRemoteCart(mergedLines);
        }

        if (cancelled) return;

        // Rebuild the local snapshot from the merged quantities, reusing the
        // price snapshots we already have for locally-known lines.
        const byKey = new Map(local.map((l) => [cartKey(l.productId, l.weightId), l]));
        const nextLines: CartLine[] = [];
        for (const line of mergedLines) {
          const key = cartKey(line.product_id, line.weight_id);
          const snapshot = byKey.get(key);
          if (!snapshot) continue; // unknown product: catalogue refresh will fill it
          nextLines.push({ ...snapshot, quantity: line.quantity });
        }

        if (local.length > 0) {
          useCartStore.getState().replaceAll(nextLines);
        }

        syncedFor.current = userId;
        setSyncError(null);
      } catch (error) {
        if (!cancelled) {
          setSyncError(
            error instanceof CartServiceError
              ? error.message
              : "We could not sync your basket. Please try again.",
          );
        }
      } finally {
        if (!cancelled) {
          setHydrating(false);
          setSyncing(false);
        }
      }
    }

    void reconcile();
    return () => {
      cancelled = true;
    };
  }, [userId, setSyncError, setSyncing]);

  /* ---------------------------------------------------------------------- */
  /* Mutations: optimistic locally, then persisted when signed in           */
  /* ---------------------------------------------------------------------- */

  const run = React.useCallback(
    async (
      mutate: () => void,
      persist: () => Promise<void>,
      snapshot: CartLine[],
    ) => {
      mutate();
      if (!userId) return;
      try {
        await persist();
      } catch (error) {
        // Roll the UI back so it never shows a basket the server disagrees with.
        useCartStore.getState().replaceAll(snapshot);
        const message =
          error instanceof CartServiceError
            ? error.message
            : "We could not save that to your basket.";
        setSyncError(message);
        toast.error(message);
      }
    },
    [userId, setSyncError],
  );

  const add = React.useCallback<CartSessionValue["add"]>(
    (line, options) => {
      const snapshot = Object.values(useCartStore.getState().lines);
      const existing = useCartStore
        .getState()
        .lines[cartKey(line.productId, line.weightId)];
      const nextQuantity = Math.min(
        (existing?.quantity ?? 0) + line.quantity,
        Math.max(1, line.quantityLimit),
      );

      void run(
        () => useCartStore.getState().add(line),
        () =>
          persistLine({
            productId: line.productId,
            weightId: line.weightId,
            quantity: nextQuantity,
          }),
        snapshot,
      );

      if (!options?.silent) {
        toast.success(`${line.name} added`, {
          description: `${nextQuantity} × ${line.weightLabel}`,
          duration: 2000,
        });
      }
    },
    [run],
  );

  const setQuantity = React.useCallback<CartSessionValue["setQuantity"]>(
    (productId, weightId, quantity) => {
      const snapshot = Object.values(useCartStore.getState().lines);
      void run(
        () => useCartStore.getState().setQuantity(productId, weightId, quantity),
        () => persistLine({ productId, weightId, quantity }),
        snapshot,
      );
    },
    [run],
  );

  const increment = React.useCallback<CartSessionValue["increment"]>(
    (productId, weightId) => {
      const snapshot = Object.values(useCartStore.getState().lines);
      const current = useCartStore.getState().quantityOf(productId, weightId);
      const limit =
        useCartStore.getState().lines[cartKey(productId, weightId)]
          ?.quantityLimit ?? 100;
      const next = Math.min(current + 1, limit);
      if (next === current) return;

      void run(
        () => useCartStore.getState().setQuantity(productId, weightId, next),
        () => persistLine({ productId, weightId, quantity: next }),
        snapshot,
      );
    },
    [run],
  );

  const decrement = React.useCallback<CartSessionValue["decrement"]>(
    (productId, weightId) => {
      const snapshot = Object.values(useCartStore.getState().lines);
      const current = useCartStore.getState().quantityOf(productId, weightId);
      const next = current - 1;

      void run(
        () => useCartStore.getState().setQuantity(productId, weightId, next),
        () => persistLine({ productId, weightId, quantity: next }),
        snapshot,
      );

      if (next <= 0) {
        const name =
          useCartStore.getState().lines[cartKey(productId, weightId)]?.name ??
          "Item";
        toast.success(`${name} removed from basket`, { duration: 1800 });
      }
    },
    [run],
  );

  const remove = React.useCallback<CartSessionValue["remove"]>(
    (productId, weightId) => {
      const snapshot = Object.values(useCartStore.getState().lines);
      const name =
        useCartStore.getState().lines[cartKey(productId, weightId)]?.name ??
        "Item";
      void run(
        () => useCartStore.getState().remove(productId, weightId),
        () => persistLine({ productId, weightId, quantity: 0 }),
        snapshot,
      );
      toast.success(`${name} removed from basket`, { duration: 1800 });
    },
    [run],
  );

  const clear = React.useCallback(() => {
    const snapshot = Object.values(useCartStore.getState().lines);
    const previousCount = snapshot.reduce((sum, l) => sum + l.quantity, 0);
    void run(
      () => useCartStore.getState().clear(),
      () => replaceRemoteCart([]),
      snapshot,
    );
    toast.success(`Basket emptied (${pluralise(previousCount, "item")})`);
  }, [run]);

  const value = React.useMemo<CartSessionValue>(
    () => ({
      userId,
      hydrating,
      add,
      setQuantity,
      increment,
      decrement,
      remove,
      clear,
      syncError,
      dismissSyncError: () => setSyncError(null),
    }),
    [
      userId,
      hydrating,
      add,
      setQuantity,
      increment,
      decrement,
      remove,
      clear,
      syncError,
      setSyncError,
    ],
  );

  return (
    <CartSessionContext.Provider value={value}>
      {children}
    </CartSessionContext.Provider>
  );
}

export function useCartSession(): CartSessionValue {
  const context = React.useContext(CartSessionContext);
  if (!context) {
    throw new Error("useCartSession must be used inside <CartProvider>");
  }
  return context;
}
