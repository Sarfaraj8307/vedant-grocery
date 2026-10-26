/**
 * Centralised store configuration — the single source of truth for every
 * piece of business identity the UI renders.
 *
 * WHY THIS EXISTS
 * Components must never hard-code a phone number, address or opening hour.
 * Resolution order:
 *   1. `NEXT_PUBLIC_STORE_*` environment variables (deploy-time override)
 *   2. the `unset` sentinel declared here
 *   3. (future) `private.shop_settings`, served by an owner-maintained route
 *
 * HONESTY RULE
 * Fields that the owner has not supplied yet resolve to `null` — never to a
 * plausible-looking fake. A `null` phone number hides the `tel:` affordance and
 * shows an explanatory state instead of a button that silently does nothing.
 * See docs/TASK_STATE.md → BLOCKED.
 */

export type StoreContactChannel = {
  /** E.164 or local dial string, digits only plus optional leading `+`. */
  readonly dial: string;
  /** Human-readable number for display. */
  readonly display: string;
};

export type StoreConfig = {
  readonly name: string;
  readonly legalName: string;
  readonly tagline: string;
  readonly shortDescription: string;
  readonly phone: StoreContactChannel | null;
  readonly whatsapp: StoreContactChannel | null;
  readonly email: string | null;
  readonly address: {
    readonly line1: string | null;
    readonly line2: string | null;
    readonly city: string | null;
    readonly state: string | null;
    readonly postalCode: string | null;
    readonly country: string;
  };
  /** Human-readable opening hours; `null` until the owner supplies them. */
  readonly openingHours: readonly string[];
  readonly deliveryAreas: readonly string[];
  readonly social: {
    readonly instagram: string | null;
    readonly facebook: string | null;
  };
  /** Set when the owner has not yet supplied real business details. */
  readonly needsOwnerConfiguration: boolean;
};

export function normaliseDial(raw: string | undefined): StoreContactChannel | null {
  const value = raw?.trim();
  if (!value) return null;
  // Strip everything that cannot be part of a dial string.
  const digits = value.replace(/[^\d+]/g, "");
  if (digits.replace(/\D/g, "").length < 8) return null;
  const display =
    raw
      ?.trim()
      .replace(/^\+?91/, "")
      .replace(/(\d{5})(\d{5})/, "$1 $2") ?? digits;
  return { dial: digits, display: display || digits };
}

const phone = normaliseDial(process.env.NEXT_PUBLIC_STORE_PHONE);
const whatsapp = normaliseDial(process.env.NEXT_PUBLIC_STORE_WHATSAPP);

/** Digits-only WhatsApp number for `wa.me` links (no `+`, no spaces). */
export const whatsappHref = whatsapp
  ? `https://wa.me/${whatsapp.dial.replace(/\D/g, "")}`
  : null;

export const telHref = phone ? `tel:${phone.dial}` : null;

export const store: StoreConfig = {
  name: "Vedant Fruit & Vegetables",
  legalName: "Vedant Fruit & Vegetables",
  tagline: "Market-fresh fruit, weighed the way you want it.",
  shortDescription:
    "Same-day fruit and vegetable delivery. Picked at the market this morning, weighed and packed to order — the freshest produce you've tasted.",
  phone,
  whatsapp,
  email: process.env.NEXT_PUBLIC_STORE_EMAIL?.trim() || null,
  address: {
    line1: process.env.NEXT_PUBLIC_STORE_ADDRESS?.trim() || null,
    line2: null,
    city: null,
    state: null,
    postalCode: null,
    country: "IN",
  },
  openingHours: [],
  deliveryAreas: [],
  social: { instagram: null, facebook: null },
  needsOwnerConfiguration: !phone && !whatsapp,
} as const;

/**
 * True when a contact channel is real and safe to render as a link.
 * UI must call this before rendering Call / WhatsApp affordances.
 */
export function hasPhone(): boolean {
  return store.phone !== null;
}

export function hasWhatsApp(): boolean {
  return store.whatsapp !== null && whatsappHref !== null;
}

/**
 * A `wa.me` deep link pre-filled with a customer-safe opening message.
 * Never includes order internals or identifiers — the customer is identified
 * to the store by the phone number their chat comes from.
 */
export function whatsappLink(message?: string): string | null {
  if (!whatsappHref) return null;
  const text = message ?? `Hi ${store.name}, I'd like to ask about my order.`;
  return `${whatsappHref}?text=${encodeURIComponent(text)}`;
}
