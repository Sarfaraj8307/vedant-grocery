"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/menu";
import { SheetContent, SheetRoot } from "@/components/ui/sheet";
import {
  recordTermsAcceptance,
  signOut,
  syncSignupConsent,
} from "@/services/auth-service";

/**
 * Consent gate.
 *
 * Shown once per terms version, immediately after sign-in, until the customer
 * accepts. Deliberately **not dismissible**: no close button, `Esc` and
 * outside-click are prevented, because the point is to capture a decision
 * before we keep holding their data. The only ways out are accepting or
 * signing out (which is reachable from the header).
 *
 * Why a dialog rather than a separate route: a route can be navigated away
 * from, and a customer deep-linking into `/cart` would never see it.
 */
export function ConsentGate({ needed }: { needed: boolean }) {
  const router = useRouter();
  const [open, setOpen] = React.useState(needed);
  const [agreed, setAgreed] = React.useState(false);
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  /*
   * A password signup already ticked the box. When email confirmation deferred
   * the session, that tick could not be written at the time — it travelled in
   * `user_metadata` instead. Persist it now so the customer is not asked twice.
   * If there is nothing to sync, the dialog simply stays open.
   */
  React.useEffect(() => {
    if (!needed) return;

    let cancelled = false;
    void syncSignupConsent().then((synced) => {
      if (cancelled || !synced) return;
      setOpen(false);
      router.refresh();
    });

    return () => {
      cancelled = true;
    };
  }, [needed, router]);

  async function accept() {
    setPending(true);
    setError(null);

    const result = await recordTermsAcceptance("login");
    if (!result.ok) {
      setError(result.error);
      setPending(false);
      return;
    }

    setOpen(false);
    router.refresh();
  }

  return (
    <SheetRoot open={open} onOpenChange={() => {}}>
      <SheetContent
        side="center"
        showClose={false}
        title="Accept the terms to continue"
        description="We need your agreement before we keep storing your account details."
        // Every dismissal path is blocked: accepting is the only exit.
        onEscapeKeyDown={(event) => event.preventDefault()}
        onPointerDownOutside={(event) => event.preventDefault()}
        onInteractOutside={(event) => event.preventDefault()}
      >
        <div className="px-5 pt-6 pb-5">
          <span
            aria-hidden="true"
            className="mb-4 grid size-11 place-items-center rounded-full bg-leaf-100 text-leaf-700"
          >
            <ShieldCheck className="size-5" />
          </span>

          <h2 className="font-display text-h3 text-ink">
            One last step
          </h2>
          <p className="mt-2 text-small text-ink-muted">
            We store your name, email, delivery addresses and order history so we
            can deliver your orders and let you reorder. Before we continue, please
            confirm you accept our terms and privacy policy.
          </p>

          <label className="mt-4 flex cursor-pointer items-start gap-2.5 rounded-md border border-line bg-surface-muted px-3 py-3">
            <Checkbox
              className="mt-0.5"
              checked={agreed}
              onCheckedChange={(checked) => setAgreed(checked === true)}
              aria-describedby="consent-note"
            />
            <span id="consent-note" className="text-small text-ink-muted">
              I agree to the{" "}
              <Link
                href="/terms"
                target="_blank"
                className="font-medium text-leaf-700 underline underline-offset-2"
              >
                terms &amp; conditions
              </Link>{" "}
              and the{" "}
              <Link
                href="/privacy"
                target="_blank"
                className="font-medium text-leaf-700 underline underline-offset-2"
              >
                privacy policy
              </Link>
              .
            </span>
          </label>

          {error ? (
            <div className="mt-3">
              <p role="alert" className="text-small text-danger">
                {error}
              </p>
              {/*
                Escape hatch. The gate is deliberately undismissable, which is
                only safe if there is a way out when saving fails — otherwise a
                misconfigured grant or a persistent network fault would trap the
                customer behind a modal whose only button cannot succeed.
                This one path skips the usual confirmation: it exists to get
                someone unstuck, not as a routine sign-out.
              */}
              <button
                type="button"
                onClick={() => {
                  void signOut().then(() => window.location.assign("/"));
                }}
                className="mt-2 text-small text-ink-muted underline underline-offset-2 hover:text-ink"
              >
                Sign out instead
              </button>
            </div>
          ) : null}

          <Button
            type="button"
            block
            size="lg"
            className="mt-4"
            disabled={!agreed}
            loading={pending}
            loadingLabel="Saving…"
            onClick={() => void accept()}
          >
            Accept and continue
          </Button>

          <p className="mt-3 text-center text-caption text-ink-subtle">
            We record the date and version you accepted. You can read them any time
            from your account page.
          </p>
        </div>
      </SheetContent>
    </SheetRoot>
  );
}
