import { describe, expect, it } from "vitest";

import {
  PUNE_BOUNDS,
  isPlausibleCoordinate,
  isWithinPune,
  mapNominatimAddress,
  normalisePincode,
} from "@/lib/geocode";

/**
 * Coordinate validation is the security boundary for the GPS flow (§15): a
 * malformed or extreme coordinate must be rejected **before** it reaches the
 * geocoding network call, and a genuine Pune fix must be recognised so the
 * customer is told the truth about whether we deliver there.
 */
describe("isPlausibleCoordinate", () => {
  it("accepts a real Pune fix", () => {
    expect(isPlausibleCoordinate(18.5204, 73.8567)).toBe(true);
  });

  it("accepts the extremes of the valid range", () => {
    expect(isPlausibleCoordinate(-90, -180)).toBe(true);
    expect(isPlausibleCoordinate(90, 180)).toBe(true);
    expect(isPlausibleCoordinate(0, 0)).toBe(true);
  });

  it("rejects out-of-range latitude", () => {
    expect(isPlausibleCoordinate(91, 73)).toBe(false);
    expect(isPlausibleCoordinate(-90.0001, 73)).toBe(false);
  });

  it("rejects out-of-range longitude", () => {
    expect(isPlausibleCoordinate(18, 181)).toBe(false);
    expect(isPlausibleCoordinate(18, -181)).toBe(false);
  });

  it("rejects non-finite values", () => {
    expect(isPlausibleCoordinate(Number.NaN, 73)).toBe(false);
    expect(isPlausibleCoordinate(18, Number.POSITIVE_INFINITY)).toBe(false);
    expect(isPlausibleCoordinate(Number.NEGATIVE_INFINITY, 73)).toBe(false);
  });
});

describe("isWithinPune", () => {
  it("accepts fixes inside the bounding box", () => {
    // Koregaon Park, and both edges of the declared box.
    expect(isWithinPune(18.5362, 73.8939)).toBe(true);
    expect(isWithinPune(PUNE_BOUNDS.lat.min, PUNE_BOUNDS.lng.min)).toBe(true);
    expect(isWithinPune(PUNE_BOUNDS.lat.max, PUNE_BOUNDS.lng.max)).toBe(true);
  });

  it("rejects the other Indian cities we do not serve", () => {
    expect(isWithinPune(19.076, 72.8777)).toBe(false); // Mumbai
    expect(isWithinPune(28.6139, 77.209)).toBe(false); // Delhi
    expect(isWithinPune(12.9716, 77.5946)).toBe(false); // Bengaluru
  });

  it("rejects a plausible coordinate that is simply elsewhere", () => {
    // Valid range, so it survives `isPlausibleCoordinate`, but outside Pune.
    expect(isPlausibleCoordinate(51.5074, -0.1278)).toBe(true);
    expect(isWithinPune(51.5074, -0.1278)).toBe(false);
  });
});

describe("normalisePincode", () => {
  it("keeps a clean pincode", () => {
    expect(normalisePincode("411001")).toBe("411001");
  });

  it("strips the spaces Nominatim puts in some Indian pincodes", () => {
    expect(normalisePincode("411 001")).toBe("411001");
  });

  it("returns null for anything that is not 5–10 digits", () => {
    expect(normalisePincode("4110")).toBeNull();
    expect(normalisePincode("")).toBeNull();
    expect(normalisePincode("41100A")).toBeNull();
    expect(normalisePincode(null)).toBeNull();
    expect(normalisePincode(undefined)).toBeNull();
    expect(normalisePincode("411001234567")).toBeNull(); // 12 digits
  });
});

describe("mapNominatimAddress", () => {
  it("maps a fully-populated response", () => {
    const mapped = mapNominatimAddress({
      house_number: "12",
      road: "Koregaon Park Lane 5",
      suburb: "Koregaon Park",
      city: "Pune",
      state_district: "Pune district",
      state: "Maharashtra",
      postcode: "411001",
    });
    expect(mapped).toEqual({
      house: "12",
      street: "Koregaon Park Lane 5",
      area: "Koregaon Park",
      city: "Pune",
      district: "Pune district",
      state: "Maharashtra",
      pincode: "411001",
    });
  });

  it("degrades to null for missing keys rather than undefined or a crash", () => {
    const mapped = mapNominatimAddress({ road: "MG Road", city: "Pune" });
    expect(mapped.house).toBeNull();
    expect(mapped.street).toBe("MG Road");
    expect(mapped.city).toBe("Pune");
    expect(mapped.pincode).toBeNull();
    expect(mapped.state).toBeNull();
    expect(mapped.district).toBeNull();
  });

  it("falls back through city aliases", () => {
    expect(mapNominatimAddress({ town: "Pimpri" }).city).toBe("Pimpri");
    expect(mapNominatimAddress({ village: "Uruli Kanchan" }).city).toBe("Uruli Kanchan");
  });

  it("falls back through area aliases", () => {
    expect(mapNominatimAddress({ neighbourhood: "Kothrud" }).area).toBe("Kothrud");
    expect(mapNominatimAddress({ quarter: "Hadapsar" }).area).toBe("Hadapsar");
  });

  it("tolerates a completely empty address object", () => {
    const mapped = mapNominatimAddress({});
    expect(Object.values(mapped).every((v) => v === null)).toBe(true);
  });

  it("tolerates null and undefined input", () => {
    expect(() => mapNominatimAddress(null)).not.toThrow();
    expect(() => mapNominatimAddress(undefined)).not.toThrow();
  });

  it("normalises a spaced pincode", () => {
    expect(mapNominatimAddress({ postcode: "411 001" }).pincode).toBe("411001");
  });
});
