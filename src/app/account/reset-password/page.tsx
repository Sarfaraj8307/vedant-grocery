import type { Metadata } from "next";
import { redirect } from "next/navigation";

import { AuthShell } from "@/components/auth/auth-shell";
import { ResetPasswordForm } from "@/components/auth/auth-forms";
import { getSessionUser } from "@/lib/auth";

export const metadata: Metadata = {
  title: "Choose a new password",
  robots: { index: false, follow: false },
};

/**
 * Landed on after the customer clicks the recovery link in their email.
 * `supabase.auth.exchangeCodeForSession` in `/auth/callback` has already
 * established a session, so this page is only reachable while signed in.
 */
export default async function ResetPasswordPage() {
  const user = await getSessionUser();
  if (!user) redirect("/forgot-password");

  return (
    <AuthShell
      title="Choose a new password"
      subtitle="Pick something you have not used here before."
    >
      <ResetPasswordForm />
    </AuthShell>
  );
}
