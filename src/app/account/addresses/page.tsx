import type { Metadata } from "next";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";

import { AddressManager } from "@/components/account/address-manager";
import { requireUser } from "@/lib/auth";
import { listAddressesForUser } from "@/lib/addresses";

export const metadata: Metadata = {
  title: "Saved addresses",
  description: "Add, edit and manage your delivery addresses.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

export default async function AddressesPage() {
  await requireUser("/account/addresses");
  const addresses = await listAddressesForUser();

  return (
    <div className="shell flex flex-col gap-5 py-6 sm:py-8">
      <div>
        <Link
          href="/account"
          className="inline-flex items-center gap-1.5 text-small text-ink-muted transition-colors hover:text-ink"
        >
          <ArrowLeft className="size-3.5" aria-hidden="true" />
          Your account
        </Link>
        <h1 className="mt-3 font-display text-h1 text-ink">Delivery addresses</h1>
        <p className="mt-1 max-w-[56ch] text-body text-ink-muted">
          We deliver to the address you pick at checkout. Your default is used
          automatically, and past orders keep the address they were delivered to.
        </p>
      </div>

      <div className="max-w-2xl">
        <AddressManager initialAddresses={addresses} />
      </div>
    </div>
  );
}
