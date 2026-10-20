"use client";

import * as React from "react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { Check, Loader2, Minus, Plus } from "lucide-react";
import { useCartSession } from "@/components/cart/cart-provider";
import { useCartQuantity } from "@/hooks/use-cart";
import { buildCartLine } from "@/lib/cart-line";
import type { CatalogueProduct, ProductWeightRow } from "@/types/database";
import { cn } from "@/lib/utils";

/**
 * QtyStepper — DESIGN_SYSTEM §7.6. The signature control.
 *
 * "Add" and "the thing you added" occupy the same pixels: the closed pill morphs
 * in place into `− n +`. The container width is identical in both states
 * (min-width 92px) so a grid row never reflows when the customer taps ADD — the
 * single most disruptive thing a grocery grid can do.
 *
 * Behaviour:
 *  • `−` at quantity 1 removes the line (and the pill collapses back to ADD).
 *  • `+` disables at `products.quantity_limit` and surfaces a "Max N" hint.
 *  • A `aria-live` region announces the resulting basket quantity.
 *  • Under `prefers-reduced-motion` the morph becomes a plain opacity swap.
 */

const SIZES = {
  sm: { h: "h-7", pad: "px-2.5", icon: "size-[13px]", step: "size-6", text: "text-[0.75rem]" },
  md: { h: "h-9", pad: "px-3.5", icon: "size-4", step: "size-7", text: "text-small" },
  lg: { h: "h-11", pad: "px-4", icon: "size-[18px]", step: "size-9", text: "text-body" },
} as const;

export function QtyStepper({
  product,
  weight,
  size = "md",
  className,
  disabled = false,
  disabledLabel,
  onAdded,
}: {
  product: CatalogueProduct;
  weight: ProductWeightRow | null;
  size?: keyof typeof SIZES;
  className?: string;
  disabled?: boolean;
  disabledLabel?: string;
  onAdded?: () => void;
}) {
  const quantity = useCartQuantity(product.id, weight?.id ?? null);
  const { add, increment, decrement, hydrating } = useCartSession();
  const reduceMotion = useReducedMotion();
  const [justAdded, setJustAdded] = React.useState(false);
  const s = SIZES[size];

  // Reset the success flash so it can fire again on the next tap.
  React.useEffect(() => {
    if (!justAdded) return;
    const timer = window.setTimeout(() => setJustAdded(false), 900);
    return () => window.clearTimeout(timer);
  }, [justAdded]);

  const atLimit = quantity >= product.quantity_limit;

  function handleAdd() {
    add(buildCartLine(product, weight, 1));
    setJustAdded(true);
    onAdded?.();
  }

  if (disabled) {
    return (
      <span
        className={cn(
          "inline-flex select-none items-center justify-center rounded-pill bg-surface-muted font-medium text-ink-subtle",
          s.h,
          s.pad,
          s.text,
          "min-w-[92px]",
          className,
        )}
      >
        {disabledLabel ?? "Unavailable"}
      </span>
    );
  }

  return (
    <div className={cn("inline-flex flex-col items-end gap-0.5", className)}>
      <div
        className={cn(
          "relative inline-flex min-w-[92px] select-none items-center justify-center overflow-hidden rounded-pill",
          s.h,
          quantity > 0 ? "bg-leaf-600 text-white" : "bg-leaf-600 text-white",
          !quantity && "shadow-xs",
        )}
      >
        <AnimatePresence initial={false} mode="wait">
          {quantity === 0 ? (
            <motion.button
              key="add"
              type="button"
              onClick={handleAdd}
              initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -6 }}
              transition={{ duration: 0.15, ease: [0.2, 0.8, 0.2, 1] }}
              className={cn(
                "inline-flex h-full w-full items-center justify-center gap-1.5 font-semibold tracking-[0.01em]",
                s.pad,
                s.text,
              )}
              aria-label={`Add ${product.name} to basket`}
            >
              {hydrating ? (
                <Loader2 className={cn(s.icon, "animate-spin")} aria-hidden="true" />
              ) : justAdded ? (
                <Check className={s.icon} strokeWidth={3} aria-hidden="true" />
              ) : (
                <Plus className={s.icon} strokeWidth={2.6} aria-hidden="true" />
              )}
              ADD
            </motion.button>
          ) : (
            <motion.div
              key="stepper"
              initial={reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.94 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.94 }}
              transition={{ duration: 0.15, ease: [0.2, 0.8, 0.2, 1] }}
              className="flex h-full w-full items-center justify-between"
            >
              <button
                type="button"
                onClick={() => decrement(product.id, weight?.id ?? null)}
                className={cn(
                  "grid h-full place-items-center text-white/90 transition-colors hover:bg-leaf-700",
                  s.step,
                )}
                aria-label={
                  quantity === 1
                    ? `Remove ${product.name} from basket`
                    : `Reduce ${product.name} quantity`
                }
              >
                <Minus className={s.icon} strokeWidth={2.6} aria-hidden="true" />
              </button>

              <span
                data-numeric
                className={cn(
                  "min-w-4 text-center font-semibold tabular-nums",
                  s.text,
                )}
                aria-live="polite"
              >
                {quantity}
              </span>

              <button
                type="button"
                onClick={() => increment(product.id, weight?.id ?? null)}
                disabled={atLimit}
                className={cn(
                  "grid h-full place-items-center text-white transition-colors hover:bg-leaf-700 disabled:cursor-not-allowed disabled:text-white/40 disabled:hover:bg-transparent",
                  s.step,
                )}
                aria-label={
                  atLimit
                    ? `Maximum ${product.quantity_limit} of ${product.name} in basket`
                    : `Increase ${product.name} quantity`
                }
              >
                <Plus className={s.icon} strokeWidth={2.6} aria-hidden="true" />
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {atLimit && quantity > 0 ? (
        <span className="text-[10px] font-medium text-marigold-600">
          Max {product.quantity_limit}
        </span>
      ) : null}
    </div>
  );
}
