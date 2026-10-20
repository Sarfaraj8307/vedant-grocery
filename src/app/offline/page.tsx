import type { Metadata } from "next";
import Link from "next/link";
import { WifiOff } from "lucide-react";

import { Button } from "@/components/ui/button";

export const metadata: Metadata = {
  title: "You're offline",
  robots: { index: false, follow: false },
};

/**
 * Offline fallback, precached by the service worker.
 *
 * Must be entirely static — no data fetching, no client-side session read —
 * because the whole point is that it renders with no network. It is honest about
 * what is and is not available rather than showing a spinner that will never
 * resolve.
 */
export default function OfflinePage() {
  return (
    <div className="shell flex min-h-[60vh] flex-col items-center justify-center py-16 text-center">
      <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
        <WifiOff className="size-7 text-marigold-600" aria-hidden="true" />
      </span>

      <h1 className="mt-5 font-display text-h1 text-ink">You&apos;re offline</h1>
      <p className="mt-2 max-w-[46ch] text-body text-ink-muted">
        We can&apos;t reach the shop right now. Your basket is saved on this device
        and will still be here when you reconnect.
      </p>

      <div className="mt-6 flex flex-wrap justify-center gap-2">
        <Button asChild variant="secondary">
          <Link href="/">Retry the shop</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/contact">Contact the shop</Link>
        </Button>
      </div>

      <div className="mt-8 w-full max-w-md rounded-lg border border-line bg-surface p-4 text-left">
        <h2 className="text-h3 text-ink">Still works offline</h2>
        <ul className="mt-2 flex flex-col gap-1.5 text-small text-ink-muted">
          <li>• Pages you already visited in this session</li>
          <li>• Your basket, as long as you do not clear browser data</li>
          <li>• This help page</li>
        </ul>
        <h2 className="mt-4 text-h3 text-ink">Needs a connection</h2>
        <ul className="mt-2 flex flex-col gap-1.5 text-small text-ink-muted">
          <li>• Today&apos;s prices and stock</li>
          <li>• Choosing a delivery slot</li>
          <li>• Placing an order</li>
        </ul>
      </div>
    </div>
  );
}
