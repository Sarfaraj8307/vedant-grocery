import type {
  ComplaintStatus,
  OrderStatus,
  ProductWeightRow,
} from "@/types/database";

/* -------------------------------------------------------------------------- */
/* Weight & units                                                             */
/* -------------------------------------------------------------------------- */

/** `1000g -> "1 kg"`, `500g -> "500 g"`. Never hard-coded per product. */
export function formatGrams(grams: number): string {
  if (grams >= 1000 && grams % 1000 === 0) return `${grams / 1000} kg`;
  if (grams >= 1000) return `${(grams / 1000).toFixed(2).replace(/0$/, "")} kg`;
  return `${grams} g`;
}

/** Display label for a weight variant, falling back to the product unit. */
export function weightLabel(
  weight: ProductWeightRow | null,
  unitLabel: string,
): string {
  return weight ? weight.label : `per ${unitLabel}`;
}

/** A price-per-unit suffix such as `/ kg`. */
export function perUnit(unitLabel: string): string {
  return `/ ${unitLabel}`;
}

/* -------------------------------------------------------------------------- */
/* Dates & times                                                              */
/* -------------------------------------------------------------------------- */

const dayFormatter = new Intl.DateTimeFormat("en-IN", {
  weekday: "short",
  day: "numeric",
  month: "short",
});

const dateFormatter = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  year: "numeric",
});

const timeFormatter = new Intl.DateTimeFormat("en-IN", {
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

const dateTimeFormatter = new Intl.DateTimeFormat("en-IN", {
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}

/** "Today", "Tomorrow", a weekday name, or a short date for anything later. */
export function relativeDay(input: string | Date): string {
  const date = typeof input === "string" ? new Date(input) : input;
  const days = Math.round(
    (startOfDay(date) - startOfDay(new Date())) / 86_400_000,
  );
  if (days === 0) return "Today";
  if (days === 1) return "Tomorrow";
  if (days === -1) return "Yesterday";
  return dayFormatter.format(date);
}

export function formatDate(input: string | Date): string {
  const date = typeof input === "string" ? new Date(input) : input;
  return dateFormatter.format(date);
}

export function formatTime(input: string | Date): string {
  const date = typeof input === "string" ? new Date(input) : input;
  return timeFormatter.format(date);
}

export function formatDateTime(input: string | Date): string {
  const date = typeof input === "string" ? new Date(input) : input;
  return dateTimeFormatter.format(date);
}

/**
 * "Today, 4:00 pm – 7:00 pm" for a delivery slot.
 * Slot times are rendered in the **customer's local timezone**, which is what
 * the store's `starts_at`/`ends_at` timestamptz values mean.
 */
export function formatSlotWindow(startsAt: string, endsAt: string): string {
  return `${relativeDay(startsAt)}, ${formatTime(startsAt)} – ${formatTime(endsAt)}`;
}

/** Compact form for slot pickers: "4:00 – 7:00 pm". */
export function formatSlotRange(startsAt: string, endsAt: string): string {
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  const samePeriod = start.getHours() < 12 === end.getHours() < 12;
  const startText = timeFormatter.format(start);
  const endText = timeFormatter.format(end);
  return samePeriod
    ? `${startText.replace(/\s?(am|pm)$/i, "")} – ${endText}`
    : `${startText} – ${endText}`;
}

/** "2 days ago" / "just now" — used in order history and notifications. */
export function timeAgo(input: string | Date): string {
  const date = typeof input === "string" ? new Date(input) : input;
  const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} hr ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} ${days === 1 ? "day" : "days"} ago`;
  return formatDate(date);
}

/* -------------------------------------------------------------------------- */
/* Order & complaint status                                                   */
/* -------------------------------------------------------------------------- */

type StatusPresentation = {
  label: string;
  /** Plain-language explanation of what happens next. */
  description: string;
};

export const ORDER_STATUS_META: Record<OrderStatus, StatusPresentation> = {
  PENDING: {
    label: "Order received",
    description: "We have your order. The store will confirm it shortly.",
  },
  CONFIRMED: {
    label: "Confirmed by store",
    description: "The store has accepted your order and will start packing it.",
  },
  PROCESSING: {
    label: "Being packed",
    description: "We are weighing and packing your produce right now.",
  },
  READY_FOR_DELIVERY: {
    label: "Ready to dispatch",
    description: "Your order is packed and waiting for the delivery partner.",
  },
  OUT_FOR_DELIVERY: {
    label: "Out for delivery",
    description: "Your order is on its way to your address.",
  },
  DELIVERED: {
    label: "Delivered",
    description: "Delivered. Thank you for shopping with us.",
  },
  CANCELLED: {
    label: "Cancelled",
    description: "This order was cancelled.",
  },
};

export const ORDER_STATUS_FLOW: readonly OrderStatus[] = [
  "PENDING",
  "CONFIRMED",
  "PROCESSING",
  "READY_FOR_DELIVERY",
  "OUT_FOR_DELIVERY",
  "DELIVERED",
] as const;

export function orderStatusLabel(status: OrderStatus): string {
  return ORDER_STATUS_META[status]?.label ?? status;
}

export const COMPLAINT_STATUS_META: Record<ComplaintStatus, StatusPresentation> = {
  OPEN: { label: "Open", description: "We have received your message." },
  IN_PROGRESS: {
    label: "In review",
    description: "Our team is looking into this.",
  },
  RESOLVED: {
    label: "Resolved",
    description: "This has been resolved. Tell us if it isn't.",
  },
  CLOSED: { label: "Closed", description: "This conversation is closed." },
};

export function complaintStatusLabel(status: ComplaintStatus): string {
  return COMPLAINT_STATUS_META[status]?.label ?? status;
}

/* -------------------------------------------------------------------------- */
/* Misc                                                                       */
/* -------------------------------------------------------------------------- */

/** "1 item" / "3 items" */
export function pluralise(count: number, singular: string, plural?: string): string {
  return count === 1 ? `1 ${singular}` : `${count} ${plural ?? `${singular}s`}`;
}

/** Initials for an avatar fallback, e.g. "Aarav Shah" -> "AS". */
export function initials(...parts: (string | null | undefined)[]): string {
  const letters = parts
    .filter((p): p is string => Boolean(p && p.trim()))
    .map((p) => p.trim()[0]!.toUpperCase())
    .slice(0, 2)
    .join("");
  return letters || "V";
}

/** Mask a phone number for display: "98765 43210" -> "98••• 43210". */
export function maskPhone(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 6) return phone;
  return `${digits.slice(0, 2)}•••${digits.slice(-5)}`;
}
