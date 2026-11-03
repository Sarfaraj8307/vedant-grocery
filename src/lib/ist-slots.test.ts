import { describe, expect, it } from "vitest";

import { istDayKey, istDayOffset } from "@/lib/format";
import { buildThreeDaySlots } from "@/lib/ist-slots";
import type { DeliverySlotRow } from "@/types/database";

/**
 * The rolling 3-day IST delivery horizon is the core of the checkout picker, so
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

describe("buildThreeDaySlots", () => {
  it("always returns exactly the three IST days: today, tomorrow, day-after", () => {
    const days = buildThreeDaySlots([], NOW);
    expect(days).toHaveLength(3);
    expect(days.map((d) => d.offset)).toEqual([0, 1, 2]);
  });

  it("labels the days Today / Tomorrow / weekday (day-after is never Today/Tomorrow)", () => {
    const today = slot(1, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");
    const tomorrow = slot(2, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");
    const dayAfter = slot(3, "2026-11-06T02:30:00Z", "2026-11-06T04:30:00Z");

    const days = buildThreeDaySlots([today, tomorrow, dayAfter], NOW);
    expect(days[0]!.label).toBe("Today");
    expect(days[1]!.label).toBe("Tomorrow");
    // 2026-11-06 is a Friday, so the day-after shows a weekday, not Today/Tomorrow.
    expect(days[2]!.label).not.toBe("Today");
    expect(days[2]!.label).not.toBe("Tomorrow");
    expect(days[2]!.label).toContain("Nov");
  });

  it("groups slots under the correct IST day", () => {
    const todayMorning = slot(1, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");
    const todayEvening = slot(2, "2026-11-04T11:30:00Z", "2026-11-04T13:30:00Z");
    const tomorrow = slot(3, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");
    const dayAfter = slot(4, "2026-11-06T02:30:00Z", "2026-11-06T04:30:00Z");

    const days = buildThreeDaySlots(
      [todayMorning, tomorrow, dayAfter, todayEvening],
      NOW,
    );
    expect(days[0]!.slots).toHaveLength(2);
    expect(days[1]!.slots).toHaveLength(1);
    expect(days[2]!.slots).toHaveLength(1);
  });

  it("drops slots outside the 3-day window (past and beyond day-after)", () => {
    const past = slot(5, "2026-11-03T02:30:00Z", "2026-11-03T04:30:00Z");
    const beyond = slot(6, "2026-11-07T02:30:00Z", "2026-11-07T04:30:00Z");
    const today = slot(7, "2026-11-04T02:30:00Z", "2026-11-04T04:30:00Z");

    const days = buildThreeDaySlots([past, beyond, today], NOW);
    expect(days[0]!.slots).toHaveLength(1);
    expect(days[1]!.slots).toHaveLength(0);
    expect(days[2]!.slots).toHaveLength(0);
    // The past/beyond slots must never appear at all.
    const allSlots = days.flatMap((d) => d.slots);
    expect(allSlots.map((s) => s.id)).toEqual([today.id]);
  });

  it("renders a day with no slots as empty (the 'no slots yet' case)", () => {
    const tomorrow = slot(8, "2026-11-05T02:30:00Z", "2026-11-05T04:30:00Z");
    const days = buildThreeDaySlots([tomorrow], NOW);
    expect(days[0]!.slots).toHaveLength(0);
    expect(days[1]!.slots).toHaveLength(1);
    expect(days[2]!.slots).toHaveLength(0);
  });
});
