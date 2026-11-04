import { describe, expect, it } from "vitest";

import { istDayKey, istDayOffset } from "@/lib/format";
import { buildTwoDaySlots, HORIZON_DAYS } from "@/lib/ist-slots";
import type { DeliverySlotRow } from "@/types/database";

/**
 * The rolling 2-day IST delivery horizon is the core of the checkout picker, so
 * these tests pin it down. The timezone matters: a slot is grouped by its IST
 * calendar day, not by the browser's timezone or the server's UTC clock.
 */

function slot(id: number, startsAt: string, endsAt: string): DeliverySlotRow {
  return {
    id: `00000000-0000-0000-0000-${String(id).padStart(12, "0")}`,
    starts_at: startsAt,
    ends_at: endsAt,
    capacity: 25,
    is_active: true,
    created_at: startsAt,
    updated_at: startsAt,
  };
}

// A fixed "now": 2026-11-04 10:00 UTC = 2026-11-04 15:30 IST.
const NOW = new Date("2026-11-04T10:00:00Z");

describe("istDayKey / istDayOffset", () => {
  it("resolves the IST calendar day across the IST midnight boundary", () => {
    // 2026-11-04 18:30 UTC == 2026-11-05 00:00 IST
    expect(istDayKey("2026-11-04T18:30:00Z")).toBe("2026-11-05");
    // 2026-11-04 18:00 UTC == 2026-11-04 23:30 IST
    expect(istDayKey("2026-11-04T18:00:00Z")).toBe("2026-11-04");
  });

  it("computes whole-day offsets in IST", () => {
    expect(istDayOffset("2026-11-04T02:30:00Z", NOW)).toBe(0);
    expect(istDayOffset("2026-11-05T02:30:00Z", NOW)).toBe(1);
    expect(istDayOffset("2026-11-06T02:30:00Z", NOW)).toBe(2);
    expect(istDayOffset("2026-11-03T02:30:00Z", NOW)).toBe(-1);
  });
});

describe("buildTwoDaySlots", () => {
  it("has a 2-day horizon constant", () => {
    expect(HORIZON_DAYS).toBe(2);
  });

  it("always returns exactly the two IST days: today and tomorrow", () => {
    const days = buildTwoDaySlots([], NOW);
    expect(days).toHaveLength(2);
    expect(days.map((d) => d.offset)).toEqual([0, 1]);
    expect(days.map((d) => d.dayKey)).toEqual(["2026-11-04", "2026-11-05"]);
  });

  it("labels the days Today / Tomorrow", () => {
    const today = slot(1, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");
    const tomorrow = slot(2, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");

    const days = buildTwoDaySlots([today, tomorrow], NOW);
    expect(days[0]!.label).toBe("Today");
    expect(days[1]!.label).toBe("Tomorrow");
  });

  it("groups slots under the correct IST day", () => {
    const todayMorning = slot(1, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");
    const todayEvening = slot(2, "2026-11-04T11:30:00Z", "2026-11-04T13:30:00Z");
    const tomorrow = slot(3, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");

    const days = buildTwoDaySlots(
      [todayMorning, tomorrow, todayEvening],
      NOW,
    );
    expect(days[0]!.slots).toHaveLength(2);
    expect(days[1]!.slots).toHaveLength(1);
  });

  it("drops slots outside the 2-day window (past, day-after and beyond)", () => {
    const past = slot(5, "2026-11-03T02:30:00Z", "2026-11-03T04:30:00Z");
    const dayAfter = slot(6, "2026-11-06T02:30:00Z", "2026-11-06T04:30:00Z");
    const beyond = slot(9, "2026-11-07T02:30:00Z", "2026-11-07T04:30:00Z");
    const today = slot(7, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");

    const days = buildTwoDaySlots([past, dayAfter, beyond, today], NOW);
    expect(days[0]!.slots).toHaveLength(1);
    expect(days[1]!.slots).toHaveLength(0);
    // The out-of-window slots must never appear at all.
    const allSlots = days.flatMap((d) => d.slots);
    expect(allSlots.map((s) => s.id)).toEqual([today.id]);
  });

  it("excludes the day-after (offset 2) slot entirely", () => {
    const dayAfter = slot(3, "2026-11-06T02:30:00Z", "2026-11-06T04:30:00Z");
    const days = buildTwoDaySlots([dayAfter], NOW);
    expect(days).toHaveLength(2);
    expect(days.flatMap((d) => d.slots)).toHaveLength(0);
  });

  it("renders a day with no slots as empty (the 'no slots yet' case)", () => {
    const tomorrow = slot(8, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");
    const days = buildTwoDaySlots([tomorrow], NOW);
    expect(days[0]!.slots).toHaveLength(0);
    expect(days[1]!.slots).toHaveLength(1);
  });
});
