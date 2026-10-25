"use client";

import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/input";
import { PasswordInput } from "@/components/auth/password-input";
import { changePasswordSchema, type ChangePasswordInput } from "@/lib/validation";
import { changePassword } from "@/services/auth-service";

/**
 * Change password.
 *
 * Requests the current password plus the new one (typed twice, and the two must
 * match). The service verifies the current password against Supabase before it
 * lets a new one through, so a stale or forgotten password is rejected rather
 * than silently overwritten.
 */
export function ChangePasswordForm() {
  const router = useRouter();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ChangePasswordInput>({
    resolver: zodResolver(changePasswordSchema),
  });

  async function onSubmit(values: ChangePasswordInput) {
    const result = await changePassword({
      currentPassword: values.currentPassword,
      newPassword: values.newPassword,
    });

    if (!result.ok) {
      setError("currentPassword", { message: result.error });
      return;
    }

    reset();
    toast.success("Your password has been updated");
    router.refresh();
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      <Field
        label="Current password"
        htmlFor="current-password"
        error={errors.currentPassword?.message}
        required
      >
        <PasswordInput
          id="current-password"
          autoComplete="current-password"
          placeholder="Your current password"
          invalid={Boolean(errors.currentPassword)}
          {...register("currentPassword")}
        />
      </Field>

      <div className="grid gap-x-3 sm:grid-cols-2">
        <Field
          label="New password"
          htmlFor="new-password"
          error={errors.newPassword?.message}
          hint="At least 8 characters."
          required
        >
          <PasswordInput
            id="new-password"
            autoComplete="new-password"
            placeholder="Choose a new password"
            invalid={Boolean(errors.newPassword)}
            {...register("newPassword")}
          />
        </Field>

        <Field
          label="Confirm new password"
          htmlFor="confirm-password"
          error={errors.confirmPassword?.message}
          required
        >
          <PasswordInput
            id="confirm-password"
            autoComplete="new-password"
            placeholder="Type it again"
            invalid={Boolean(errors.confirmPassword)}
            {...register("confirmPassword")}
          />
        </Field>
      </div>

      <Button
        type="submit"
        className="mt-1"
        loading={isSubmitting}
        loadingLabel="Updating…"
      >
        Update password
      </Button>
    </form>
  );
}
