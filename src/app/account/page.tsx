import type { Metadata } from "next";
import Link from "next/link";
import { Bell, HeadphonesIcon, MapPin, ReceiptText } from "lucide-react";

import { AccountDangerZone } from "@/components/account/account-actions";
import { ProfileForm } from "@/components/account/profile-form";
import { Button } from "@/components/ui/button";
import { requireUser, getProfile } from "@/lib/auth";
import { getSupabaseServerClient } from "@/lib/supabase/server";
import { initials, maskPhone, pluralise } from "@/lib/format";
import { formatDate } from "@/lib/format";
import type { OrderRow } from "@/types/database";

export const metadata: Metadata = {
  title: "Your account",
  description: "Manage your details, addresses and orders.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Account overview.
 *
 * `getProfile()` may legitimately return `null` — the live database has no
 * trigger creating a `profiles` row (docs/BACKEND_CONTRACT.md §7.3). Instead of
 * crashing, the page renders the correct empty state and `ProfileSync` creates
 * the row on the client within a moment of landing.
 */
export default async function AccountPage() {
  const user = await requireUser("/account");
  const profile = await getProfile();

  const supabase = await getSupabaseServerClient();
  const [{ count: addressCount }, { data: recentOrders }] = await Promise.all([
    supabase.from("addresses").select("id", { count: "exact", head: true }),
    supabase
      .from("orders")
      .select("*")
      .order("placed_at", { ascending: false })
      .limit(3),
  ]);

  const orders = (recentOrders ?? []) as OrderRow[];
  const displayName =
    [profile?.first_name, profile?.last_name].filter(Boolean).join(" ") ||
    user.email?.split("@")[0] ||
    "there";

  return (
    <div className="shell flex flex-col gap-6 py-6 sm:py-8">
      <header className="flex items-center gap-4">
        <span
          aria-hidden="true"
          className="grid size-14 shrink-0 place-items-center rounded-full bg-leaf-100 font-display text-h3 text-leaf-700"
        >
          {initials(profile?.first_name, profile?.last_name, displayName)}
        </span>
        <div className="min-w-0">
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
      </header>

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

      {/* Details */}
      <section className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <h2 className="font-display text-h3 text-ink">Your details</h2>
        <p className="mt-1 mb-4 text-small text-ink-muted">
          We use these to reach you about deliveries.
        </p>
        {profile ? (
          <ProfileForm profile={profile} email={user.email} />
        ) : (
          <div className="rounded-md border border-dashed border-line-strong bg-surface-muted px-4 py-5 text-small text-ink-muted">
            <p>
              Your profile is being set up — this takes a moment. Refresh the page
              if it does not appear.
            </p>
            <Button asChild variant="outline" size="sm" className="mt-3">
              <Link href="/account">Refresh</Link>
            </Button>
          </div>
        )}
      </section>

      <AccountDangerZone />
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
