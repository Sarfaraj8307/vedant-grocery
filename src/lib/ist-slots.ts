import type { DeliverySlotRow } from "@/types/database";
import {
  istDayKey,
  relativeDayIST,
  shiftDayKey,
} from "@/lib/format";

/**
 * Pure helpers for the rolling 3-day IST delivery-slot horizon. Keeping the
 * grouping/labelling logic here (rather than inside the JSX) makes it unit
 * testable and guarantees the client and server agree on what "today /
 * tomorrow / day-after" means for the store's timezone.
 */

export type DaySlots = {
  /** IST YYYY-MM-DD key, e.g. "2026-11-04". */
  dayKey: string;
  /** 0 = today, 1 = tomorrow, 2 = day-after. */
  offset: number;
  /** Display label ("Today" / "Tomorrow" / weekday). */
  label: string;
  /** The slots on this day, ordered by starts_at asc. */
  slots: DeliverySlotRow[];
};

const HORIZON_DAYS = 3;

/**
 * Build the rolling 3-day IST horizon as a fixed array of three day groups.
 *
 * Every day is always present, so the picker can render "no slots yet" for a
 * day that has no slots instead of hiding it. Slots that fall outside the
 * 3-day window (past days or beyond the day-after) are ignored.
 */
export function buildThreeDaySlots(
  slots: DeliverySlotRow[],
  now: Date = new Date(),
): DaySlots[] {
  const todayKey = istDayKey(now);

  const grouped = new Map<string, DeliverySlotRow[]>();
  for (const slot of slots) {
    const key = istDayKey(slot.starts_at);
    const list = grouped.get(key);
    if (list) list.push(slot);
    else grouped.set(key, [slot]);
  }

  const days: DaySlots[] = [];
  for (let offset = 0; offset < HORIZON_DAYS; offset++) {
    const dayKey = shiftDayKey(todayKey, offset);
    days.push({
      dayKey,
      offset,
      label: relativeDayIST(dayKey, now),
      slots: grouped.get(dayKey) ?? [],
    });
  }
  return days;
}
