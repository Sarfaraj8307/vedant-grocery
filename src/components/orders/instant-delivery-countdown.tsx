"use client";

import * as React from "react";
import { Timer } from "lucide-react";

/**
 * Countdown window for Instant Delivery (minutes).
 *
 * This matches the store's admin-editable ETA **max** default (45 minutes) —
 * the window the customer was shown at checkout. It is the "within 45 minutes"
 * promise the store makes for an express order. A future enhancement could pull
 * `store_settings.instant_delivery_eta_max` here instead of the constant.
 */
const INSTANT_DELIVERY_WINDOW_MINUTES = 45;

/** Format a remaining duration as mm:ss ("44:59"). */
function formatRemaining(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, "0")}`;
}

/**
 * Live countdown for an Instant Delivery order.
 *
 * Runs from the moment the order was placed (`placed_at`) to the express
 * window's end. It ticks once per second. Once the window passes it stops at
 * "00:00" and switches to a calm "still on it" message rather than a nagging
 * zero — the order remains in progress on the tracker.
 */
export function InstantDeliveryCountdown({ placedAt }: { placedAt: string }) {
  const [now, setNow] = React.useState(() => Date.now());

  React.useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);

  const deadline =
    new Date(placedAt).getTime() +
    INSTANT_DELIVERY_WINDOW_MINUTES * 60_000;
  const remaining = deadline - now;

  if (remaining <= 0) {
    return (
      <div className="rounded-lg border border-line bg-surface p-4 shadow-xs">
        <div className="flex items-center gap-2">
          <Timer className="size-4 text-leaf-600" aria-hidden="true" />
          <h2 className="font-display text-h3 text-ink">Instant delivery</h2>
        </div>
        <p className="mt-1.5 text-small text-ink-muted">
          We aim to deliver within {INSTANT_DELIVERY_WINDOW_MINUTES} minutes.
          If your order has not arrived yet, we are on it — the tracker on this
          page always shows the latest step.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-leaf-600/35 bg-leaf-50 p-4 shadow-xs">
      <div className="flex items-center gap-2">
        <Timer className="size-4 text-leaf-700" aria-hidden="true" />
        <h2 className="font-display text-h3 text-leaf-800">Instant delivery</h2>
      </div>
      <p className="mt-1.5 text-small text-leaf-800/85">
        Your order is being prepared for express delivery.
      </p>
      <p className="mt-2 font-display text-price tabular-nums text-leaf-800">
        {formatRemaining(remaining)}
      </p>
      <p className="text-caption text-leaf-800/70">
        Estimated time left to reach you (within{" "}
        {INSTANT_DELIVERY_WINDOW_MINUTES} minutes)
      </p>
    </div>
  );
}
