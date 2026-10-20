"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { MapPin, Pencil, Plus, Star, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/input";
import { RadioGroup, RadioGroupItem } from "@/components/ui/menu";
import {
  SheetBody,
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetRoot,
} from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { addressSchema, type AddressInput } from "@/lib/validation";
import { DEFAULT_CITY, DEFAULT_STATE } from "@/config/service-area";
import {
  createAddress,
  deleteAddress,
  formatAddressCity,
  formatAddressLine,
  setDefaultAddress,
  updateAddress,
} from "@/services/address-service";
import { cn } from "@/lib/utils";
import type { AddressRow } from "@/types/database";

/**
 * Address book — used by checkout and the account area.
 *
 * The picker is a Radix `RadioGroup`, so it is keyboard-navigable with arrow
 * keys and exposes `aria-checked` to a screen reader, rather than being a set of
 * divs with click handlers.
 *
 * The add/edit form is behind a sheet and validated with the schema that mirrors
 * the `addresses` CHECK constraints, so an invalid address never reaches the
 * database.
 */
export function AddressBook({
  addresses,
  selectedId,
  onSelect,
  onAddressesChange,
  mode = "select",
  className,
}: {
  addresses: AddressRow[];
  selectedId?: string | null;
  onSelect?: (address: AddressRow) => void;
  onAddressesChange?: (addresses: AddressRow[]) => void;
  /** `select` for checkout, `manage` for the account address page. */
  mode?: "select" | "manage";
  className?: string;
}) {
  const [editing, setEditing] = React.useState<AddressRow | null>(null);
  const [sheetOpen, setSheetOpen] = React.useState(false);

  function openAdd() {
    setEditing(null);
    setSheetOpen(true);
  }

  function openEdit(address: AddressRow) {
    setEditing(address);
    setSheetOpen(true);
  }

  function handleSaved(address: AddressRow) {
    const exists = addresses.some((a) => a.id === address.id);
    const next = exists
      ? addresses.map((a) => (a.id === address.id ? address : a))
      : [...addresses, address];
    onAddressesChange?.(next);
    onSelect?.(address);
    setSheetOpen(false);
    toast.success(exists ? "Address updated" : "Address saved");
  }

  async function handleDelete(address: AddressRow) {
    const result = await deleteAddress(address.id);
    if (!result.ok) {
      toast.error(result.error);
      return;
    }
    const next = addresses.filter((a) => a.id !== address.id);
    onAddressesChange?.(next);
    toast.success("Address removed");
  }

  async function handleMakeDefault(address: AddressRow) {
    const result = await setDefaultAddress(address.id);
    if (!result.ok) {
      toast.error(result.error);
      return;
    }
    onAddressesChange?.(
      addresses.map((a) => ({ ...a, is_default: a.id === address.id })),
    );
    toast.success("Default address updated");
  }

  const body = (
    <>
      {addresses.length === 0 ? (
        <p className="rounded-md border border-dashed border-line-strong bg-surface-muted px-4 py-5 text-center text-small text-ink-muted">
          You have not saved an address yet. Add one and we will remember it for
          next time.
        </p>
      ) : (
        <RadioGroup
          value={selectedId ?? undefined}
          onValueChange={(value) => {
            const found = addresses.find((a) => a.id === value);
            if (found) onSelect?.(found);
          }}
          className="flex flex-col gap-2"
          aria-label="Delivery address"
        >
          {addresses.map((address) => {
            const selected = address.id === selectedId;
            return (
              <div
                key={address.id}
                className={cn(
                  "flex items-start gap-3 rounded-md border-[1.5px] p-3 transition-colors",
                  selected
                    ? "border-leaf-600 bg-leaf-50"
                    : "border-line bg-surface hover:border-line-strong",
                )}
              >
                <RadioGroupItem
                  value={address.id}
                  id={`address-${address.id}`}
                  className="mt-0.5"
                />
                <div className="min-w-0 flex-1">
                  <label
                    htmlFor={`address-${address.id}`}
                    className="flex cursor-pointer flex-wrap items-center gap-2"
                  >
                    <span className="text-body font-medium text-ink">
                      {address.label}
                    </span>
                    {address.is_default ? (
                      <Badge variant="leaf" size="sm">
                        Default
                      </Badge>
                    ) : null}
                  </label>
                  <p className="mt-1 text-small text-ink-muted">
                    {address.full_name} · {address.phone}
                  </p>
                  <p className="mt-0.5 text-small text-ink-muted">
                    {formatAddressLine(address)}
                  </p>
                  <p className="text-small text-ink-subtle">
                    {formatAddressCity(address)}
                  </p>

                  <div className="mt-2 flex flex-wrap gap-1">
                    <Button
                      type="button"
                      variant="quiet"
                      size="sm"
                      onClick={() => openEdit(address)}
                    >
                      <Pencil className="size-3.5" aria-hidden="true" />
                      Edit
                    </Button>
                    {!address.is_default ? (
                      <Button
                        type="button"
                        variant="quiet"
                        size="sm"
                        onClick={() => void handleMakeDefault(address)}
                      >
                        <Star className="size-3.5" aria-hidden="true" />
                        Make default
                      </Button>
                    ) : null}
                    <Button
                      type="button"
                      variant="quiet"
                      size="sm"
                      className="text-danger hover:bg-danger-bg"
                      onClick={() => void handleDelete(address)}
                    >
                      <Trash2 className="size-3.5" aria-hidden="true" />
                      Remove
                    </Button>
                  </div>
                </div>
              </div>
            );
          })}
        </RadioGroup>
      )}

      <Button
        type="button"
        variant="outline"
        block
        className="mt-3 border-dashed"
        onClick={openAdd}
      >
        <Plus className="size-4" aria-hidden="true" />
        Add a new address
      </Button>
    </>
  );

  return (
    <div className={className}>
      {mode === "select" ? (
        <SheetRoot open={sheetOpen} onOpenChange={setSheetOpen}>
          {body}
          <SheetContent side="bottom" title={editing ? "Edit address" : "Add an address"}>
            <SheetHeader
              title={editing ? "Edit address" : "Add a delivery address"}
              description="We use this only to deliver your order."
            />
            <SheetBody>
              <AddressForm
                key={editing?.id ?? "new"}
                address={editing}
                onSaved={handleSaved}
                onCancel={() => setSheetOpen(false)}
              />
            </SheetBody>
          </SheetContent>
        </SheetRoot>
      ) : (
        <>
          {body}
          <SheetRoot open={sheetOpen} onOpenChange={setSheetOpen}>
            <SheetContent side="bottom" title={editing ? "Edit address" : "Add an address"}>
              <SheetHeader
                title={editing ? "Edit address" : "Add a delivery address"}
                description="We use this only to deliver your order."
              />
              <SheetBody>
                <AddressForm
                  key={editing?.id ?? "new"}
                  address={editing}
                  onSaved={handleSaved}
                  onCancel={() => setSheetOpen(false)}
                />
              </SheetBody>
            </SheetContent>
          </SheetRoot>
        </>
      )}
    </div>
  );
}

/* -------------------------------------------------------------------------- */
/* Form                                                                       */
/* -------------------------------------------------------------------------- */

function AddressForm({
  address,
  onSaved,
  onCancel,
}: {
  address: AddressRow | null;
  onSaved: (address: AddressRow) => void;
  onCancel: () => void;
}) {
  const [coords, setCoords] = React.useState<{ lat: number; lng: number } | null>(
    address?.latitude != null && address?.longitude != null
      ? { lat: address.latitude, lng: address.longitude }
      : null,
  );
  const [locating, setLocating] = React.useState(false);
  const [locError, setLocError] = React.useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<AddressInput>({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      label: address?.label ?? "Home",
      full_name: address?.full_name ?? "",
      phone: address?.phone ?? "",
      house_number: address?.house_number ?? "",
      street: address?.street ?? "",
      landmark: address?.landmark ?? "",
      // New addresses are pre-filled with our service area (Pune, Maharashtra)
      // so the customer only types the street — the two fields that decide
      // whether we can deliver are already correct. Both stay editable.
      city: address?.city ?? DEFAULT_CITY,
      state: address?.state ?? DEFAULT_STATE,
      postal_code: address?.postal_code ?? "",
      latitude: address?.latitude ?? null,
      longitude: address?.longitude ?? null,
    },
  });

  function useMyLocation() {
    if (!navigator.geolocation) {
      setLocError("Geolocation is not supported on this device.");
      return;
    }
    setLocating(true);
    setLocError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({ lat: pos.coords.latitude, lng: pos.coords.longitude });
        setLocating(false);
        toast.success("Location captured — it will be saved with your address.");
      },
      (err) => {
        setLocError(err.message || "Could not get your location. Please allow location permission.");
        setLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 },
    );
  }

  async function onSubmit(values: AddressInput) {
    const payload: AddressInput = {
      ...values,
      latitude: coords?.lat ?? null,
      longitude: coords?.lng ?? null,
    };
    const result = address
      ? await updateAddress(address.id, payload)
      : await createAddress(payload);

    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    onSaved(result.data);
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-1">
      {errors.root ? (
        <p
          role="alert"
          className="mb-2 rounded-md border border-danger-bg bg-danger-bg px-3 py-2 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <div className="rounded-md border border-line bg-surface-muted px-3 py-2.5">
        <p className="text-small font-medium text-ink">Delivery location</p>
        <p className="mt-0.5 text-small text-ink-muted">
          Enable location so the delivery partner can find you on the map.
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" size="sm" onClick={useMyLocation} loading={locating} loadingLabel="Locating…">
            <MapPin className="size-4" aria-hidden="true" />
            {coords ? "Update location" : "Use my location"}
          </Button>
          {coords ? (
            <span className="text-small text-success">
              ✓ {coords.lat.toFixed(5)}, {coords.lng.toFixed(5)}
            </span>
          ) : null}
        </div>
        {locError ? <p className="mt-1.5 text-small text-danger">{locError}</p> : null}
        {coords ? (
          <a
            href={`https://www.google.com/maps/search/?api=1&query=${coords.lat},${coords.lng}`}
            target="_blank"
            rel="noopener noreferrer"
            className="mt-1 inline-block text-small text-leaf-700 underline-offset-2 hover:underline"
          >
            Preview on Google Maps
          </a>
        ) : null}
      </div>

      <div className="grid gap-x-3 sm:grid-cols-2">
        <Field label="Address name" htmlFor="label" error={errors.label?.message} required>
          <Input
            id="label"
            placeholder="Home, Office…"
            autoComplete="off"
            invalid={Boolean(errors.label)}
            {...register("label")}
          />
        </Field>

        <Field
          label="Full name"
          htmlFor="full_name"
          error={errors.full_name?.message}
          required
        >
          <Input
            id="full_name"
            autoComplete="name"
            invalid={Boolean(errors.full_name)}
            {...register("full_name")}
          />
        </Field>

        <Field label="Phone" htmlFor="phone" error={errors.phone?.message} required>
          <Input
            id="phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="10-digit mobile number"
            invalid={Boolean(errors.phone)}
            {...register("phone")}
          />
        </Field>

        <Field
          label="House / flat number"
          htmlFor="house_number"
          error={errors.house_number?.message}
          hint="Optional but helps our delivery partner"
        >
          <Input
            id="house_number"
            autoComplete="address-line2"
            invalid={Boolean(errors.house_number)}
            {...register("house_number")}
          />
        </Field>

        <Field label="Street" htmlFor="street" error={errors.street?.message} required>
          <Input
            id="street"
            autoComplete="address-line1"
            invalid={Boolean(errors.street)}
            {...register("street")}
          />
        </Field>

        <Field
          label="Landmark"
          htmlFor="landmark"
          error={errors.landmark?.message}
          hint="Optional — a nearby shop or building"
        >
          <Input
            id="landmark"
            invalid={Boolean(errors.landmark)}
            {...register("landmark")}
          />
        </Field>

        <Field
          label="City"
          htmlFor="city"
          error={errors.city?.message}
          hint={`We deliver in ${DEFAULT_CITY}, ${DEFAULT_STATE}`}
          required
        >
          <Input
            id="city"
            autoComplete="address-level2"
            invalid={Boolean(errors.city)}
            {...register("city")}
          />
        </Field>

        <Field label="State" htmlFor="state" error={errors.state?.message} required>
          <Input
            id="state"
            autoComplete="address-level1"
            invalid={Boolean(errors.state)}
            {...register("state")}
          />
        </Field>

        <Field
          label="Pincode"
          htmlFor="postal_code"
          error={errors.postal_code?.message}
          required
        >
          <Input
            id="postal_code"
            inputMode="numeric"
            autoComplete="postal-code"
            invalid={Boolean(errors.postal_code)}
            {...register("postal_code")}
          />
        </Field>
      </div>

      <SheetFooter className="mt-3">
        <Button type="button" variant="quiet" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          type="submit"
          loading={isSubmitting}
          loadingLabel="Saving address…"
        >
          <MapPin className="size-4" aria-hidden="true" />
          {address ? "Save changes" : "Save address"}
        </Button>
      </SheetFooter>

    </form>
  );
}
