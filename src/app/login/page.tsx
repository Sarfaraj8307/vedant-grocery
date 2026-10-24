import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { AuthCard } from "@/components/auth/auth-card";
import { LoginForm } from "@/components/auth/auth-forms";
import { getSessionUser } from "@/lib/auth";
import { getAuthProviderCapabilities } from "@/lib/auth-providers";

export const metadata: Metadata = {
  title: "Sign in",
  description: "Sign in to your Vedant Fruit & Vegetables account.",
  robots: { index: false, follow: true },
};

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const [user, providers, query] = await Promise.all([
    getSessionUser(),
    getAuthProviderCapabilities(),
    searchParams,
  ]);

  // Already signed in? Nothing to do here.
  if (user) {
    const next = typeof query.next === "string" ? query.next : "/account";
    redirect(next.startsWith("/") && !next.startsWith("//") ? next : "/account");
  }

  const error = typeof query.error === "string" ? query.error : null;

  return (
    <AuthCard
      title="Login Here"
      subtitle="Sign in to reorder favourites and track deliveries."
      footer={
        <>
          New to Vedant?{" "}
          <Link href="/signup" className="font-medium text-leaf-700 underline-offset-2 hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      {error ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {error}
        </p>
      ) : null}

      {/*
        `useSearchParams` is used inside the form, so it must be wrapped in
        Suspense for the page to remain statically renderable.
      */}
      <Suspense fallback={<div className="h-64" />}>
        <LoginForm googleEnabled={providers.google} />
      </Suspense>
    </AuthCard>
  );
}
