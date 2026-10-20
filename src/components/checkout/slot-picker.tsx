"use client";

import * as React from "react";
import { CalendarClock } from "lucide-react";
import { RadioGroup, RadioGroupItem } from "@/components/ui/menu";
import { Badge } from "@/components/ui/badge";
import { formatSlotRange, relativeDay } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { DeliverySlotRow } from "@/types/database";

/**
 * Delivery slot picker.
 *
 * Slots are grouped by day and rendered as radio options, which gives keyboard
 * arrow navigation and `aria-checked` for free.
 *
 * The component only ever receives slots that already satisfy the server's
 * predicate (`is_active` AND `starts_at > now()`), because `getDeliverySlots`
 * applies exactly the same filter `place_order` applies. Capacity is shown but
 * treated as a hint: the authoritative capacity check happens inside the
 * transaction, and a full slot surfaces as a `delivery slot full` error which
 * the checkout maps to "that slot just filled up — pick another".
 */
export function SlotPicker({
  slots,
  load,
  selectedId,
  onSelect,
  className,
}: {
  slots: DeliverySlotRow[];
  /** Existing non-cancelled orders per slot id. */
  load: Record<string, number>;
  selectedId: string | null;
  onSelect: (slot: DeliverySlotRow) => void;
  className?: string;
}) {
  const grouped = React.useMemo(() => {
    const map = new Map<string, DeliverySlotRow[]>();
    for (const slot of slots) {
      const key = new Date(slot.starts_at).toDateString();
      const list = map.get(key);
      if (list) list.push(slot);
      else map.set(key, [slot]);
    }
    return [...map.entries()];
  }, [slots]);

  if (slots.length === 0) {
    return (
      <div className={cn("rounded-md border border-warning-bg bg-warning-bg p-4", className)}>
        <p className="flex items-start gap-2 text-small text-warning">
          <CalendarClock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            <strong className="font-semibold">
              No delivery slots are open right now.
            </strong>{" "}
            We publish slots a few days ahead. Please{" "}
            <a href="/contact" className="underline underline-offset-2">
              message the shop
            </a>{" "}
            and we will arrange a time with you directly.
          </span>
        </p>
      </div>
    );
  }

  return (
    <RadioGroup
      value={selectedId ?? undefined}
      onValueChange={(value) => {
        const found = slots.find((s) => s.id === value);
        if (found) onSelect(found);
      }}
      className={cn("flex flex-col gap-4", className)}
      aria-label="Delivery slot"
    >
      {grouped.map(([dayKey, daySlots]) => (
        <div key={dayKey} className="flex flex-col gap-2">
          <p className="text-caption text-ink-subtle">
            {relativeDay(daySlots[0]!.starts_at)}
          </p>
          <div className="grid gap-2 sm:grid-cols-2">
            {daySlots.map((slot) => {
              const booked = load[slot.id] ?? 0;
              const remaining = Math.max(0, slot.capacity - booked);
              const full = remaining === 0;
              const selected = slot.id === selectedId;

              return (
                <label
                  key={slot.id}
                  htmlFor={`slot-${slot.id}`}
                  className={cn(
                    "flex cursor-pointer items-center gap-3 rounded-md border-[1.5px] px-3 py-2.5 transition-colors",
                    selected
                      ? "border-leaf-600 bg-leaf-50"
                      : "border-line bg-surface hover:border-line-strong",
                    full && "cursor-not-allowed opacity-50 hover:border-line",
                  )}
                >
                  <RadioGroupItem
                    value={slot.id}
                    id={`slot-${slot.id}`}
                    disabled={full}
                  />
                  <span className="min-w-0 flex-1">
                    <span className="block text-body font-medium text-ink">
                      {formatSlotRange(slot.starts_at, slot.ends_at)}
                    </span>
                    <span className="mt-0.5 block text-small text-ink-subtle">
                      {full
                        ? "Fully booked"
                        : remaining <= 3
                          ? `Only ${remaining} left`
                          : "Available"}
                    </span>
                  </span>
                  {full ? (
                    <Badge variant="neutral" size="sm">
                      Full
                    </Badge>
                  ) : remaining <= 3 ? (
                    <Badge variant="warning" size="sm">
                      Almost full
                    </Badge>
                  ) : null}
                </label>
              );
            })}
          </div>
        </div>
      ))}
    </RadioGroup>
  );
}
