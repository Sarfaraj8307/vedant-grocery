/**
 * Geocoding + Pune coordinate sanity — the pure, framework-free half of GPS
 * address capture.
 *
 * WHY A PURE MODULE
 * `src/components/address/location-picker.tsx` is a client component and this
 * file is imported by it, so nothing here may touch the network. The network
 * half lives in `reverseGeocode()` at the bottom of this file, which is
 * **server-side only** — import it from Server Actions / Route Handlers only,
 * never from a client component (it sends a custom `User-Agent`, which browsers
 * forbid).
 *
 * WHY NOMINATIM
 * OpenStreetMap's Nominatim is free and needs no credential, so there is no API
 * key to leak. It comes with obligations (see `NOMINATIM_USER_AGENT` and the
 * rate note on `reverseGeocode`) — violating them gets us blocked, so the usage
 * policy is enforced in code, not in prose.
 */

/** One reverse-geocoded address, flattened into the shape our form wants. */
export type GeocodedAddress = {
  house: string | null;
  street: string | null;
  area: string | null;
  city: string | null;
  district: string | null;
  state: string | null;
  pincode: string | null;
};

/**
 * Pune's bounding box — the coordinate-based serviceability check (§15).
 *
 * Deliberately generous (city + Pimpri-Chinchwad) because it is a *sanity*
 * check on a device fix, not the authority: the authoritative check is still
 * `src/lib/serviceability.ts` on the address the customer confirms. A future
 * city is a config change here, not a rewrite.
 */
export const PUNE_BOUNDS = {
  lat: { min: 18.40, max: 18.65 },
  lng: { min: 73.70, max: 74.00 },
} as const;

/**
 * Is this a coordinate a device could plausibly have produced?
 *
 * Rejects `NaN`, `Infinity`, out-of-range values, and anything that is not a
 * finite number at all — so a tampered or corrupt fix never reaches the
 * network or the database. This is the gate that must run **before** Nominatim.
 */
export function isPlausibleCoordinate(lat: number, lng: number): boolean {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return false;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
}

/** Is this coordinate inside Pune? Implies `isPlausibleCoordinate`. */
export function isWithinPune(lat: number, lng: number): boolean {
  if (!isPlausibleCoordinate(lat, lng)) return false;
  return (
    lat >= PUNE_BOUNDS.lat.min &&
    lat <= PUNE_BOUNDS.lat.max &&
    lng >= PUNE_BOUNDS.lng.min &&
    lng <= PUNE_BOUNDS.lng.max
  );
}

/** Trim a value to `null` when it is absent or empty — never a fake string. */
function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

/**
 * A pincode is digits only, so anything else becomes `null`.
 *
 * Whitespace is tolerated (Nominatim emits `"411 001"` for Indian pincodes), but
 * **any other character is a data error and is rejected rather than stripped**:
 * silently truncating `"41100A"` to `"41100"` would hand the customer a
 * plausible-looking, wrong pincode they cannot notice. Returning `null` leaves
 * the field blank for them to correct.
 */
export function normalisePincode(value: unknown): string | null {
  const raw = text(value);
  if (!raw) return null;
  if (!/^\d[\d\s]*$/.test(raw)) return null;
  const digits = raw.replace(/\D/g, "");
  return digits.length >= 5 && digits.length <= 10 ? digits : null;
}

/**
 * Flatten Nominatim's `address` object into `GeocodedAddress`.
 *
 * Nominatim returns a *variable* shape depending on how precise the fix is: a
 * rooftop fix may carry `house_number`, a street-centroid fix will not, and a
 * village fix uses `village` rather than `city`. Each field is resolved through
 * a documented fallback chain and anything absent becomes `null` — **nothing is
 * ever invented**, so a partial fix leaves the field blank for the customer to
 * type rather than showing a plausible-looking guess.
 */
export function mapNominatimAddress(
  address: Record<string, unknown> | null | undefined,
): GeocodedAddress {
  const a = address ?? {};
  return {
    house: text(a.house_number),
    street: text(a.road),
    // Neighbourhood-level detail, under a few different keys Nominatim uses.
    area:
      text(a.suburb) ?? text(a.neighbourhood) ?? text(a.quarter) ?? null,
    city: text(a.city) ?? text(a.town) ?? text(a.village) ?? null,
    // `state_district` is the district inside a state, `county` the next level up.
    district: text(a.state_district) ?? text(a.county) ?? null,
    state: text(a.state),
    pincode: normalisePincode(a.postcode),
  };
}

/* -------------------------------------------------------------------------- */
/* Server-side network half                                                   */
/* -------------------------------------------------------------------------- */

/**
 * Nominatim requires a real identifying User-Agent; requests without one are
 * blocked outright. Using a project-specific constant (rather than an empty
 * string or the library default) keeps us compliant and lets us be contacted
 * if our traffic is a problem.
 */
export const NOMINATIM_USER_AGENT = "VedantGrocery/1.0 (store-owner@example.com)";

/** Nominatim latencies are low; do not hang a click for more than this. */
const NOMINATIM_TIMEOUT_MS = 8_000;

/**
 * Reverse-geocode a coordinate via Nominatim (OpenStreetMap).
 *
 * SERVER-SIDE ONLY. Usage policy compliance is the caller's job:
 *   * one request per explicit user action — never on a keystroke or a timer;
 *   * no bulk geocoding.
 * Our flow fires exactly once per "Use my current location" press, so both hold.
 *
 * Returns `null` on any failure (network, non-2xx, timeout, malformed body) —
 * never a hallucinated address. The caller turns `null` into a customer-safe
 * error message, so no upstream text or stack trace can reach the browser.
 */
export async function reverseGeocode(
  lat: number,
  lng: number,
): Promise<GeocodedAddress | null> {
  // Validate before the network: a malformed or extreme coordinate must never
  // leave our process.
  if (!isPlausibleCoordinate(lat, lng)) return null;

  const url =
    "https://nominatim.openstreetmap.gov/reverse" +
    `?format=jsonv2&lat=${encodeURIComponent(lat)}&lon=${encodeURIComponent(lng)}`;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), NOMINATIM_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      // `User-Agent` is a forbidden header name in browsers but fine in Node,
      // which is exactly why this function must not run client-side.
      headers: {
        "User-Agent": NOMINATIM_USER_AGENT,
        Accept: "application/json",
        "Accept-Language": "en",
      },
      signal: controller.signal,
    });

    if (!response.ok) return null;

    const json = (await response.json()) as {
      address?: Record<string, unknown> | null;
    };

    if (!json.address) return null;
    return mapNominatimAddress(json.address);
  } catch {
    // AbortError (timeout) and TypeError (network) both land here.
    return null;
  } finally {
    clearTimeout(timer);
  }
}
