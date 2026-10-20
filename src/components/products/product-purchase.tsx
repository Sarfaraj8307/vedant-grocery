"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { AlertTriangle, Info, Truck } from "lucide-react";
import { PriceTag, SavingLabel } from "@/components/products/price-tag";
import { QtyStepper } from "@/components/products/qty-stepper";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useCartSession } from "@/components/cart/cart-provider";
import { buildCartLine, unavailableReason } from "@/lib/cart-line";
import { priceFor, SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import { formatMoney } from "@/lib/money";
import { cn } from "@/lib/utils";
import type { CatalogueProduct, ProductWeightRow } from "@/types/database";

/**
 * Purchase panel for the product page.
 *
 * This is the only client component on the page. Everything else — heading,
 * description, related products, structured data — is server-rendered HTML.
 *
 * Weight selection is a real control, not decoration: `product_weights` supplies
 * the options and the chosen `weight_id` is what `place_order` prices and what
 * `cart_items` keys the line by. The options are never hard-coded, so adding a
 * 750 g pack in the admin app makes it appear here immediately.
 */
export function ProductPurchase({
  product,
  className,
}: {
  product: CatalogueProduct;
  className?: string;
}) {
  const router = useRouter();
  const { add, hydrating } = useCartSession();

  const firstAvailable =
    product.weights.find((w) => w.is_available) ?? product.weights[0] ?? null;

  const [selectedId, setSelectedId] = React.useState<string | null>(
    firstAvailable?.id ?? null,
  );
  const [addingToCheckout, setAddingToCheckout] = React.useState(false);

  const selected: ProductWeightRow | null = React.useMemo(
    () => product.weights.find((w) => w.id === selectedId) ?? null,
    [product.weights, selectedId],
  );

  const breakdown = priceFor(product, selected);
  const reason = unavailableReason(product, selected);
  const inStock = reason === null;

  // What the customer saves on this exact pack, so the claim matches the charge.
  const lineForCheckout = React.useMemo(
    () => buildCartLine(product, selected, 1),
    [product, selected],
  );

  async function buyNow() {
    setAddingToCheckout(true);
    add(lineForCheckout, { silent: true });
    // Checkout requires a server-side cart, so a signed-out customer is sent
    // through sign-in first; the merge on sign-in pushes this line up.
    router.push("/checkout");
  }

  return (
    <div className={cn("flex flex-col gap-5", className)}>
      {/* --- Price block ------------------------------------------------- */}
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <PriceTag
            amount={breakdown.unitPrice}
            compareAt={breakdown.isDiscounted ? breakdown.listPrice : undefined}
            size="lg"
          />
          <span className="text-small text-ink-muted">
            {selected ? `per ${selected.label}` : `per ${product.unit_label}`}
          </span>
          {breakdown.isDiscounted ? (
            <SavingLabel
              percent={breakdown.savingPercent}
              saving={breakdown.savingPerUnit}
            />
          ) : null}
        </div>

        {selected ? (
          <p className="text-small text-ink-subtle" data-numeric>
            {formatMoney(breakdown.unitPrice)} per {selected.label} ·{" "}
            {selected.grams >= 1000
              ? `${formatMoney(Math.round((breakdown.unitPrice / selected.grams) * 1000 * 100) / 100)} per kg`
              : `${formatMoney(Math.round((breakdown.unitPrice / selected.grams) * 100 * 100) / 100)} per 100 g`}
          </p>
        ) : null}

        {!breakdown.isDiscounted && product.activeOffer ? (
          <Badge variant="offer" size="md" className="self-start">
            {product.activeOffer.title}
          </Badge>
        ) : null}
      </div>

      {/* --- Weight / pack selection ------------------------------------- */}
      {product.weights.length > 0 ? (
        <fieldset className="flex flex-col gap-2.5">
          <legend className="text-small font-medium text-ink">
            Choose your pack
          </legend>
          <div
            role="radiogroup"
            aria-label="Pack size"
            className="flex flex-wrap gap-2"
          >
            {product.weights.map((weight) => {
              const isSelected = weight.id === selectedId;
              const weightPrice = priceFor(product, weight);
              return (
                <button
                  key={weight.id}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  aria-label={`${weight.label}, ${formatMoney(weightPrice.unitPrice)}`}
                  disabled={!weight.is_available}
                  onClick={() => setSelectedId(weight.id)}
                  className={cn(
                    "flex min-w-[86px] flex-col items-start gap-0.5 rounded-md border-[1.5px] px-3 py-2 text-left transition-[border-color,background-color] duration-[var(--motion-fast)]",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600/25",
                    isSelected
                      ? "border-leaf-600 bg-leaf-50"
                      : "border-line-strong bg-surface hover:border-ink-subtle",
                    !weight.is_available &&
                      "cursor-not-allowed opacity-45 hover:border-line-strong",
                  )}
                >
                  <span
                    className={cn(
                      "text-body font-medium",
                      isSelected ? "text-leaf-800" : "text-ink",
                    )}
                  >
                    {weight.label}
                  </span>
                  <span
                    className={cn(
                      "text-small tabular-nums",
                      isSelected ? "text-leaf-700" : "text-ink-muted",
                    )}
                    data-numeric
                  >
                    {weight.is_available
                      ? formatMoney(weightPrice.unitPrice)
                      : "Sold out"}
                  </span>
                </button>
              );
            })}
          </div>
        </fieldset>
      ) : null}

      {/* --- Availability ------------------------------------------------- */}
      <AvailabilityNotice
        reason={reason}
        inStock={inStock}
        limit={product.quantity_limit}
        unitPrice={breakdown.unitPrice}
      />

      {/* --- Actions ------------------------------------------------------ */}
      <div className="flex flex-col gap-2.5 sm:flex-row sm:items-center">
        <QtyStepper
          product={product}
          weight={selected}
          size="lg"
          disabled={!inStock}
          disabledLabel="Out of stock"
          className="sm:order-2"
        />
        <Button
          variant="secondary"
          size="lg"
          block
          className="sm:order-1 sm:w-auto"
          disabled={!inStock}
          loading={addingToCheckout}
          loadingLabel="Taking you to checkout…"
          onClick={() => void buyNow()}
        >
          Buy now
        </Button>
      </div>

      {hydrating ? (
        <p className="flex items-center gap-1.5 text-small text-ink-subtle">
          <Info className="size-3.5" aria-hidden="true" />
          Syncing your basket with your account…
        </p>
      ) : null}

      <p className="flex items-start gap-1.5 text-small text-ink-muted">
        <Truck className="mt-0.5 size-3.5 shrink-0 text-leaf-600" aria-hidden="true" />
        Free delivery over {formatMoney(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold)}.
        Below that, delivery is {formatMoney(SHOP_SETTINGS_PREVIEW.deliveryCharge)}.
      </p>
    </div>
  );
}

function AvailabilityNotice({
  reason,
  inStock,
  limit,
  unitPrice,
}: {
  reason: string | null;
  inStock: boolean;
  limit: number;
  unitPrice: number;
}) {
  if (!inStock) {
    return (
      <p className="flex items-start gap-2 rounded-md border border-warning-bg bg-warning-bg px-3.5 py-2.5 text-small text-warning">
        <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>
          <strong className="font-semibold">{reason}.</strong> We restock
          every morning — check back, or{" "}
          <a href="/contact" className="underline underline-offset-2">
            ask the shop
          </a>{" "}
          to hold some for you.
        </span>
      </p>
    );
  }

  return (
    <p className="text-small text-ink-subtle">
      In stock today · maximum {limit} per order
      {unitPrice > 0 ? "" : ""}
    </p>
  );
}
