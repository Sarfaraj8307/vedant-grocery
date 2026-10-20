"use server";

import { getSupabaseServerClient } from "@/lib/supabase/server";
import {
  isPlausibleCoordinate,
  reverseGeocode,
  type GeocodedAddress,
} from "@/lib/geocode";

/**
 * Account-area Server Actions.
 *
 * WHY A SERVER ACTION FOR GEOCODING
 * Reverse geocoding talks to a third party (Nominatim) and needs a custom
 * `User-Agent` header — a forbidden header name in browsers. Doing it in a
 * Server Action also keeps the outbound call one-directional: the browser sends
 * us a coordinate, and we send back a *structured* address, so upstream
 * response text can never reach the customer.
 *
 * The pattern mirrors `src/app/checkout/actions.ts`: `"use server"`,
 * `getSupabaseServerClient()` (the customer's own session — no privileged
 * client anywhere in this codebase), then re-validate on the server.
 */

export type ReverseGeocodeResult =
  | { ok: true; address: GeocodedAddress }
  | { ok: false; error: string };

/**
 * Turn a device coordinate into a structured address the form can prefill.
 *
 * Order of operations:
 *   1. reject a coordinate the device could not plausibly have produced (NaN,
 *      Infinity, out of range) — before any network call;
 *   2. require an authenticated session (this writes to our outbound budget);
 *   3. reverse-geocode with Nominatim.
 *
 * On any failure the result is `{ ok: false, error }` with customer-safe copy.
 * **No upstream text and no stack trace is ever returned**, and a coordinate
 * for which we have no address yields `ok: false` rather than a fabricated
 * address — the caller keeps the coordinate and lets the customer type.
 */
export async function reverseGeocodeAddress(
  lat: number,
  lng: number,
): Promise<ReverseGeocodeResult> {
  if (!isPlausibleCoordinate(lat, lng)) {
    return {
      ok: false,
      error:
        "That is not a location we can use. Please check your device's location settings and try again.",
    };
  }

  const supabase = await getSupabaseServerClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) {
    return { ok: false, error: "Please sign in to use this feature." };
  }

  const address = await reverseGeocode(lat, lng);
  if (!address) {
    return {
      ok: false,
      error:
        "We could not work out the address for that location. Please type it in instead.",
    };
  }

  return { ok: true, address };
}
