"use client";

import * as React from "react";
import { AddressBook } from "@/components/checkout/address-book";
import type { AddressRow } from "@/types/database";

/**
 * Account address book.
 *
 * Keeps the list in local state after the initial server render so an
 * add/edit/delete is reflected immediately without a full round-trip, while the
 * server remains the source of truth (RLS scopes every write to the caller).
 */
export function AddressManager({
  initialAddresses,
}: {
  initialAddresses: AddressRow[];
}) {
  const [addresses, setAddresses] = React.useState(initialAddresses);

  return (
    <AddressBook
      addresses={addresses}
      onAddressesChange={setAddresses}
      mode="manage"
    />
  );
}
