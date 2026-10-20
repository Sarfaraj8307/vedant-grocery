import Link from "next/link";
import { AlertTriangle, CheckCircle2, ChevronRight, MapPin } from "lucide-react";

import { checkServiceability } from "@/lib/serviceability";
import { serviceAreaLabels } from "@/config/service-area";
import type { DeliveryLocation } from "@/lib/addresses";

/**
 * "Delivering to …" — the delivery-location bar at the top of the home page.
 *
 * A **Server Component**: the location and its serviceability verdict are
 * decided on the server from the customer's own default address (RLS-scoped), so
 * the answer is consistent with checkout rather than guessed in the browser.
 *
 * Three honest states, never a fabricated one:
 *   • no address yet        → invite the customer to set a location;
 *   • address inside area   → confirm, with a one-tap way to change it;
 *   • address outside area  → say so plainly, before they fill a basket.
 *
 * Layout: the call-to-action is `w-full sm:w-auto` inside a `flex-wrap` row, so
 * on a 360px phone it drops to its own full-width line instead of squeezing the
 * text column to ~90px (which wrapped the title onto five lines).
 */
export function DeliveryLocationBar({
  location,
}: {
  location: DeliveryLocation | null;
}) {
  const cta = location ? "Change" : "Add an address";

  const verdict = location
    ? checkServiceability({
        city: location.city,
        postalCode: location.postalCode,
      })
    : null;

  const place = location
    ? [location.city, location.postalCode].filter(Boolean).join(" ")
    : "";

  return (
    <section
      aria-label="Delivery location"
      className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-lg border border-line bg-surface p-4 shadow-xs"
    >
      <span className="grid size-10 shrink-0 place-items-center rounded-full bg-leaf-100 text-leaf-700">
        <MapPin className="size-5" aria-hidden="true" />
      </span>

      <div className="min-w-0 flex-1">
        {!location ? (
          <>
            <p className="text-body font-medium text-ink">
              Set your delivery location
            </p>
            <p className="mt-0.5 text-small text-ink-muted">
              Add an address so we can confirm we deliver to you. We currently
              deliver in {serviceAreaLabels.join(" and ")}.
            </p>
          </>
        ) : (
          <>
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-caption text-ink-subtle">
              Delivering to
              {verdict?.serviceable ? (
                <span className="inline-flex items-center gap-1 rounded-pill bg-leaf-100 px-2 py-0.5 text-[10px] font-semibold text-leaf-700">
                  <CheckCircle2 className="size-3" aria-hidden="true" />
                  In our area
                </span>
              ) : (
                <span className="inline-flex items-center gap-1 rounded-pill bg-marigold-50 px-2 py-0.5 text-[10px] font-semibold text-marigold-700">
                  <AlertTriangle className="size-3" aria-hidden="true" />
                  Outside our area
                </span>
              )}
            </p>
            <p className="mt-0.5 truncate text-body font-medium text-ink">
              {location.label}
              {place ? <span className="text-ink-muted"> · {place}</span> : null}
            </p>
            {verdict && !verdict.serviceable ? (
              <p className="mt-0.5 text-small text-marigold-700">
                {verdict.reason}
              </p>
            ) : null}
          </>
        )}
      </div>

      <Link
        href="/account/addresses"
        className={
          location
            ? "inline-flex h-10 w-full shrink-0 items-center justify-center gap-1.5 rounded-md border-[1.5px] border-line-strong px-4 text-small font-medium text-ink transition-colors hover:border-ink-subtle hover:bg-surface-muted sm:w-auto"
            : "inline-flex h-10 w-full shrink-0 items-center justify-center gap-1.5 rounded-md bg-leaf-600 px-4 text-small font-medium text-white transition-colors hover:bg-leaf-700 sm:w-auto"
        }
      >
        {cta}
        <ChevronRight className="size-4" aria-hidden="true" />
      </Link>
    </section>
  );
}
