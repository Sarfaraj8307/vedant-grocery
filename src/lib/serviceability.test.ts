import { describe, expect, it } from "vitest";

import { DEFAULT_CITY, DEFAULT_STATE } from "@/config/service-area";
import {
  checkServiceability,
  normaliseCity,
  normalisePincode,
} from "@/lib/serviceability";

/**
 * Serviceability is the gate in front of checkout, so its rules are pinned here.
 * These cases describe the *business* rule (Pune, Maharashtra), not the
 * implementation: if the rule changes, one of these should fail.
 */

describe("normaliseCity", () => {
  it("trims, lowercases and collapses whitespace", () => {
    expect(normaliseCity("  Pune  ")).toBe("pune");
    expect(normaliseCity("PIMRI  CHINCHWAD")).toBe("pimri chinchwad");
  });

  it("treats null/undefined as empty", () => {
    expect(normaliseCity(null)).toBe("");
    expect(normaliseCity(undefined)).toBe("");
  });
});

describe("normalisePincode", () => {
  it("keeps digits only", () => {
    expect(normalisePincode("411 001")).toBe("411001");
    expect(normalisePincode("411001")).toBe("411001");
  });
});

describe("checkServiceability", () => {
  it("is configured for Pune, Maharashtra", () => {
    expect(DEFAULT_CITY).toBe("Pune");
    expect(DEFAULT_STATE).toBe("Maharashtra");
  });

  it("accepts a Pune address with a Pune pincode", () => {
    const verdict = checkServiceability({ city: "Pune", postalCode: "411001" });
    expect(verdict.serviceable).toBe(true);
    if (verdict.serviceable) {
      expect(verdict.matchedBy).toBe("city");
      expect(verdict.areaLabel).toBe("Pune, Maharashtra");
    }
  });

  it("is case- and whitespace-insensitive about the city", () => {
    expect(checkServiceability({ city: "  PUNE ", postalCode: "411038" }).serviceable).toBe(
      true,
    );
    expect(checkServiceability({ city: "pune", postalCode: "411038" }).serviceable).toBe(
      true,
    );
  });

  it("accepts a known alias (Pimpri-Chinchwad)", () => {
    expect(
      checkServiceability({ city: "Pimpri-Chinchwad", postalCode: "411017" })
        .serviceable,
    ).toBe(true);
  });

  it("rejects an address in another city", () => {
    const verdict = checkServiceability({ city: "Mumbai", postalCode: "400001" });
    expect(verdict.serviceable).toBe(false);
    if (!verdict.serviceable) {
      expect(verdict.code).toBe("OUT_OF_AREA");
      expect(verdict.reason).toContain("Pune");
    }
  });

  it("rejects a pincode outside the served area even when the city matches", () => {
    // A wrong pincode inside a served city means the wrong delivery zone.
    const verdict = checkServiceability({ city: "Pune", postalCode: "400001" });
    expect(verdict.serviceable).toBe(false);
    if (!verdict.serviceable) {
      expect(verdict.code).toBe("OUT_OF_AREA");
      expect(verdict.reason).toContain("400001");
    }
  });

  it("resolves by pincode alone when the city is blank", () => {
    const verdict = checkServiceability({ city: "", postalCode: "411014" });
    expect(verdict.serviceable).toBe(true);
    if (verdict.serviceable) {
      expect(verdict.matchedBy).toBe("pincode");
    }
  });

  it("asks for a city when there is nothing to match on", () => {
    const verdict = checkServiceability({ city: "", postalCode: "" });
    expect(verdict.serviceable).toBe(false);
    if (!verdict.serviceable) {
      expect(verdict.code).toBe("MISSING_CITY");
    }
  });

  it("does not throw on a null address", () => {
    expect(checkServiceability({ city: null, state: null, postalCode: null })).toMatchObject(
      { serviceable: false, code: "MISSING_CITY" },
    );
  });
});
