"use client";

import * as React from "react";
import { Download, RefreshCw, X } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * PWA lifecycle: registration, update handling and the install prompt.
 *
 * Three deliberate behaviours:
 *  1. **Registration only in production.** In development the service worker
 *     would cache `/_next/static` chunks across rebuilds and serve stale modules,
 *     which makes hot reload lie to you. Dev always runs without it.
 *  2. **Updates never interrupt.** A new worker is never activated automatically;
 *     the customer is offered an "update" button. Auto-activating mid-checkout
 *     would reload the page at the worst possible moment.
 *  3. **The install prompt is opt-in and dismissible, remembered.** Browsers fire
 *     `beforeinstallprompt` once; we re-surface it as a tasteful bar rather than a
 *     browser-native mega-banner, and we never show it again after dismissal.
 */

const DISMISSED_KEY = "vedant.pwa.installDismissed";

type InstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

export function PwaProvider() {
  const [waitingWorker, setWaitingWorker] = React.useState<ServiceWorker | null>(null);
  /** Set only from the `beforeinstallprompt` handler, which is a user-agent
   *  callback rather than an effect body — so no cascading render on mount. */
  const [installEvent, setInstallEvent] = React.useState<InstallPromptEvent | null>(null);

  /* --- Service worker registration + update detection --------------------- */
  React.useEffect(() => {
    if (process.env.NODE_ENV !== "production") return;
    if (typeof navigator === "undefined" || !("serviceWorker" in navigator)) return;

    let registration: ServiceWorkerRegistration | undefined;
    let cancelled = false;

    navigator.serviceWorker
      .register("/sw.js", { scope: "/" })
      .then((reg) => {
        if (cancelled) return;
        registration = reg;

        // Already waiting when we load (e.g. a second tab installed it).
        if (reg.waiting && navigator.serviceWorker.controller) {
          setWaitingWorker(reg.waiting);
        }

        reg.addEventListener("updatefound", () => {
          const installing = reg.installing;
          if (!installing) return;
          installing.addEventListener("statechange", () => {
            if (installing.state === "installed" && navigator.serviceWorker.controller) {
              setWaitingWorker(installing);
            }
          });
        });
      })
      .catch(() => {
        // A blocked or failed registration is not an error the customer can act
        // on; the app works perfectly well as a website.
      });

    // When the new worker takes control, reload once so the page and the cache
    // are guaranteed to be the same version.
    let reloading = false;
    const onControllerChange = () => {
      if (reloading) return;
      reloading = true;
      window.location.reload();
    };
    if ("serviceWorker" in navigator) {
      navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);
    }

    return () => {
      cancelled = true;
      navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
      void registration;
    };
  }, []);

  /* --- Install prompt ----------------------------------------------------- */
  React.useEffect(() => {
    function onBeforeInstall(event: Event) {
      // Prevent the mini-infobar so we can present it at a considerate moment.
      event.preventDefault();

      // The dismissal flag is read here, inside a browser callback, rather than
      // in the effect body — so a customer who chose "not now" is never shown
      // the prompt again, and mounting never triggers a render.
      try {
        if (window.localStorage.getItem(DISMISSED_KEY) === "1") return;
      } catch {
        // Storage unavailable: show the prompt once per session.
      }
      setInstallEvent(event as InstallPromptEvent);
    }

    window.addEventListener("beforeinstallprompt", onBeforeInstall);
    return () => window.removeEventListener("beforeinstallprompt", onBeforeInstall);
  }, []);

  function dismissInstall() {
    setInstallEvent(null);
    try {
      window.localStorage.setItem(DISMISSED_KEY, "1");
    } catch {
      // ignore
    }
  }

  const showInstall = installEvent !== null;

  if (!waitingWorker && !showInstall) return null;

  return (
    <div
      role="status"
      className="fixed inset-x-3 bottom-24 z-40 flex flex-col gap-3 rounded-lg border border-line bg-surface p-3.5 shadow-lg lg:inset-x-auto lg:bottom-6 lg:right-6 lg:max-w-sm"
    >
      {waitingWorker ? (
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-md bg-leaf-100 text-leaf-700">
            <RefreshCw className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-small font-medium text-ink">
              A new version is ready
            </p>
            <p className="mt-0.5 text-small text-ink-muted">
              Update now to get the latest prices and stock.
            </p>
            <div className="mt-2 flex gap-2">
              <Button
                size="sm"
                onClick={() => {
                  waitingWorker.postMessage("SKIP_WAITING");
                  setWaitingWorker(null);
                }}
              >
                Update
              </Button>
              <Button
                size="sm"
                variant="quiet"
                onClick={() => setWaitingWorker(null)}
              >
                Later
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      {showInstall ? (
        <div className="flex items-start gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-md bg-leaf-100 text-leaf-700">
            <Download className="size-4" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="text-small font-medium text-ink">
              Add Vedant to your home screen
            </p>
            <p className="mt-0.5 text-small text-ink-muted">
              Opens faster and keeps your basket when you are offline.
            </p>
            <div className="mt-2 flex gap-2">
              <Button
                size="sm"
                onClick={async () => {
                  const event = installEvent;
                  if (!event) return;
                  await event.prompt();
                  await event.userChoice;
                  dismissInstall();
                }}
              >
                Install
              </Button>
              <Button size="sm" variant="quiet" onClick={dismissInstall}>
                Not now
              </Button>
            </div>
          </div>
          <button
            type="button"
            onClick={dismissInstall}
            className="grid size-7 shrink-0 place-items-center rounded-xs text-ink-subtle transition-colors hover:bg-surface-muted hover:text-ink"
          >
            <X className="size-3.5" aria-hidden="true" />
            <span className="sr-only">Dismiss</span>
          </button>
        </div>
      ) : null}
    </div>
  );
}
