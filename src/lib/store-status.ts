import { cache } from "react";

import {
  INSTANT_DELIVERY_ETA_MAX_DEFAULT,
  INSTANT_DELIVERY_ETA_MIN_DEFAULT,
  normaliseInstantDeliveryEta,
} from "@/lib/instant-delivery";
import { getSupabaseServerClient } from "@/lib/supabase/server";

/**
 * The store's live trading state, read from `store_settings`.
 *
 * WHY THIS EXISTS
 * `place_order` has always refused with `STORE_CLOSED` when `is_open` is false —
 * that enforcement is correct and stays server-side. But the website had **no
 * notion of store status at all**: a customer only discovered a closure by
 * filling in an address, choosing a slot, and having checkout fail. This reads
 * the same row the RPC enforces against, so the UI can say so up front.
 *
 * FAIL-OPEN, DELIBERATELY
 * If the read fails, this reports `acceptingOrders: true`. The reasoning: a
 * transient read error must not stop a real customer from ordering, because
 * `place_order` will still refuse correctly if the store is genuinely closed.
 * Failing *closed* on a read error would turn a blip into lost revenue. The
 * database remains the authority either way.
 */
export type StoreStatus = {
  readonly acceptingOrders: boolean;
  /** The operator's reason for closing, if they gave one. */
  readonly closedReason: string | null;
  readonly storeName: string | null;
  readonly deliveryFee: number | null;
  readonly freeDeliveryThreshold: number | null;
  readonly minOrderAmount: number | null;
  /**
   * Whether instant delivery is offered (operator toggle). IMPORTANT: unlike
   * the rest of the store status this fails *closed* (false) — a transient read
   * error must never wrongly offer instant delivery, because an order placed
   * without a slot has no scheduled slot to fall back on.
   */
  readonly instantDeliveryEnabled: boolean;
  /**
   * Admin-editable Instant Delivery ETA bounds (minutes), read from
   * `store_settings.instant_delivery_eta_min` / `_max`. Missing or invalid
   * values are normalised to the safe 30 / 45 defaults, so the website never
   * renders a hard-coded or nonsensical ETA.
   */
  readonly instantDeliveryEtaMin: number;
  readonly instantDeliveryEtaMax: number;
};

const UNKNOWN: StoreStatus = {
  acceptingOrders: true,
  closedReason: null,
  storeName: null,
  deliveryFee: null,
  freeDeliveryThreshold: null,
  minOrderAmount: null,
  instantDeliveryEnabled: false,
  instantDeliveryEtaMin: INSTANT_DELIVERY_ETA_MIN_DEFAULT,
  instantDeliveryEtaMax: INSTANT_DELIVERY_ETA_MAX_DEFAULT,
};

type StoreSettingsRow = {
  is_open: boolean | null;
  acceptance_reason: string | null;
  store_name: string | null;
  delivery_fee: number | null;
  free_delivery_threshold: number | null;
  min_order_amount: number | null;
  instant_delivery_enabled: boolean | null;
  instant_delivery_eta_min: number | null;
  instant_delivery_eta_max: number | null;
};

/**
 * Read the store status. Memoised per request with React `cache` so a page that
 * renders both a banner and a checkout summary issues one query, not two.
 */
export const getStoreStatus = cache(async (): Promise<StoreStatus> => {
  try {
    const supabase = await getSupabaseServerClient();
    const { data, error } = await supabase
      .from("store_settings")
      .select(
        "is_open, acceptance_reason, store_name, delivery_fee, free_delivery_threshold, min_order_amount, instant_delivery_enabled, instant_delivery_eta_min, instant_delivery_eta_max",
      )
      .eq("id", 1)
      .maybeSingle();

    if (error || !data) return UNKNOWN;

    const row = data as StoreSettingsRow;
    const eta = normaliseInstantDeliveryEta(
      row.instant_delivery_eta_min,
      row.instant_delivery_eta_max,
    );
    return {
      // `is_open` is NOT NULL in the database, but a null is treated as open for
      // the same fail-open reason described above.
      acceptingOrders: row.is_open !== false,
      closedReason: row.acceptance_reason?.trim() || null,
      storeName: row.store_name?.trim() || null,
      deliveryFee: row.delivery_fee,
      freeDeliveryThreshold: row.free_delivery_threshold,
      minOrderAmount: row.min_order_amount,
      instantDeliveryEnabled: row.instant_delivery_enabled === true,
      instantDeliveryEtaMin: eta.min,
      instantDeliveryEtaMax: eta.max,
    };
  } catch {
    return UNKNOWN;
  }
});

/** Customer-facing copy for a closed store. One wording, used everywhere. */
export const ORDERS_CLOSED_MESSAGE =
  "We're currently not accepting new orders. You can still browse the store.";
