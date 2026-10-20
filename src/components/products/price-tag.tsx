import { splitMoney } from "@/lib/money";
import { cn } from "@/lib/utils";

/**
 * PriceTag — DESIGN_SYSTEM §7.3.
 *
 * The `₹` glyph is smaller than the amount so the number carries the weight.
 * Digits are tabular so prices align down a grid column.
 *
 * The effective price is highlighted only when the item is *in the basket*, so
 * the emphasis means "you're buying this" rather than just "this is cheap".
 */
export function PriceTag({
  amount,
  compareAt,
  size = "md",
  active = false,
  className,
}: {
  amount: number;
  /** Undiscounted price; rendered struck through when higher. */
  compareAt?: number;
  size?: "sm" | "md" | "lg";
  active?: boolean;
  className?: string;
}) {
  const { symbol, value } = splitMoney(amount);
  const showCompare =
    typeof compareAt === "number" && compareAt > amount && compareAt > 0;

  return (
    <span className={cn("flex items-baseline gap-1.5", className)}>
      <span
        data-numeric
        className={cn(
          "flex items-baseline font-semibold tracking-[-0.02em]",
          size === "lg"
            ? "text-price-lg"
            : size === "sm"
              ? "text-small"
              : "text-price",
          active ? "text-leaf-700" : "text-ink",
        )}
      >
        <span aria-hidden="true" className="mr-px text-[0.82em] text-ink-muted">
          {symbol}
        </span>
        <span className="sr-only">{symbol}</span>
        {value}
      </span>
      {showCompare ? (
        <span
          data-numeric
          className={cn(
            "text-ink-subtle line-through",
            size === "lg" ? "text-small" : "text-[0.75rem]",
          )}
        >
          <span className="sr-only">was </span>
          {splitMoney(compareAt).value}
        </span>
      ) : null}
    </span>
  );
}

/** "15% off" chip content, marigold-on-marigold-free so it never shouts. */
export function SavingLabel({
  percent,
  saving,
  className,
}: {
  percent: number;
  saving?: number;
  className?: string;
}) {
  if (percent <= 0) return null;
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-xs bg-beet-100 px-1.5 py-0.5 text-[10px] font-semibold tracking-[0.02em] text-beet-600",
        className,
      )}
    >
      {percent}% off
      <span className="sr-only">
        {saving ? `, saving ${saving} rupees` : ""}
      </span>
    </span>
  );
}
