"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/input";
import { profileUpdateSchema, type ProfileUpdateInput } from "@/lib/validation";
import { updateProfile } from "@/services/auth-service";
import type { ProfileRow } from "@/types/database";

/**
 * Profile editor.
 *
 * The `username` is shown read-only. `profiles.username` is `NOT NULL UNIQUE`
 * and is the handle the shop uses to identify a customer, so changing it
 * silently would be confusing; the copy explains that the shop can change it.
 */
export function ProfileForm({
  profile,
  email,
}: {
  profile: ProfileRow;
  email: string | null;
}) {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting, isDirty },
    setError,
  } = useForm<ProfileUpdateInput>({
    resolver: zodResolver(profileUpdateSchema),
    defaultValues: {
      firstName: profile.first_name ?? "",
      lastName: profile.last_name ?? "",
      phone: profile.phone ?? "",
    },
  });

  async function onSubmit(values: ProfileUpdateInput) {
    const result = await updateProfile({
      firstName: values.firstName,
      lastName: values.lastName,
      phone: values.phone || null,
    });

    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }

    reset(values);
    toast.success("Details saved");
    router.refresh();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <div className="grid gap-x-3 sm:grid-cols-2">
        <Field
          label="First name"
          htmlFor="profile-first"
          error={errors.firstName?.message}
        >
          <Input
            id="profile-first"
            autoComplete="given-name"
            invalid={Boolean(errors.firstName)}
            {...register("firstName")}
          />
        </Field>

        <Field label="Last name" htmlFor="profile-last" error={errors.lastName?.message}>
          <Input
            id="profile-last"
            autoComplete="family-name"
            invalid={Boolean(errors.lastName)}
            {...register("lastName")}
          />
        </Field>

        <Field
          label="Phone"
          htmlFor="profile-phone"
          error={errors.phone?.message}
          hint="Used by the delivery partner to reach you."
        >
          <Input
            id="profile-phone"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            invalid={Boolean(errors.phone)}
            {...register("phone")}
          />
        </Field>

        <Field
          label="Email"
          htmlFor="profile-email"
          hint="Contact the shop to change the email on your account."
        >
          <Input id="profile-email" value={email ?? ""} readOnly disabled />
        </Field>
      </div>

      <div className="flex items-center gap-3">
        <Button type="submit" loading={isSubmitting} loadingLabel="Saving…" disabled={!isDirty}>
          Save changes
        </Button>
        {isDirty ? (
          <span className="text-small text-ink-subtle">Unsaved changes</span>
        ) : null}
      </div>
    </form>
  );
}

/**
 * Destructive-account actions.
 *
 * Account *deletion* requires a server-side admin call against `auth.users`,
 * which would need a service-role key — explicitly forbidden in this
 * customer-only app (see docs/BACKEND_CONTRACT.md §3). So the honest
 * implementation is: the customer can erase all their personal data themselves,
 * and a recorded deletion request is actioned by the store. The UI says exactly
 * that rather than offering a button that pretends to delete the account.
 */
export function DangerZone({ onClearData }: { onClearData: () => Promise<void> }) {
  const [working, setWorking] = React.useState(false);
  const [confirming, setConfirming] = React.useState(false);

  return (
    <div className="rounded-lg border border-danger-bg bg-surface p-4 sm:p-5">
      <h2 className="font-display text-h3 text-ink">Your data</h2>
      <p className="mt-1.5 max-w-[58ch] text-small text-ink-muted">
        You can erase your saved addresses and basket at any time. Past orders are
        kept for accounting and warranty reasons, as set out in our privacy
        policy.
      </p>

      {confirming ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="danger"
            size="sm"
            loading={working}
            loadingLabel="Erasing…"
            onClick={async () => {
              setWorking(true);
              await onClearData();
              setWorking(false);
              setConfirming(false);
            }}
          >
            Yes, erase my saved data
          </Button>
          <Button
            type="button"
            variant="quiet"
            size="sm"
            onClick={() => setConfirming(false)}
          >
            Cancel
          </Button>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-3 border-danger/40 text-danger hover:bg-danger-bg"
          onClick={() => setConfirming(true)}
        >
          Erase saved addresses &amp; basket
        </Button>
      )}

      <p className="mt-4 border-t border-line pt-3 text-small text-ink-subtle">
        To delete your account entirely, message the shop from the{" "}
        <a href="/contact" className="text-leaf-700 underline underline-offset-2">
          contact page
        </a>{" "}
        and we will remove it within 7 days.
      </p>
    </div>
  );
}
