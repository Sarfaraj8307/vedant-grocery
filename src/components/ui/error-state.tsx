"use client";

import Link from "next/link";
import { AlertTriangle, RefreshCw, WifiOff } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Friendly error presentation (brief §31).
 *
 * A customer is never shown a stack trace, a Postgres error code or the word
 * "exception". Every variant states what happened, whether their data is safe,
 * and offers one clear action:
 *  * `offline`   — no connection; the cached shell is still usable
 *  * `unexpected`— a server error while rendering the page
 *  * `notFound`  — the thing they asked for does not exist
 */
export type ErrorKind = "offline" | "unexpected" | "notFound";

const COPY: Record<
  ErrorKind,
  { title: string; body: string; reassurance: string }
> = {
  offline: {
    title: "You're offline",
    body: "We can't reach the shop right now. Your basket is saved on this device and will still be here when you reconnect.",
    reassurance: "Nothing in your basket has been lost.",
  },
  unexpected: {
    title: "Something went wrong on our side",
    body: "This page didn't load properly. It is almost certainly temporary — trying again usually fixes it.",
    reassurance: "Your account, basket and past orders are unaffected.",
  },
  notFound: {
    title: "We couldn't find that page",
    body: "The link may be old, or the item may no longer be stocked. The rest of the shop is still open.",
    reassurance: "",
  },
};

export function ErrorState({
  kind,
  /** Rendered only when the caller can actually retry (e.g. an error boundary). */
  onRetry,
  className,
}: {
  kind: ErrorKind;
  onRetry?: () => void;
  className?: string;
}) {
  const copy = COPY[kind];
  const Icon = kind === "offline" ? WifiOff : AlertTriangle;

  return (
    <div
      className={
        className ??
        "shell flex min-h-[60vh] flex-col items-center justify-center py-16 text-center"
      }
    >
      <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
        <Icon className="size-7 text-marigold-600" aria-hidden="true" />
      </span>

      <h1 className="mt-5 font-display text-h1 text-ink">{copy.title}</h1>
      <p className="mt-2 max-w-[46ch] text-body text-ink-muted">{copy.body}</p>
      {copy.reassurance ? (
        <p className="mt-1.5 text-small text-ink-subtle">{copy.reassurance}</p>
      ) : null}

      <div className="mt-6 flex flex-wrap justify-center gap-2">
        {onRetry ? (
          <Button type="button" onClick={onRetry}>
            <RefreshCw className="size-4" aria-hidden="true" />
            Try again
          </Button>
        ) : null}
        <Button asChild variant={onRetry ? "outline" : "primary"}>
          <Link href="/">Go to the shop</Link>
        </Button>
        {kind === "notFound" ? (
          <Button asChild variant="outline">
            <Link href="/categories">Browse produce</Link>
          </Button>
        ) : null}
        <Button asChild variant="quiet">
          <Link href="/contact">Contact the shop</Link>
        </Button>
      </div>
    </div>
  );
}
