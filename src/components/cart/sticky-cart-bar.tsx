"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowRight, ShoppingBag } from "lucide-react";
import { useCart } from "@/hooks/use-cart";
import { formatMoney } from "@/lib/money";
import { pluralise } from "@/lib/format";

/**
 * Mobile sticky cart bar — DESIGN_SYSTEM §11.
 *
 * Appears above the bottom nav whenever the basket has items, so the customer
 * never has to hunt for it. Hidden on `/cart` and `/checkout` (where it would be
 * redundant) and on desktop, where the header cart already has a fixed position.
 *
 * The whole bar is one link: a large, forgiving tap target, not a row of
 * fiddly sub-targets.
 */
export function StickyCartBar() {
  const pathname = usePathname();
  const { count, totals, hydrating } = useCart();

  const hiddenRoutes = ["/cart", "/checkout", "/order"];
  const shouldHide = hiddenRoutes.some((route) => pathname.startsWith(route));

  if (count === 0 || shouldHide) return null;

  return (
    <div
      className="fixed inset-x-0 z-40 px-3 lg:hidden"
      style={{ bottom: "calc(3.5rem + env(safe-area-inset-bottom) + 8px)" }}
    >
      <Link
        href="/cart"
        className="flex items-center gap-3 rounded-lg bg-leaf-700 px-3.5 py-2.5 shadow-lg"
        aria-label={`View basket: ${pluralise(count, "item")}, ${formatMoney(totals.total)}`}
      >
        <span className="grid size-9 shrink-0 place-items-center rounded-md bg-white/15">
          <ShoppingBag className="size-[18px] text-white" aria-hidden="true" />
        </span>
        <span className="flex min-w-0 flex-1 flex-col leading-tight">
          <span className="text-caption text-white/75">
            {hydrating ? "Syncing basket…" : pluralise(count, "item")}
          </span>
          <span
            className="text-body font-semibold text-white tabular-nums"
            data-numeric
          >
            {formatMoney(totals.total)}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-1 text-small font-medium text-white/90">
          View basket
          <ArrowRight className="size-4" aria-hidden="true" />
        </span>
      </Link>
    </div>
  );
}
