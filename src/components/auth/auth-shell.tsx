import Link from "next/link";
import { BrandLogo } from "@/components/brand/logo";
import { store } from "@/config/store";

/**
 * Shared shell for the sign-in / sign-up / password pages.
 *
 * Two-column from `lg`: a brand panel (deep leaf) carrying the store's promise,
 * and the form. On mobile the brand panel collapses to nothing but the logo —
 * a customer on a phone came here to type, not to read.
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
}) {
  return (
    <div className="grid min-h-[70vh] lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      {/* Brand panel — desktop only. */}
      <aside className="relative hidden flex-col justify-between overflow-hidden bg-leaf-800 p-10 lg:flex">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(120%_90%_at_15%_10%,rgba(222,142,24,0.28),transparent_60%)]"
        />
        <Link href="/" className="relative w-fit">
          <BrandLogo tone="inverse" />
        </Link>

        <div className="relative max-w-[34ch]">
          <p className="font-display text-h2 text-white">
            You&apos;ve never tasted fruit this fresh.
          </p>
          <ul className="mt-5 flex flex-col gap-2.5 text-small text-white/80">
            <li>• Picked at the market this morning, delivered the same day</li>
            <li>• Save your addresses and reorder in two taps</li>
            <li>• Free delivery on orders over ₹500</li>
          </ul>
        </div>

        <p className="relative text-small text-white/60">
          {store.name} · Local delivery
        </p>
      </aside>

      {/* Form panel */}
      <div className="flex items-center justify-center px-4 py-10 sm:px-6 lg:py-14">
        <div className="w-full max-w-md">
          <div className="lg:hidden">
            <Link href="/" className="inline-block">
              <BrandLogo />
            </Link>
          </div>

          <h1 className="mt-6 font-display text-h1 text-ink lg:mt-0">{title}</h1>
          <p className="mt-1.5 text-body text-ink-muted">{subtitle}</p>

          <div className="mt-6">{children}</div>

          {footer ? (
            <div className="mt-6 border-t border-line pt-5 text-small text-ink-muted">
              {footer}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

/** Divider used between password auth and Google auth. */
export function OrDivider({ label = "or" }: { label?: string }) {
  return (
    <div className="my-4 flex items-center gap-3">
      <span className="h-px flex-1 bg-line" />
      <span className="text-caption text-ink-subtle">{label}</span>
      <span className="h-px flex-1 bg-line" />
    </div>
  );
}
