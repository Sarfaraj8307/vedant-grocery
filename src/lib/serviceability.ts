import {
  serviceAreas,
  serviceAreaLabels,
  type ServiceArea,
} from "@/config/service-area";

/**
 * Serviceability — "do we deliver to this address?".
 *
 * A pure function of an address's *city / state / pincode* and the configured
 * `serviceAreas`. It has no I/O, so it runs unchanged in a Server Component, a
 * Server Action and a unit test — the same property `lib/pricing.ts` relies on.
 *
 * The rule is intentionally forgiving about *how* an area is matched (city name
 * OR pincode) but strict about *whether* it matches: a wrong pincode inside a
 * served city is rejected, because a wrong pincode means the wrong delivery
 * zone. See `docs/` and `config/service-area.ts` for the business context.
 */

export type ServiceabilityInput = {
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
};

export type ServiceabilityVerdict =
  | {
      serviceable: true;
      areaId: string;
      areaLabel: string;
      /** Which field proved it — useful for copy and for debugging. */
      matchedBy: "city" | "pincode";
    }
  | {
      serviceable: false;
      code: "MISSING_CITY" | "OUT_OF_AREA";
      /** Customer-facing explanation. Never contains database text. */
      reason: string;
    };

/** Lowercase, trim and collapse whitespace so " Pune " === "pune". */
export function normaliseCity(value: string | null | undefined): string {
  return (value ?? "").trim().toLowerCase().replace(/\s+/g, " ");
}

/** Digits only — a pincode with spaces or a leading `-` still resolves. */
export function normalisePincode(value: string | null | undefined): string {
  return (value ?? "").replace(/\D/g, "");
}

function coversPincode(area: ServiceArea, pincode: string): boolean {
  if (area.pincodePrefixes.length === 0) return true;
  return area.pincodePrefixes.some((prefix) => pincode.startsWith(prefix));
}

function findAreaByCity(city: string): ServiceArea | undefined {
  if (!city) return undefined;
  return serviceAreas.find((area) => area.cityAliases.includes(city));
}

function findAreaByPincode(pincode: string): ServiceArea | undefined {
  if (!pincode) return undefined;
  return serviceAreas.find((area) => coversPincode(area, pincode));
}

/**
 * Decide whether an address is inside our delivery area.
 *
 * Order of evidence:
 *   1. a recognised city name — then the pincode (when present) must also be in
 *      that area, otherwise the address points outside the served zone;
 *   2. failing a city match, a pincode inside a served area still resolves (a
 *      customer may leave the city blank or spell it differently);
 *   3. otherwise, not serviceable.
 */
export function checkServiceability(
  input: ServiceabilityInput,
): ServiceabilityVerdict {
  const city = normaliseCity(input.city);
  const pincode = normalisePincode(input.postalCode);

  const byCity = findAreaByCity(city);
  if (byCity) {
    if (pincode && !coversPincode(byCity, pincode)) {
      return {
        serviceable: false,
        code: "OUT_OF_AREA",
        reason: `We do not deliver to pincode ${pincode} yet.`,
      };
    }
    return {
      serviceable: true,
      areaId: byCity.id,
      areaLabel: byCity.label,
      matchedBy: "city",
    };
  }

  const byPincode = findAreaByPincode(pincode);
  if (byPincode) {
    return {
      serviceable: true,
      areaId: byPincode.id,
      areaLabel: byPincode.label,
      matchedBy: "pincode",
    };
  }

  if (!city) {
    return {
      serviceable: false,
      code: "MISSING_CITY",
      reason: "Add a city to your address so we can check delivery.",
    };
  }

  return {
    serviceable: false,
    code: "OUT_OF_AREA",
    reason: `We deliver in ${serviceAreaLabels.join(" and ")} only.`,
  };
}
