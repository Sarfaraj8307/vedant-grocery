"use client";

import * as React from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  isPlausibleCoordinate,
  isWithinPune,
  type GeocodedAddress,
} from "@/lib/geocode";
import { reverseGeocodeAddress } from "@/app/account/addresses/actions";

/**
 * Location picker — capture a device fix, turn it into address fields, and let
 * the customer confirm it on a map.
 *
 * GEOLOCATION IS A CONVENIENCE, NEVER A GATE. Every state below ends with the
 * customer able to type the address by hand, and the picker never blocks the
 * form's manual fields. It also never writes anything itself: it hands the
 * parent the coordinate and the prefilled fields, and the parent decides what
 * to store — so a failed fix cannot sneak a value into `addresses`.
 *
 * The state machine is deliberately explicit (`FixState` below) rather than a
 * handful of booleans, because the interesting part of this component is which
 * failure happened and what we say about it — see the states table in
 * `reverseGeocodeAddress`'s caller docs.
 */

/** A device fix with the accuracy that came with it. */
export type CoordFix = { lat: number; lng: number; accuracy: number };

type FixState =
  | { kind: "idle" }
  | { kind: "locating" }
  | { kind: "unsupported" }
  | { kind: "denied"; permanent: boolean }
  | { kind: "timeout" }
  | { kind: "unavailable" }
  | { kind: "geocoding"; coords: CoordFix }
  | { kind: "resolved"; coords: CoordFix }
  | { kind: "geocodeFailed"; coords: CoordFix };

const IDLE: FixState = { kind: "idle" };

/** Accuracy below which we say nothing; above which we warn and offer a retry. */
const LOW_ACCURACY_METRES = 100;

/** Half-width of the map preview, in degrees (~0.5 km at Pune's latitude). */
const MAP_PAD = 0.005;

/** Ask the Permissions API whether location is hard-blocked. */
async function geolocationPermissionState(): Promise<string | null> {
  try {
    if (!navigator.permissions?.query) return null;
    const status = await navigator.permissions.query({
      name: "geolocation" as PermissionName,
    });
    return status.state;
  } catch {
    return null;
  }
}

function formatAccuracy(metres: number): string {
  if (!Number.isFinite(metres) || metres < 0) return "accuracy unknown";
  if (metres >= 1000) return `±${(metres / 1000).toFixed(1)} km`;
  return `±${Math.round(metres)} m`;
}

function osmEmbedUrl(lat: number, lng: number): string {
  const bbox = [
    lng - MAP_PAD,
    lat - MAP_PAD,
    lng + MAP_PAD,
    lat + MAP_PAD,
  ].join(",");
  return `https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&marker=${lat},${lng}`;
}

export function LocationPicker({
  initialCoords = null,
  onAddressFound,
  onCoordsChange,
}: {
  /** Coordinates already stored on this address, when editing. */
  initialCoords?: { lat: number; lng: number } | null;
  /** Called with the parsed address so the parent form can prefill its fields. */
  onAddressFound: (address: GeocodedAddress) => void;
  /** Called with the confirmed coordinate, or null to clear it. */
  onCoordsChange: (coords: { lat: number; lng: number } | null) => void;
}) {

  /**
   * Seed once from an existing saved fix so editing shows the same map and
   * readout the customer saw when they saved it.
   *
   * Read via a lazy `useState` initialiser, never synced from an effect: the
   * parent re-renders whenever it stores a coordinate, and an effect keyed on
   * `initialCoords` would fire on every fresh capture and overwrite the
   * accuracy we just measured.
   */
  const seed = React.useMemo<{ state: FixState; accepted: CoordFix | null }>(() => {
    if (!initialCoords) return { state: IDLE, accepted: null };
    const fix: CoordFix = {
      lat: initialCoords.lat,
      lng: initialCoords.lng,
      // We do not know how accurately the stored fix was taken.
      accuracy: Number.NaN,
    };
    return { state: { kind: "resolved", coords: fix }, accepted: fix };
    // Intentionally mounted once: `initialCoords` is a snapshot of what was
    // saved, and re-running when the parent passes a fresh capture would reset
    // the panel to a stale fix mid-interaction.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Seeded lazily at mount rather than synced from an effect: an effect would
  // re-fire whenever the parent re-renders after a fresh capture and clobber the
  // accuracy we just measured, which is precisely the value this panel shows.
  const [state, setState] = React.useState<FixState>(() => seed.state);
  const [accepted, setAccepted] = React.useState<CoordFix | null>(() => seed.accepted);

  function accept(fix: CoordFix): void {
    setAccepted(fix);
    onCoordsChange({ lat: fix.lat, lng: fix.lng });
  }

  async function geocode(fix: CoordFix): Promise<void> {
    setState({ kind: "geocoding", coords: fix });
    const result = await reverseGeocodeAddress(fix.lat, fix.lng);

    if (result.ok) {
      accept(fix);
      setState({ kind: "resolved", coords: fix });
      onAddressFound(result.address);
      // Out of Pune is not an error — the customer may be adding an address for
      // another city, and the authoritative check happens at checkout.
      const outside = !isWithinPune(fix.lat, fix.lng);
      toast.success(
        outside
          ? "Location captured — please check the details we filled in."
          : "Location captured — we filled in what we could, please check it.",
        {
          description: outside ? "This looks outside our usual delivery area." : undefined,
        },
      );
      return;
    }

    // Geocoding failed: KEEP the coordinate, leave the text fields alone. The
    // customer types; nothing is invented.
    accept(fix);
    setState({ kind: "geocodeFailed", coords: fix });
    toast.error(result.error);
  }

  function requestFix(): void {
    if (!navigator.geolocation) {
      setState({ kind: "unsupported" });
      return;
    }
    setState({ kind: "locating" });

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords;

        // A corrupt or tampered coordinate must not reach Nominatim or the DB.
        if (!isPlausibleCoordinate(latitude, longitude)) {
          setState({ kind: "unavailable" });
          return;
        }
        void geocode({ lat: latitude, lng: longitude, accuracy });
      },
      async (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          // `denied` means the browser will not prompt again and the customer
          // must re-enable the site permission; anything else is dismissible.
          const permission = await geolocationPermissionState();
          setState({
            kind: "denied",
            permanent: permission === "denied",
          });
          return;
        }
        if (error.code === error.TIMEOUT) {
          setState({ kind: "timeout" });
          return;
        }
        setState({ kind: "unavailable" });
      },
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 0 },
    );
  }

  const coords =
    state.kind === "geocoding" ||
    state.kind === "resolved" ||
    state.kind === "geocodeFailed"
      ? state.coords
      : null;
  const lowAccuracy =
    coords != null && Number.isFinite(coords.accuracy) &&
    coords.accuracy > LOW_ACCURACY_METRES;
  const outsidePune = coords ? !isWithinPune(coords.lat, coords.lng) : false;

  return (
    <div className="flex flex-col gap-2.5">
      {/* --- capture ------------------------------------------------------- */}
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={requestFix}
        loading={state.kind === "locating" || state.kind === "geocoding"}
        loadingLabel={
          state.kind === "geocoding" ? "Filling your address…" : "Finding you…"
        }
      >
        {accepted ? "Update my location" : "Use my current location"}
      </Button>

      {/* --- the fix ------------------------------------------------------- */}
      {coords ? (
        <div className="flex flex-col gap-1.5">
          <p className="text-small text-ink-muted">
            <span className="font-medium text-ink">
              {coords.lat.toFixed(5)}, {coords.lng.toFixed(5)}
            </span>
            <span className="ml-1 text-ink-subtle">
              {Number.isFinite(coords.accuracy)
                ? formatAccuracy(coords.accuracy)
                : "accuracy from when this was saved"}
            </span>
          </p>

          {state.kind === "geocoding" ? (
            <p className="text-small text-ink-muted">
              Working out your address…
            </p>
          ) : null}

          {/* Never save a low-accuracy fix as if it were precise: say so, and
              offer a way to get a better one. */}
          {lowAccuracy ? (
            <div
              role="status"
              className="rounded-md border border-line-strong bg-warning-bg px-3 py-2 text-small text-warning"
            >
              <p>
                Your location accuracy is low — move to an open area or adjust
                the address manually.
              </p>
              <Button
                type="button"
                variant="quiet"
                size="sm"
                className="mt-1"
                onClick={requestFix}
              >
                Try again
              </Button>
            </div>
          ) : null}

          {outsidePune ? (
            <p className="text-small text-warning">
              This location looks outside our usual delivery area. Please
              double-check the details we filled in.
            </p>
          ) : null}

          {state.kind === "geocodeFailed" ? (
            <p className="text-small text-warning">
              We kept your location, but could not fill the address in. Please
              type the details below.
            </p>
          ) : null}

          {state.kind === "resolved" ? (
            <button
              type="button"
              onClick={() => {
                setAccepted(null);
                onCoordsChange(null);
                setState(IDLE);
              }}
              className="self-start text-small text-ink-muted underline-offset-2 hover:text-danger hover:underline"
            >
              Remove this location
            </button>
          ) : null}
        </div>
      ) : null}

      {/* --- failure copy -------------------------------------------------- */}
      {state.kind === "unsupported" ? (
        <p className="text-small text-ink-muted">
          This browser cannot share your location. Please type your address in
          below.
        </p>
      ) : null}

      {state.kind === "denied" ? (
        <p className="text-small text-ink-muted">
          {state.permanent
            ? "Location access is blocked for this site. To allow it, tap the icon in your browser's address bar (or open your browser's site settings) and set Location to Allow, then try again."
            : "We could not get your location. You can try again, or just type your address below."}{" "}
          <button
            type="button"
            onClick={requestFix}
            className="underline-offset-2 hover:underline"
          >
            Try again
          </button>
        </p>
      ) : null}

      {state.kind === "timeout" ? (
        <p className="text-small text-ink-muted">
          It took too long to get your location. Make sure location services are
          switched on, then{" "}
          <button
            type="button"
            onClick={requestFix}
            className="underline-offset-2 hover:underline"
          >
            try again
          </button>{" "}
          — or type your address below.
        </p>
      ) : null}

      {state.kind === "unavailable" ? (
        <p className="text-small text-ink-muted">
          Your device could not provide a location. Please check that location
          services are switched on, or type your address below.
        </p>
      ) : null}

      {/* --- map preview --------------------------------------------------- */}
      {coords ? (
        <div className="flex flex-col gap-1">
          <iframe
            title="Map preview of your delivery location"
            src={osmEmbedUrl(coords.lat, coords.lng)}
            loading="lazy"
            referrerPolicy="no-referrer-when-downgrade"
            className="h-40 w-full rounded-md border border-line bg-surface-muted"
          />
          <p className="text-small text-ink-muted">Is this your delivery location?</p>
          <a
            href={`https://www.openstreetmap.org/?mlat=${coords.lat}&mlon=${coords.lng}#map=19/${coords.lat}/${coords.lng}`}
            target="_blank"
            rel="noopener noreferrer"
            className="self-start text-small text-leaf-700 underline-offset-2 hover:underline"
          >
            Open a larger map
          </a>
        </div>
      ) : null}

      {/* --- the manual path is always available -------------------------- */}
      <p className="text-small text-ink-subtle">
        Prefer to type it? Just fill in the address below — you do not need to
        share your location.
      </p>
    </div>
  );
}
