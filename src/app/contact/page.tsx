import type { Metadata } from "next";
import Link from "next/link";
import { Clock, Mail, MapPin, MessageCircle, Phone } from "lucide-react";

import { ContactPanel, CallStoreButton, WhatsAppButton } from "@/components/store/contact-buttons";
import { Button } from "@/components/ui/button";
import { SectionHeading } from "@/components/ui/misc";
import { getAuthProviderCapabilities } from "@/lib/auth-providers";
import { getSessionUser } from "@/lib/auth";
import { store } from "@/config/store";
import { getStoreContact } from "@/lib/store-contact";

export const metadata: Metadata = {
  title: "Contact the store",
  description: `Get in touch with ${store.name} — call, WhatsApp or send a message about your order.`,
  alternates: { canonical: "/contact" },
};

/**
 * Contact page.
 *
 * Renders only the channels that actually exist: `CallStoreButton` and
 * `WhatsAppButton` return `null` when the store's numbers have not been
 * published to the app, and the page explains that rather than showing dead
 * links. This is the one page where the owner-configured details surface, so it
 * is also where the configuration gap is documented for the customer.
 */
export default async function ContactPage() {
  const [user, providers] = await Promise.all([
    getSessionUser(),
    getAuthProviderCapabilities(),
  ]);

  // Prefer the owner-published numbers from `store_settings`; env config is the
  // fallback when the DB row has no number.
  const published = await getStoreContact().catch(() => ({
    phone: store.phone,
    whatsapp: store.whatsapp,
  }));
  const phone = published.phone ?? store.phone;
  const whatsapp = published.whatsapp ?? store.whatsapp;

  return (
    <div className="shell flex flex-col gap-8 py-6 sm:py-8">
      <SectionHeading
        as="h1"
        title="How can we help?"
        subtitle={`Message, call or WhatsApp ${store.name}. We are a local shop, so you will usually speak to the people who packed your order.`}
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Call */}
        <ChannelCard
          icon={<Phone className="size-4" aria-hidden="true" />}
          title="Call the shop"
          body={
            phone
              ? `Speak to us directly on ${phone.display}.`
              : "Our phone number has not been published to the app yet."
          }
        >
          {phone ? (
            <CallStoreButton variant="secondary" phone={phone} />
          ) : (
            <ConfigNote>
              The owner needs to publish a phone number in the store settings for
              the call button to appear here.
            </ConfigNote>
          )}
        </ChannelCard>

        {/* WhatsApp */}
        <ChannelCard
          icon={<MessageCircle className="size-4" aria-hidden="true" />}
          title="WhatsApp"
          body={
            whatsapp
              ? `Send us a photo or a message on +${whatsapp.dial.replace(/^\+/, "")}.`
              : "WhatsApp is not connected to the app yet."
          }
        >
          {whatsapp ? (
            <WhatsAppButton variant="outline" whatsapp={whatsapp} />
          ) : (
            <ConfigNote>
              The owner needs to publish a WhatsApp number in the store settings to
              enable one-tap chat with the shop.
            </ConfigNote>
          )}
        </ChannelCard>

        {/* In-app */}
        <ChannelCard
          icon={<Mail className="size-4" aria-hidden="true" />}
          title="Message us in the app"
          body="Best for anything about a specific order — it keeps the whole conversation in one place, with the order attached."
        >
          {user ? (
            <Button asChild variant="secondary">
              <Link href="/support">Open support</Link>
            </Button>
          ) : (
            <div className="flex flex-wrap gap-2">
              <Button asChild variant="secondary">
                <Link href="/login?next=/support">Sign in to message us</Link>
              </Button>
            </div>
          )}
        </ChannelCard>
      </div>

      {/* Facts */}
      <section className="rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
        <h2 className="font-display text-h3 text-ink">The shop</h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2">
          <div className="flex items-start gap-3">
            <MapPin className="mt-0.5 size-4 shrink-0 text-leaf-600" aria-hidden="true" />
            <div>
              <dt className="text-caption text-ink-subtle">Address</dt>
              <dd className="mt-1 text-body text-ink-muted">
                {store.address.line1 ??
                  "The shop's street address has not been published to the app yet."}
              </dd>
            </div>
          </div>

          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 size-4 shrink-0 text-leaf-600" aria-hidden="true" />
            <div>
              <dt className="text-caption text-ink-subtle">Opening hours</dt>
              <dd className="mt-1 text-body text-ink-muted">
                {store.openingHours.length > 0
                  ? store.openingHours.join(" · ")
                  : "Hours are set by the owner and will appear here once published."}
              </dd>
            </div>
          </div>
        </dl>
      </section>

      {/* Fallback panel */}
      <section className="rounded-lg border border-line bg-surface-muted p-4 sm:p-5">
        <h2 className="font-display text-h3 text-ink">Reach us right now</h2>
        <p className="mt-1.5 mb-4 max-w-[62ch] text-small text-ink-muted">
          {phone || whatsapp
            ? "Whichever is easier for you."
            : "Direct calling and WhatsApp are not enabled yet, so in-app messages are the reliable route at the moment."}
        </p>
        <ContactPanel phone={phone} whatsapp={whatsapp} />
      </section>

      {!providers.google ? (
        <p className="text-small text-ink-subtle">
          Note for the shop: Google sign-in is currently disabled on the backend,
          so customers sign up with email and password. Enable the Google provider
          in the Supabase dashboard to turn the Google button on — no app change is
          needed.
        </p>
      ) : null}
    </div>
  );
}

function ChannelCard({
  icon,
  title,
  body,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  body: string;
  children: React.ReactNode;
}) {
  return (
    <section className="flex h-full flex-col rounded-lg border border-line bg-surface p-4 shadow-xs sm:p-5">
      <span className="grid size-8 place-items-center rounded-full bg-leaf-100 text-leaf-700">
        {icon}
      </span>
      <h2 className="mt-3 font-display text-h3 text-ink">{title}</h2>
      <p className="mt-1 flex-1 text-small text-ink-muted">{body}</p>
      <div className="mt-4">{children}</div>
    </section>
  );
}

function ConfigNote({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-md border border-dashed border-line-strong bg-surface-muted px-3 py-2.5 text-[11px] leading-relaxed text-ink-muted">
      {children}
    </p>
  );
}
