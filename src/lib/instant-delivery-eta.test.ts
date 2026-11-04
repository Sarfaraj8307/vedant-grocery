import { describe, expect, it } from "vitest";

import {
  formatInstantDeliveryEta,
  normaliseInstantDeliveryEta,
} from "@/lib/instant-delivery";

/**
 * The Instant Delivery ETA is admin-editable (store_settings), so the website
 * must never trust the raw values. These tests pin down the normalisation and
 * the customer-facing label formatting.
 */
describe("normaliseInstantDeliveryEta", () => {
  it("keeps a valid range unchanged", () => {
    expect(normaliseInstantDeliveryEta(20, 60)).toEqual({ min: 20, max: 60 });
    expect(normaliseInstantDeliveryEta(30, 45)).toEqual({ min: 30, max: 45 });
  });

  it("accepts the inclusive 1..1440 boundaries", () => {
    expect(normaliseInstantDeliveryEta(1, 1)).toEqual({ min: 1, max: 1 });
    expect(normaliseInstantDeliveryEta(1, 1440)).toEqual({
      min: 1,
      max: 1440,
    });
  });

  it("falls back to 30/45 when min > max", () => {
    expect(normaliseInstantDeliveryEta(90, 30)).toEqual({ min: 30, max: 45 });
  });

  it("falls back to 30/45 for out-of-range values", () => {
    expect(normaliseInstantDeliveryEta(0, 45)).toEqual({ min: 30, max: 45 });
    expect(normaliseInstantDeliveryEta(30, 1441)).toEqual({ min: 30, max: 45 });
    expect(normaliseInstantDeliveryEta(-5, 45)).toEqual({ min: 30, max: 45 });
    expect(normaliseInstantDeliveryEta(2000, 3000)).toEqual({
      min: 30,
      max: 45,
    });
  });

  it("falls back to 30/45 for null / undefined / non-integer / NaN", () => {
    expect(normaliseInstantDeliveryEta(null, null)).toEqual({
      min: 30,
      max: 45,
    });
    expect(normaliseInstantDeliveryEta(undefined, undefined)).toEqual({
      min: 30,
      max: 45,
    });
    expect(normaliseInstantDeliveryEta(30.5, 45)).toEqual({
      min: 30,
      max: 45,
    });
    expect(normaliseInstantDeliveryEta(Number.NaN, 45)).toEqual({
      min: 30,
      max: 45,
    });
    expect(normaliseInstantDeliveryEta(30, Number.POSITIVE_INFINITY)).toEqual({
      min: 30,
      max: 45,
    });
  });
});

describe("formatInstantDeliveryEta", () => {
  it("formats the default range with an en-dash", () => {
    expect(formatInstantDeliveryEta(30, 45)).toBe("Delivered in 30–45 mins");
  });

  it("uses admin-provided values rather than a hard-coded range", () => {
    expect(formatInstantDeliveryEta(15, 25)).toBe("Delivered in 15–25 mins");
    expect(formatInstantDeliveryEta(60, 90)).toBe("Delivered in 60–90 mins");
  });

  it("collapses equal bounds into a single value", () => {
    expect(formatInstantDeliveryEta(30, 30)).toBe("Delivered in 30 mins");
  });

  it("normalises invalid input before formatting", () => {
    expect(formatInstantDeliveryEta(90, 10)).toBe("Delivered in 30–45 mins");
    expect(formatInstantDeliveryEta(0, 2000)).toBe("Delivered in 30–45 mins");
  });
});
