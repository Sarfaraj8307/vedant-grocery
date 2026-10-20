/**
 * Service-area configuration — the single source of truth for **where we deliver**.
 *
 * WHY THIS EXISTS
 * Vedant is a single-location Pune shop. Before a customer can check out we must
 * be able to answer "do we deliver to this address?" from one place that can
 * grow to other cities without touching the UI. This module is that rule at the
 * *application* layer.
 *
 * RESOLUTION ORDER (the same pattern as `config/store.ts`):
 *   1. `NEXT_PUBLIC_SERVICE_AREA_*` environment variables (deploy-time override)
 *   2. the defaults declared here (Pune, Maharashtra)
 *
 * HONESTY RULE
 * `pincodePrefixes` defaults to Pune's `411` only — we do **not** advertise
 * coverage we cannot serve. Expanding is adding a prefix here or a new
 * `ServiceArea` to `serviceAreas`; nothing else in the app changes.
 *
 * ENFORCEMENT
 * The authoritative check is `verifyAddressServiceability()` /
 * `submitOrder()` in `src/app/checkout/actions.ts`: a Server Action that
 * re-reads the address from the database with the customer's own session, so a
 * tampered client cannot fake a city or pincode. A database-level constraint
 * would be the strongest form, but a schema change is intentionally out of scope
 * for this task (see docs/SUPABASE_FINDING_REGISTER.md).
 */

export type ServiceArea = {
  readonly id: string;
  /** Human-readable, used in copy: "Pune, Maharashtra". */
  readonly label: string;
  readonly city: string;
  readonly state: string;
  readonly country: string;
  /**
   * Normalised (trimmed, lowercased) city names that resolve to this area.
   * Must include `normaliseCity(area.city)` so the canonical spelling matches.
   */
  readonly cityAliases: readonly string[];
  /** Pincode prefixes this area covers. Empty ⇒ the whole city is covered. */
  readonly pincodePrefixes: readonly string[];
};

/** City a brand-new address is pre-filled with (see `AddressForm`). */
export const DEFAULT_CITY =
  process.env.NEXT_PUBLIC_SERVICE_AREA_CITY?.trim() || "Pune";

/** State a brand-new address is pre-filled with. */
export const DEFAULT_STATE =
  process.env.NEXT_PUBLIC_SERVICE_AREA_STATE?.trim() || "Maharashtra";

export const DEFAULT_COUNTRY = "India";

function list(raw: string | undefined): string[] {
  return (raw ?? "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);
}

/**
 * Pune pincodes default to the `411` block (Pune city + Pimpri-Chinchwad). The
 * owner can widen this at deploy time without a code change.
 */
const pincodePrefixes: readonly string[] = (() => {
  const configured = list(process.env.NEXT_PUBLIC_SERVICE_AREA_PINCODE_PREFIXES)
    .map((prefix) => prefix.replace(/\D/g, ""))
    .filter(Boolean);
  return configured.length > 0 ? configured : ["411"];
})();

export const PUNE_SERVICE_AREA: ServiceArea = {
  id: "pune",
  label: `${DEFAULT_CITY}, ${DEFAULT_STATE}`,
  city: DEFAULT_CITY,
  state: DEFAULT_STATE,
  country: DEFAULT_COUNTRY,
  // The canonical city plus the spellings customers actually type.
  cityAliases: [
    "pune",
    "poona",
    "pimpri",
    "pimpri chinchwad",
    "pimpri-chinchwad",
    "chinchwad",
    "pcmc",
  ],
  pincodePrefixes,
};

/** Every area we currently deliver to. Add a `ServiceArea` to expand. */
export const serviceAreas: readonly ServiceArea[] = [PUNE_SERVICE_AREA];

/** Human-readable area list for customer copy, e.g. ["Pune, Maharashtra"]. */
export const serviceAreaLabels: readonly string[] = serviceAreas.map(
  (area) => area.label,
);
