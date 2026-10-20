"use client";

import { create } from "zustand";
import { persist, createJSONStorage } from "zustand/middleware";
import { cartKey } from "@/lib/pricing";

/**
 * Cart state.
 *
 * A cart line is identified by *(product, weight)* — the same key the database
 * uses in its two partial unique indexes on `cart_items`
 * (docs/BACKEND_CONTRACT.md §2). Using the identical key locally and remotely
 * is what makes reconciliation trivial: a local line is either present on the
 * server or it is not.
 *
 * Each line carries a **display + price snapshot** so the cart, header badge and
 * checkout render instantly from local state without refetching the catalogue.
 * The snapshot is a preview: `place_order` re-prices everything server-side.
 *
 * Persisted to `localStorage` so a signed-out customer's basket survives a
 * refresh, tab close and (on iOS) a PWA relaunch. On sign-in the local cart is
 * merged into the customer's server cart by `CartSync`.
 */

export type CartLine = {
  readonly productId: string;
  readonly weightId: string | null;
  quantity: number;
  /* --- snapshot for rendering ------------------------------------------- */
  readonly name: string;
  readonly glyph: string;
  readonly accent: number;
  readonly imageUrl: string | null;
  readonly unitLabel: string;
  readonly weightLabel: string;
  /** Price the customer pays per unit (after the best live offer). */
  unitPrice: number;
  /** Undiscounted price, for the strikethrough. */
  listPrice: number;
  /** `products.quantity_limit` — the `+` disables at this quantity. */
  readonly quantityLimit: number;
  /** Set when the item was added from an out-of-stock-tolerant context. */
  addedAt: number;
};

export type CartState = {
  lines: Record<string, CartLine>;
  /** Set while the server cart is being reconciled, to gate checkout. */
  syncing: boolean;
  /** Last server-sync failure message, surfaced in the UI. */
  syncError: string | null;
};

export type CartActions = {
  add: (line: Omit<CartLine, "addedAt">) => void;
  setQuantity: (productId: string, weightId: string | null, quantity: number) => void;
  increment: (productId: string, weightId: string | null) => void;
  decrement: (productId: string, weightId: string | null) => void;
  remove: (productId: string, weightId: string | null) => void;
  clear: () => void;
  /** Replace the whole cart (used after a server fetch / sign-in merge). */
  replaceAll: (lines: CartLine[]) => void;
  /** Refresh price/availability snapshots from fresh catalogue data. */
  refreshSnapshots: (lines: CartLine[]) => void;
  setSyncing: (syncing: boolean) => void;
  setSyncError: (message: string | null) => void;
  quantityOf: (productId: string, weightId: string | null) => number;
};

export type CartStore = CartState & CartActions;

const EMPTY: CartState = { lines: {}, syncing: false, syncError: null };

/** Return a copy of `lines` without `key` (never mutates the input). */
function omitLine(
  lines: Record<string, CartLine>,
  key: string,
): Record<string, CartLine> {
  const next: Record<string, CartLine> = {};
  for (const [lineKey, line] of Object.entries(lines)) {
    if (lineKey !== key) next[lineKey] = line;
  }
  return next;
}

function clampQuantity(quantity: number, limit: number): number {
  if (!Number.isFinite(quantity)) return 1;
  return Math.max(1, Math.min(Math.trunc(quantity), Math.max(1, limit)));
}

export const useCartStore = create<CartStore>()(
  persist(
    (set, get) => ({
      ...EMPTY,

      add: (line) =>
        set((state) => {
          const key = cartKey(line.productId, line.weightId);
          const existing = state.lines[key];
          const nextQuantity = clampQuantity(
            (existing?.quantity ?? 0) + line.quantity,
            line.quantityLimit,
          );
          return {
            lines: {
              ...state.lines,
              [key]: {
                ...line,
                quantity: nextQuantity,
                addedAt: existing?.addedAt ?? Date.now(),
              },
            },
          };
        }),

      setQuantity: (productId, weightId, quantity) =>
        set((state) => {
          const key = cartKey(productId, weightId);
          const existing = state.lines[key];
          if (!existing) return state;

          if (quantity <= 0) {
            return { lines: omitLine(state.lines, key) };
          }

          return {
            lines: {
              ...state.lines,
              [key]: {
                ...existing,
                quantity: clampQuantity(quantity, existing.quantityLimit),
              },
            },
          };
        }),

      increment: (productId, weightId) => {
        const { quantityOf, setQuantity, lines } = get();
        const key = cartKey(productId, weightId);
        const line = lines[key];
        if (!line) return;
        setQuantity(productId, weightId, quantityOf(productId, weightId) + 1);
      },

      decrement: (productId, weightId) => {
        const { quantityOf, setQuantity } = get();
        setQuantity(productId, weightId, quantityOf(productId, weightId) - 1);
      },

      remove: (productId, weightId) =>
        set((state) => {
          const key = cartKey(productId, weightId);
          if (!(key in state.lines)) return state;
          return { lines: omitLine(state.lines, key) };
        }),

      clear: () => set({ lines: {}, syncError: null }),

      replaceAll: (lines) =>
        set({
          lines: Object.fromEntries(
            lines.map((line) => [cartKey(line.productId, line.weightId), line]),
          ),
          syncError: null,
        }),

      /**
       * Keep the prices the customer sees honest when the catalogue changes
       * underneath them (an offer expiring, stock returning). Called after the
       * catalogue loads. Quantities are preserved and re-clamped to any new
       * `quantity_limit`.
       */
      refreshSnapshots: (fresh) =>
        set((state) => {
          const byKey = new Map(
            fresh.map((line) => [cartKey(line.productId, line.weightId), line]),
          );
          const lines: Record<string, CartLine> = {};
          for (const [key, existing] of Object.entries(state.lines)) {
            const update = byKey.get(key);
            lines[key] = update
              ? {
                  ...update,
                  quantity: clampQuantity(existing.quantity, update.quantityLimit),
                  addedAt: existing.addedAt,
                }
              : existing;
          }
          return { lines };
        }),

      setSyncing: (syncing) => set({ syncing }),
      setSyncError: (syncError) => set({ syncError }),

      quantityOf: (productId, weightId) =>
        get().lines[cartKey(productId, weightId)]?.quantity ?? 0,
    }),
    {
      name: "vedant.cart.v1",
      version: 1,
      storage: createJSONStorage(() => localStorage),
      // `syncing` / `syncError` are transient — never restore them.
      partialize: (state) => ({ lines: state.lines }),
      // Do NOT rehydrate synchronously at store creation. Rehydrating here would
      // populate `lines` on the client's *first* render while the server rendered
      // an empty cart, producing a React hydration mismatch for every consumer of
      // the persisted cart (QtyStepper, SiteHeader badge, StickyCartBar). The
      // cart is rehydrated explicitly after mount in `CartProvider`
      // (cart-provider.tsx) so the persisted basket reappears as a post-mount
      // re-render rather than a mismatch.
      skipHydration: true,
    },
  ),
);

/* -------------------------------------------------------------------------- */
/* Derived selectors — components subscribe to these, not to `lines`.         */
/* -------------------------------------------------------------------------- */

export function selectLines(state: CartStore): CartLine[] {
  return Object.values(state.lines);
}

/** Total number of individual units in the cart (drives the header badge). */
export function cartCount(lines: readonly CartLine[]): number {
  return lines.reduce((sum, line) => sum + line.quantity, 0);
}

/** Subtotal in rupees, computed on integer paise to avoid float drift. */
export function cartSubtotal(lines: readonly CartLine[]): number {
  const paise = lines.reduce(
    (sum, line) => sum + Math.round(line.unitPrice * 100) * line.quantity,
    0,
  );
  return paise / 100;
}

export function cartListSubtotal(lines: readonly CartLine[]): number {
  const paise = lines.reduce(
    (sum, line) => sum + Math.round(line.listPrice * 100) * line.quantity,
    0,
  );
  return paise / 100;
}
