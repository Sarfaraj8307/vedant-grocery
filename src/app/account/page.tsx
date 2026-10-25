import type { Metadata } from "next";
import Link from "next/link";
import { Bell, CheckCircle2, FileText, HeadphonesIcon, MapPin, ReceiptText, ShieldCheck } from "lucide-react";

import { ChangePasswordForm } from "@/components/account/change-password-form";
import { ProfileForm } from "@/components/account/profile-form";
import { SignOutButton } from "@/components/account/sign-out-dialog";
import { UserAvatar } from "@/components/account/user-avatar";
import { requireUser, getProfile } from "@/lib/auth";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { getConsentState } from "@/lib/terms-server";
import { TERMS_SOURCE_LABEL, TERMS_VERSION } from "@/lib/terms";
import { formatDate, maskPhone, pluralise } from "@/lib/format";
import type { OrderRow } from "@/types/database";

export const metadata: Metadata = {
  title: "Your account",
  description: "Manage your addresses, orders and account.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Account overview.
 *
 * `getProfile()` may legitimately return `null` — the live database has no
 * trigger creating a `profiles` row (docs/BACKEND_CONTRACT.md §7.3). The header
 * therefore falls back to the name carried in `user_metadata`, so a missing row
 * never leaves the customer greeted as their email local-part.
 *
 * The "My Account" section renders the editable profile form (`ProfileForm`) for
 * first name, last name, phone and email. Email is read-only — changing the
 * address on an account goes through support. When the live database has not yet
 * produced the `profiles` row, the section shows a short setup note instead of a
 * fake control.
 *
 * `?confirmed=1` is set by `/auth/callback` when an email link is the reason the
 * session exists. Without the banner the customer clicks "confirm" in their inbox
 * and lands on a normal-looking page with no acknowledgement that anything
 * happened — which reads as "did that work?".
 */
export default async function AccountPage({
  searchParams,
}: {
  searchParams: Promise<{ confirmed?: string }>;
}) {
  const { confirmed } = await searchParams;
  const user = await requireUser("/account");
  const profile = await getProfile();

  const supabase = await getSupabaseServerClient();
  const [{ count: addressCount }, { data: recentOrders }, consent] = await Promise.all([
    supabase.from("addresses").select("id", { count: "exact", head: true }),
    supabase
      .from("orders")
      .select("*")
      .order("placed_at", { ascending: false })
      .limit(3),
    getConsentState(user.id),
  ]);

  const orders = (recentOrders ?? []) as OrderRow[];
  const displayName =
    [
      profile?.first_name?.trim() || user.firstName,
      profile?.last_name?.trim() || user.lastName,
    ]
      .filter(Boolean)
      .join(" ") ||
    user.email?.split("@")[0] ||
    "there";

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <header className="flex flex-wrap items-center gap-4">
        <UserAvatar name={displayName} url={user.avatarUrl} size={56} />
        <div className="min-w-0 flex-1">
          <h1 className="font-display text-h1 text-ink">{displayName}</h1>
          <p className="mt-0.5 truncate text-small text-ink-muted">
            {user.email}
            {profile?.phone ? ` · ${maskPhone(profile.phone)}` : ""}
          </p>
          {profile ? (
            <p className="mt-0.5 text-small text-ink-subtle">
              @{profile.username} · member since {formatDate(profile.created_at)}
            </p>
          ) : null}
        </div>
        <SignOutButton size="sm" className="shrink-0" />
      </header>

      {confirmed === "1" ? (
        <p
          role="status"
          className="flex items-start gap-2.5 rounded-lg border border-leaf-200 bg-leaf-50 px-4 py-3 text-small text-leaf-800"
        >
          <CheckCircle2 className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <span>
            <span className="font-medium">Your email is confirmed.</span> You are signed
            in and can start ordering.
          </span>
        </p>
      ) : null}

      {/* Quick links */}
      <nav aria-label="Account sections">
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <QuickLink
            href="/orders"
            icon={<ReceiptText className="size-4" aria-hidden="true" />}
            title="Your orders"
            detail={
              orders.length > 0
                ? `Last order ${formatDate(orders[0]!.placed_at)}`
                : "No orders yet"
            }
          />
          <QuickLink
            href="/account/addresses"
            icon={<MapPin className="size-4" aria-hidden="true" />}
            title="Addresses"
            detail={pluralise(addressCount ?? 0, "saved address", "saved addresses")}
          />
          <QuickLink
            href="/notifications"
            icon={<Bell className="size-4" aria-hidden="true" />}
            title="Notifications"
            detail="Order updates and announcements"
          />
          <QuickLink
            href="/support"
            icon={<HeadphonesIcon className="size-4" aria-hidden="true" />}
            title="Help &amp; support"
            detail="Problems, missing items, questions"
          />
        </ul>
      </nav>

      {/* My Account — the editable personal details (name, surname, phone,
          email). Email is read-only because changing it goes through support. */}
      <section
        aria-labelledby="my-account-heading"
        className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5"
      >
        <h2 id="my-account-heading" className="font-display text-h3 text-ink">
          My Account
        </h2>
        <p className="mt-1 text-small text-ink-muted">
          Your name and contact details, and how we reach you about orders.
        </p>
        <div className="mt-4">
          {profile ? (
            <ProfileForm profile={profile} email={user.email} />
          ) : (
            <p className="text-small text-ink-muted">
              We are setting up your profile — refresh in a moment to edit your
              details.
            </p>
          )}
        </div>
      </section>

      {/* Change password — requires the current password, so it lives on the
          authenticated account page rather than a public route. */}
      <section
        aria-labelledby="change-password-heading"
        className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5"
      >
        <h2 id="change-password-heading" className="font-display text-h3 text-ink">
          Change password
        </h2>
        <p className="mt-1 text-small text-ink-muted">
          Keep your sign-in secure. You will need your current password.
        </p>
        <div className="mt-4 max-w-lg">
          <ChangePasswordForm />
        </div>
      </section>

      {/* Terms and privacy — where they are, and what this customer accepted. */}
      <section
        aria-labelledby="legal-heading"
        className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5"
      >
        <h2 id="legal-heading" className="font-display text-h3 text-ink">
          Terms &amp; privacy
        </h2>
        <p className="mt-1 text-small text-ink-muted">
          What you agreed to, and where to read it in full.
        </p>

        <div className="mt-4 grid gap-2 sm:grid-cols-2">
          <LegalLink
            href="/terms"
            icon={<FileText className="size-4" aria-hidden="true" />}
            title="Terms &amp; conditions"
            detail="How ordering, pricing and delivery work"
          />
          <LegalLink
            href="/privacy"
            icon={<ShieldCheck className="size-4" aria-hidden="true" />}
            title="Privacy policy"
            detail="What we store and why"
          />
        </div>

        <div className="mt-4 rounded-md border border-line bg-surface-muted px-3 py-3 text-small">
          {consent.accepted && consent.current ? (
            <p className="text-ink-muted">
              You accepted version{" "}
              <span className="font-medium text-ink">{consent.current.terms_version}</span>{" "}
              of the terms on{" "}
              <span className="font-medium text-ink">
                {formatDate(consent.current.accepted_at)}
              </span>
              , {TERMS_SOURCE_LABEL[consent.current.source]}.
            </p>
          ) : consent.latest ? (
            <p className="text-ink-muted">
              You accepted an earlier version of the terms on{" "}
              <span className="font-medium text-ink">
                {formatDate(consent.latest.accepted_at)}
              </span>
              . The current version ({TERMS_VERSION}) has not been accepted yet —
              you will be asked next time you sign in.
            </p>
          ) : (
            <p className="text-ink-muted">
              We have no acceptance recorded for the current terms ({TERMS_VERSION}).
              You will be asked to accept them next time you sign in.
            </p>
          )}
        </div>
      </section>

    </div>
  );
}

function QuickLink({
  href,
  icon,
  title,
  detail,
}: {
  href: string;
  icon: React.ReactNode;
  title: string;
  detail: string;
}) {
  return (
    <li>
      <Link
        href={href}
        className="flex h-full items-start gap-3 rounded-lg border border-line bg-surface p-4 shadow-xs transition-[box-shadow,border-color] duration-[var(--motion-base)] hover:border-line-strong hover:shadow-sm"
      >
        <span className="grid size-8 shrink-0 place-items-center rounded-full bg-leaf-100 text-leaf-700">
          {icon}
        </span>
        <span className="min-w-0">
          <span className="block text-body font-medium text-ink">{title}</span>
          <span className="mt-0.5 block text-small text-ink-muted">{detail}</span>
        </span>
      </Link>
    </li>
  );
}

function LegalLink({
  href,
  icon,
  title,
  detail,
}: {
  href: string;
  icon: React.ReactNode;
  title: string;
  detail: string;
}) {
  return (
    <Link
      href={href}
      className="flex items-center gap-3 rounded-md border border-line px-3 py-2.5 transition-colors hover:border-line-strong hover:bg-surface-muted"
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-full bg-leaf-100 text-leaf-700">
        {icon}
      </span>
      <span className="min-w-0">
        <span className="block text-small font-medium text-ink">{title}</span>
        <span className="block truncate text-caption text-ink-muted">{detail}</span>
      </span>
    </Link>
  );
}
