import type { Metadata, Viewport } from "next";
import { Bricolage_Grotesque, Inter } from "next/font/google";
import { Toaster } from "sonner";

import "./globals.css";

import { BottomNav } from "@/components/layout/bottom-nav";
import { SiteHeader, type HeaderCategory } from "@/components/layout/site-header";
import { StickyCartBar } from "@/components/cart/sticky-cart-bar";
import { CartProvider } from "@/components/cart/cart-provider";
import { ConsentGate } from "@/components/account/consent-gate";
import { ProfileSync } from "@/components/account/profile-sync";
import { PwaProvider } from "@/components/pwa/pwa-provider";
import { getCategories } from "@/lib/catalogue";
import { getProfileOrDefault } from "@/lib/auth";
import { getConsentState } from "@/lib/terms-server";
import { getDeliveryLocation } from "@/lib/addresses";
import { site, absoluteUrl } from "@/config/site";
import { store } from "@/config/store";

/**
 * Root layout.
 *
 * Fonts are self-hosted by `next/font` (downloaded at build time, served from
 * the app's own origin) — no runtime request to Google, no FOIT, zero CLS.
 *
 * This is a Server Component. It resolves the customer's session and the
 * (public, cacheable) category list once per navigation and passes only the
 * minimal serialisable props into the client components.
 */

const inter = Inter({
  subsets: ["latin"],
  variable: "--font-inter",
  display: "swap",
  fallback: ["ui-sans-serif", "system-ui", "Segoe UI", "Roboto", "Arial"],
});

const bricolage = Bricolage_Grotesque({
  subsets: ["latin"],
  variable: "--font-bricolage",
  display: "swap",
  weight: ["500", "600", "700"],
  fallback: ["ui-sans-serif", "system-ui", "Segoe UI", "Roboto", "Arial"],
});

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: { default: site.defaultTitle, template: site.titleTemplate },
  description: site.defaultDescription,
  applicationName: store.name,
  keywords: [...site.keywords],
  authors: [{ name: store.legalName }],
  creator: store.legalName,
  category: "shopping",
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    locale: site.locale,
    url: site.url,
    siteName: store.name,
    title: site.defaultTitle,
    description: site.defaultDescription,
  },
  twitter: {
    card: "summary_large_image",
    title: site.defaultTitle,
    description: site.defaultDescription,
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" },
  },
  manifest: "/manifest.webmanifest",
  icons: {
    icon: [{ url: "/icons/icon.svg", type: "image/svg+xml" }],
    apple: [{ url: "/icons/apple-touch-icon.png", sizes: "180x180" }],
  },
  appleWebApp: {
    capable: true,
    title: store.name,
    statusBarStyle: "default",
  },
  formatDetection: { telephone: true, address: true, email: true },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  // Never block zoom — an accessibility requirement, not a design preference.
  maximumScale: 5,
  userScalable: true,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#FBF8F2" },
    { media: "(prefers-color-scheme: dark)", color: "#FBF8F2" },
  ],
  colorScheme: "light",
};

export default async function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  const [{ user, displayName, avatarUrl }, categories, deliveryLocation] = await Promise.all([
    getProfileOrDefault().catch(() => ({
      user: null,
      profile: null,
      displayName: "",
      firstName: "",
      avatarUrl: null,
    })),
    // The catalogue is public; a failure here must still render a usable shell
    // (with the sign-in and cart flows intact) rather than a 500.
    getCategories().catch(() => [] as Awaited<ReturnType<typeof getCategories>>),
    getDeliveryLocation().catch(() => null),
  ]);

  /*
   * Consent is resolved on the server so the gate never flashes: a client-side
   * check would render the whole page first and then cover it with a modal.
   * `checked === false` means the lookup failed (for example the migration has
   * not been applied yet) — the gate then stays closed rather than locking every
   * customer out behind a dialog whose accept button also cannot write.
   */
  const consent = user ? await getConsentState(user.id) : null;
  const needsConsent = Boolean(consent?.checked && !consent.accepted);

  // The header shows the customer's chosen place, e.g. "Home · Pune 411001".
  const deliveryLabel = deliveryLocation
    ? [
        deliveryLocation.label,
        [deliveryLocation.city, deliveryLocation.postalCode]
          .filter(Boolean)
          .join(" "),
      ]
        .filter(Boolean)
        .join(" · ")
    : null;

  const headerCategories: HeaderCategory[] = categories.map((category) => ({
    id: category.id,
    name: category.name,
    glyph: category.glyph,
  }));

  return (
    <html lang="en-IN" className={`${inter.variable} ${bricolage.variable}`}>
      <body className="min-h-dvh bg-paper">
        <a
          href="#main"
          className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:rounded-md focus:bg-leaf-700 focus:px-4 focus:py-2.5 focus:text-small focus:font-medium focus:text-white"
        >
          Skip to content
        </a>

        <CartProvider userId={user?.id ?? null}>
          {/* Creates the profiles row on first authenticated visit — see
              docs/BACKEND_CONTRACT.md §7.3 (no trigger exists). */}
          {user ? <ProfileSync /> : null}

          {/* Blocks the app until the current terms version is accepted. */}
          {needsConsent ? <ConsentGate needed /> : null}

          <SiteHeader
            categories={headerCategories}
            user={user ? { id: user.id, email: user.email } : null}
            displayName={displayName}
            avatarUrl={avatarUrl}
            deliveryLabel={deliveryLabel}
          />

          <main id="main" className="pb-nav">
            {children}
          </main>

          <BottomNav />
          <StickyCartBar />

          {/* Service-worker registration, update prompt and install prompt.
              Inert in development — see the component's doc comment. */}
          <PwaProvider />

          <Toaster
            position="bottom-center"
            offset={96}
            gap={8}
            toastOptions={{
              classNames: {
                toast:
                  "!rounded-lg !border !border-line !bg-surface !text-ink !shadow-md",
                description: "!text-ink-muted",
                actionButton: "!bg-leaf-600 !text-white",
                cancelButton: "!bg-surface-muted !text-ink",
              },
            }}
          />
        </CartProvider>

        {/*
          Structured data for local-business SEO. Rendered once in the shell;
          per-product structured data is emitted by the product route.
          Fields with no owner-supplied value are omitted rather than faked.
        */}
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(buildLocalBusinessSchema()),
          }}
        />
      </body>
    </html>
  );
}

/* -------------------------------------------------------------------------- */

function buildLocalBusinessSchema() {
  const schema: Record<string, unknown> = {
    "@context": "https://schema.org",
    "@type": "GroceryStore",
    name: store.name,
    url: site.url,
    description: store.shortDescription,
    image: absoluteUrl("/icons/pwa-512.png"),
    priceRange: "₹₹",
    currenciesAccepted: "INR",
  };

  if (store.phone) {
    schema.telephone = store.phone.dial;
  }
  if (store.address.line1) {
    schema.address = {
      "@type": "PostalAddress",
      streetAddress: store.address.line1,
      addressCountry: store.address.country,
    };
  }
  if (store.openingHours.length > 0) {
    schema.openingHours = [...store.openingHours];
  }

  return schema;
}
