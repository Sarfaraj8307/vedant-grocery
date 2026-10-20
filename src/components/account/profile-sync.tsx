"use client";

import * as React from "react";
import { ensureProfile } from "@/services/auth-service";

/**
 * Creates the customer's `profiles` row on their first authenticated render.
 *
 * The live database has no trigger on `auth.users`
 * (docs/BACKEND_CONTRACT.md §7.3), so when email confirmation is enabled the
 * signup flow cannot write the row itself. This component closes that gap
 * exactly once per session, then never runs again.
 */
const DONE_KEY = "vedant.profile.synced";

export function ProfileSync() {
  React.useEffect(() => {
    try {
      if (sessionStorage.getItem(DONE_KEY) === "1") return;
    } catch {
      // sessionStorage can throw in locked-down browsers; fall through and try.
    }

    let cancelled = false;
    void ensureProfile().then((ok) => {
      if (cancelled || !ok) return;
      try {
        sessionStorage.setItem(DONE_KEY, "1");
      } catch {
        // Non-fatal: worst case we attempt the idempotent insert again.
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  return null;
}
