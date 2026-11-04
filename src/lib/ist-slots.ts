import type { DeliverySlotRow } from "@/types/database";
import {
  istDayKey,
  relativeDayIST,
  shiftDayKey,
} from "@/lib/format";

/**
 * Pure helpers for the rolling 2-day IST delivery-slot horizon. Keeping the
 * grouping/labelling logic here (rather than inside the JSX) makes it unit
 * testable and guarantees the client and server agree on what "today /
 * tomorrow" means for the store's timezone.
 */

export type DaySlots = {
  /** IST YYYY-MM-DD key, e.g. "2026-11-04". */
  dayKey: string;
  /** 0 = today, 1 = tomorrow. */
  offset: number;
  /** Display label ("Today" / "Tomorrow"). */
  label: string;
  /** The slots on this day, ordered by starts_at asc. */
  slots: DeliverySlotRow[];
};

/** Only today and tomorrow (IST) are bookable. */
export const HORIZON_DAYS = 2;

/**
 * Build the rolling 2-day IST horizon as a fixed array of two day groups
 * (today and tomorrow).
 *
 * Every day is always present, so the picker can render "no slots yet" for a
 * day that has no slots instead of hiding it. Slots that fall outside the
 * 2-day window (past days or the day-after and beyond) are ignored.
 */
export function buildTwoDaySlots(
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
