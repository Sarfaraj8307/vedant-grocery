"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { LogOut, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { signOutEverywhere } from "@/services/auth-service";

/**
 * "Where you're signed in" — the current session, and a way to end all of them.
 *
 * WHAT IT HONESTLY CAN AND CANNOT SHOW
 * ------------------------------------
 * Supabase does not expose a per-device session list to the customer's own
 * publishable key, so this cannot enumerate "iPhone · Mumbai · 2 days ago" the
 * way a bank app does. Inventing that list would be worse than not having it:
 * a security panel that shows plausible but made-up sessions teaches people to
 * trust a control that is not real.
 *
 * What it shows instead is what is actually knowable — how this account signs
 * in, when it last signed in, and whether this device's session is currently
 * valid — plus the one action that genuinely matters when a device is lost:
 * revoking every refresh token at once.
 */
export function SessionsPanel({
  provider,
  lastSignInAt,
  sessionRenewsAt,
}: {
  provider: string;
  /** ISO timestamp, or null when Supabase has no record. */
  lastSignInAt: string | null;
  /** ISO timestamp of the current access token's expiry. */
  sessionRenewsAt: string | null;
}) {
  const router = useRouter();
  const [working, setWorking] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  async function endAll() {
    setWorking(true);
    setError(null);
    const result = await signOutEverywhere();
    if (!result.ok) {
      setError(result.error);
      setWorking(false);
      return;
    }
    // The local session is gone; send them somewhere that does not need one.
    router.push("/");
    router.refresh();
  }

  const method =
    provider === "google" ? "Google" : provider === "email" ? "Email and password" : provider;

  return (
    <div>
      <dl className="grid gap-3 text-small sm:grid-cols-2">
        <div className="rounded-md border border-line bg-surface-muted px-3 py-2.5">
          <dt className="text-ink-muted">Signed in with</dt>
          <dd className="mt-0.5 font-medium text-ink">{method}</dd>
        </div>
        <div className="rounded-md border border-line bg-surface-muted px-3 py-2.5">
          <dt className="text-ink-muted">Last sign-in</dt>
          <dd className="mt-0.5 font-medium text-ink">
            {lastSignInAt ? formatWhen(lastSignInAt) : "Not recorded"}
          </dd>
        </div>
      </dl>

      <p className="mt-3 flex items-start gap-2 text-small text-ink-muted">
        <ShieldCheck className="mt-0.5 size-4 shrink-0 text-leaf-700" aria-hidden="true" />
        <span>
          This device stays signed in until you sign out — you will not be asked for
          your password again on your next visit.
          {sessionRenewsAt
            ? " The session renews itself automatically in the background."
            : null}
        </span>
      </p>

      {error ? (
        <p role="alert" className="mt-3 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger">
          {error}
        </p>
      ) : null}

      <div className="mt-4 border-t border-line pt-4">
        <p className="text-small font-medium text-ink">Lost a device?</p>
        <p className="mt-1 text-small text-ink-muted">
          Signing out everywhere ends this session and every other one. You will need
          your password to sign back in.
        </p>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-2.5"
          loading={working}
          loadingLabel="Signing out…"
          onClick={() => void endAll()}
        >
          <LogOut className="size-4" aria-hidden="true" />
          Sign out of all devices
        </Button>
      </div>
    </div>
  );
}

/**
 * "2 Oct 2026" for old dates, "3 hours ago" for recent ones — a raw ISO string
 * is not something a customer should have to parse.
 */
function formatWhen(iso: string): string {
  const then = Date.parse(iso);
  if (Number.isNaN(then)) return "Not recorded";

  const minutes = Math.round((Date.now() - then) / 60_000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes} min ago`;

  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} ${hours === 1 ? "hour" : "hours"} ago`;

  const days = Math.round(hours / 24);
  if (days < 7) return `${days} ${days === 1 ? "day" : "days"} ago`;

  return new Date(then).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}
