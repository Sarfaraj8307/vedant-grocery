import Link from "next/link";
import { AlertTriangle } from "lucide-react";

/**
 * Shared layout for the privacy policy and terms pages.
 *
 * These documents describe what the application **actually does** — the data it
 * stores, who can read it, and how orders behave. They are accurate with respect
 * to the implementation but are not a substitute for legal review; the banner
 * says so plainly rather than pretending otherwise.
 */
export type LegalSection = {
  id: string;
  heading: string;
  body: React.ReactNode;
};

export function LegalPage({
  title,
  updated,
  intro,
  sections,
  needsReview = true,
}: {
  title: string;
  updated: string;
  intro: string;
  sections: LegalSection[];
  needsReview?: boolean;
}) {
  return (
    <div className="shell py-6 sm:py-8">
      <div className="mx-auto max-w-[72ch]">
        <h1 className="font-display text-h1 text-ink">{title}</h1>
        <p className="mt-1.5 text-small text-ink-subtle">
          Last updated {updated}
        </p>
        <p className="mt-4 text-body text-ink-muted">{intro}</p>

        {needsReview ? (
          <p className="mt-5 flex items-start gap-2.5 rounded-md border border-warning-bg bg-warning-bg px-3.5 py-3 text-small text-warning">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            <span>
              <strong className="font-semibold">Owner action required.</strong>{" "}
              This document accurately describes how the application handles data
              and orders, but it has not been reviewed by a lawyer. Have it checked
              before you rely on it commercially.
            </span>
          </p>
        ) : null}

        <nav aria-label="On this page" className="mt-7 border-y border-line py-3">
          <ol className="flex flex-wrap gap-x-4 gap-y-1.5 text-small">
            {sections.map((section, index) => (
              <li key={section.id}>
                <a
                  href={`#${section.id}`}
                  className="text-ink-muted underline-offset-2 hover:text-leaf-700 hover:underline"
                >
                  {index + 1}. {section.heading}
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <div className="mt-8 flex flex-col gap-8">
          {sections.map((section, index) => (
            <section key={section.id} id={section.id} className="scroll-mt-24">
              <h2 className="font-display text-h2 text-ink">
                {index + 1}. {section.heading}
              </h2>
              <div className="mt-2.5 flex flex-col gap-3 text-body text-ink-muted">
                {section.body}
              </div>
            </section>
          ))}
        </div>

        <div className="mt-10 rounded-lg border border-line bg-surface-muted p-4">
          <p className="text-small text-ink-muted">
            Questions about this document?{" "}
            <Link
              href="/contact"
              className="text-leaf-700 underline underline-offset-2"
            >
              Contact the shop
            </Link>{" "}
            and we will answer them.
          </p>
        </div>
      </div>
    </div>
  );
}

/** Bulleted list with consistent spacing inside legal prose. */
export function LegalList({ items }: { items: string[] }) {
  return (
    <ul className="flex flex-col gap-2">
      {items.map((item) => (
        <li key={item} className="flex gap-2.5">
          <span aria-hidden="true" className="mt-2 size-1 shrink-0 rounded-full bg-ink-subtle" />
          <span>{item}</span>
        </li>
      ))}
    </ul>
  );
}
