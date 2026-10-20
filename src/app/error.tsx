"use client";

import * as React from "react";

import { ErrorState } from "@/components/ui/error-state";

/**
 * Route-level error boundary.
 *
 * The underlying error is logged for the developer in development and reported
 * nowhere else — never rendered to the customer, and never containing the
 * database message (which can leak schema details).
 */
export default function RouteError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  React.useEffect(() => {
    if (process.env.NODE_ENV !== "production") {
      console.error("[vedant] route error:", error);
    }
  }, [error]);

  return <ErrorState kind="unexpected" onRetry={reset} />;
}
