"use client";

import * as React from "react";

/**
 * Last-resort error boundary.
 *
 * This replaces the root layout, so it must ship its own `<html>`/`<body>` and
 * cannot rely on the design-system stylesheet having loaded. The inline styles
 * below are therefore intentional: a branded-enough, readable page that always
 * renders beats a prettier one that might not.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  React.useEffect(() => {
    if (process.env.NODE_ENV !== "production") {
      console.error("[vedant] global error:", error);
    }
  }, [error]);

  return (
    <html lang="en-IN">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "grid",
          placeItems: "center",
          background: "#FBF8F2",
          color: "#14211A",
          fontFamily:
            'ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif',
          padding: "24px",
        }}
      >
        <main style={{ maxWidth: "34rem", textAlign: "center" }}>
          <h1
            style={{
              fontSize: "1.5rem",
              lineHeight: 1.2,
              margin: "0 0 12px",
              letterSpacing: "-0.02em",
            }}
          >
            The shop is briefly unavailable
          </h1>
          <p style={{ margin: "0 0 8px", color: "#55635B", fontSize: "0.9375rem" }}>
            Something went wrong loading the application. Your order history and
            basket are safe.
          </p>
          <p style={{ margin: "0 0 24px", color: "#8A968F", fontSize: "0.8125rem" }}>
            Please try again in a moment.
          </p>
          <button
            type="button"
            onClick={reset}
            style={{
              background: "#2A6B46",
              color: "#fff",
              border: "none",
              borderRadius: "14px",
              padding: "12px 22px",
              fontSize: "0.9375rem",
              fontWeight: 500,
              cursor: "pointer",
            }}
          >
            Try again
          </button>
        </main>
      </body>
    </html>
  );
}
