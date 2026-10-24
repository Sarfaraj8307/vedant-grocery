import * as React from "react";

/**
 * Auth card — the visual shell for sign-in, sign-up and password pages.
 *
 * A deep-green header carrying the page title, with the form panel overlapping
 * it on large rounded corners. The decorative circles are purely ornamental
 * (`aria-hidden`) and clipped by the header, which is what keeps them from
 * bleeding into the form.
 *
 * This replaced the previous two-column `AuthShell` on the login and signup
 * routes. The shell put the brand promise in a side panel that a phone never
 * showed; a single centred card reads the same on both and puts the form where
 * the thumb already is.
 */
export function AuthCard({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="relative isolate flex min-h-[75vh] items-center justify-center overflow-hidden px-4 py-8 sm:py-12">
      {/* Soft tinted backdrop, deliberately faint — the card is the subject. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -left-28 top-4 -z-10 size-80 rounded-full bg-leaf-100/80 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-24 bottom-0 -z-10 size-80 rounded-full bg-marigold-100/80 blur-3xl"
      />

      <div className="w-full max-w-[26rem]">
        <div className="overflow-hidden rounded-[2rem] bg-surface shadow-[0_28px_70px_-30px_rgba(26,46,26,0.45)] ring-1 ring-line/70">
          {/* Header */}
          <div className="relative overflow-hidden bg-leaf-700 px-6 pb-16 pt-9 text-center">
            <span
              aria-hidden="true"
              className="absolute -left-12 -top-10 size-36 rounded-full bg-marigold-300/85"
            />
            <span
              aria-hidden="true"
              className="absolute -right-8 top-6 size-24 rounded-full bg-leaf-400/55"
            />
            <h1 className="relative font-display text-h2 text-white">{title}</h1>
            {subtitle ? (
              <p className="relative mx-auto mt-1.5 max-w-[30ch] text-small text-white/80">
                {subtitle}
              </p>
            ) : null}
          </div>

          {/* Form panel — overlaps the header, as in the reference design. */}
          <div className="relative -mt-9 rounded-t-[1.75rem] bg-surface px-5 pb-7 pt-7 sm:px-7">
            {children}
          </div>
        </div>

        {footer ? (
          <div className="mt-5 text-center text-small text-ink-muted">{footer}</div>
        ) : null}
      </div>
    </div>
  );
}

/**
 * Input styling for the auth card: taller, softer and fill-only, so the fields
 * read as one surface with the panel rather than as boxed form controls.
 * Applied via `className` (tailwind-merge resolves the conflicts last-wins).
 */
export const authInputClass =
  "h-12 rounded-xl border-transparent bg-surface-muted px-4 shadow-none focus:border-leaf-600 focus:bg-surface";

/** The dark pill call-to-action used in the reference design. */
export const authSubmitClass =
  "h-12 rounded-xl bg-ink text-white shadow-none hover:bg-ink/90";
