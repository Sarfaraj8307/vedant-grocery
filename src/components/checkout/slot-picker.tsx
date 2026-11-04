"use client";

import * as React from "react";
import { CalendarClock, Zap } from "lucide-react";
import { RadioGroup, RadioGroupItem } from "@/components/ui/menu";
import { Badge } from "@/components/ui/badge";
import { formatSlotRange } from "@/lib/format";
import {
  formatInstantDeliveryEta,
  INSTANT_DELIVERY_ETA_MAX_DEFAULT,
  INSTANT_DELIVERY_ETA_MIN_DEFAULT,
} from "@/lib/instant-delivery";
import { buildTwoDaySlots } from "@/lib/ist-slots";
import { cn } from "@/lib/utils";
import type { DeliverySlotRow } from "@/types/database";

/** Stable "value" for the Instant Delivery radio inside the shared group. */
const INSTANT_DELIVERY_VALUE = "__instant__";

/**
 * Delivery slot picker.
 *
 * Slots are grouped by their IST calendar day (today / tomorrow) and rendered
 * as radio options. The two days are always shown, so a day with no slots
 * reads "no slots yet" instead of disappearing. When the store offers instant
 * delivery, a leading "Instant Delivery" option appears above the scheduled
 * slots, with its ETA subtitle derived from the admin-editable DB values.
 *
 * Capacity is shown but treated as a hint: the authoritative capacity check
 * happens inside the transaction, and a full slot surfaces as a "delivery slot
 * full" error which the checkout maps to "that slot just filled up — pick
 * another".
 */
export function SlotPicker({
  slots,
  load,
  selectedId,
  onSelect,
  instantEnabled = false,
  instantSelected = false,
  onSelectInstant,
  etaMin = INSTANT_DELIVERY_ETA_MIN_DEFAULT,
  etaMax = INSTANT_DELIVERY_ETA_MAX_DEFAULT,
  className,
}: {
  slots: DeliverySlotRow[];
  /** Existing non-cancelled orders per slot id. */
  load: Record<string, number>;
  selectedId: string | null;
  onSelect: (slot: DeliverySlotRow) => void;
  /** Show the Instant Delivery option. */
  instantEnabled?: boolean;
  /** Whether Instant Delivery is currently selected. */
  instantSelected?: boolean;
  onSelectInstant?: () => void;
  /**
   * Admin-editable Instant Delivery ETA bounds (minutes) from `store_settings`.
   * The subtitle is derived from these — there is no hard-coded ETA in the JSX.
   */
  etaMin?: number;
  etaMax?: number;
  className?: string;
}) {
  const days = React.useMemo(() => buildTwoDaySlots(slots), [slots]);
  const instantEtaLabel = React.useMemo(
    () => formatInstantDeliveryEta(etaMin, etaMax),
    [etaMin, etaMax],
  );

  const radioValue = instantSelected
    ? INSTANT_DELIVERY_VALUE
    : (selectedId ?? undefined);

  return (
    <RadioGroup
      value={radioValue}
      onValueChange={(value) => {
        if (value === INSTANT_DELIVERY_VALUE) {
          onSelectInstant?.();
          return;
        }
        const found = slots.find((s) => s.id === value);
        if (found) onSelect(found);
      }}
      className={cn("flex flex-col gap-4", className)}
      aria-label="Delivery option"
    >
      {instantEnabled ? (
        <label
          htmlFor="slot-instant"
          className={cn(
            "flex cursor-pointer items-center gap-3 rounded-md border-[1.5px] px-3 py-2.5 transition-colors",
            instantSelected
              ? "border-leaf-600 bg-leaf-50"
              : "border-line bg-surface hover:border-line-strong",
          )}
        >
          <RadioGroupItem value={INSTANT_DELIVERY_VALUE} id="slot-instant" />
          <Zap className="size-4 shrink-0 text-leaf-600" aria-hidden="true" />
          <span className="min-w-0 flex-1">
            <span className="block text-body font-medium text-ink">
              Instant Delivery
            </span>
            <span className="mt-0.5 block text-small text-ink-subtle">
              {instantEtaLabel}
            </span>
          </span>
        </label>
      ) : null}

      {days.map((day) => (
        <div key={day.dayKey} className="flex flex-col gap-2">
          <p className="text-caption text-ink-subtle">{day.label}</p>

          {day.slots.length === 0 ? (
            <div className="flex items-start gap-2 rounded-md border border-line bg-surface-muted px-3 py-2.5 text-small text-ink-muted">
              <CalendarClock
                className="mt-0.5 size-4 shrink-0"
                aria-hidden="true"
              />
              <span>No slots yet for this day.</span>
            </div>
          ) : (
            <div className="grid gap-2 sm:grid-cols-2">
              {day.slots.map((slot) => {
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
          )}
        </div>
      ))}
    </RadioGroup>
  );
}
