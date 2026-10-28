import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { AuthCard } from "@/components/auth/auth-card";
import { SignUpForm } from "@/components/auth/auth-forms";
import { getSessionUser } from "@/lib/auth";
import { getAuthProviderCapabilities } from "@/lib/auth-providers";

export const metadata: Metadata = {
  title: "Create your account",
  description:
    "Create a Vedant Fruit & Vegetables account to save addresses, track orders and reorder in two taps.",
  robots: { index: false, follow: true },
};

export default async function SignUpPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [user, providers, query] = await Promise.all([
    getSessionUser(),
    getAuthProviderCapabilities(),
    searchParams,
  ]);

  if (user) redirect("/account");

  const next = typeof query.next === "string" ? query.next : "/";

  return (
    <AuthCard
      title="Create account"
      subtitle={
        providers.emailAutoConfirm
          ? "You will be shopping in under a minute."
          : // Neutral on purpose: the email carries a code once a custom SMTP
            // provider is configured, and a link until then (Supabase locks the
            // template editor on the default mailer). Promising either one
            // specifically would be wrong half the time.
            "We will email you to confirm your address."
      }
      footer={
        <>
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-leaf-700 underline-offset-2 hover:underline"
          >
            Sign in
          </Link>
        </>
      }
    >
      <Suspense fallback={<div className="h-80" />}>
        <SignUpForm googleEnabled={providers.google} nextPath={next} />
      </Suspense>
    </AuthCard>
  );
}
