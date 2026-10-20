import type { Metadata } from "next";

import { ErrorState } from "@/components/ui/error-state";

export const metadata: Metadata = {
  title: "Page not found",
  robots: { index: false, follow: true },
};

/**
 * 404. Rendered for unknown routes and for `notFound()` calls from a product,
 * category or order that does not exist (or is not visible to this customer).
 */
export default function NotFound() {
  return <ErrorState kind="notFound" />;
}
