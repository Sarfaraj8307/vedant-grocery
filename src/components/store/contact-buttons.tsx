import Link from "next/link";
import { MessageCircle, Phone } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  hasPhone,
  hasWhatsApp,
  store,
  whatsappLink,
  type StoreContactChannel,
} from "@/config/store";

/**
 * Optional overrides for the store's published contact numbers.
 *
 * When provided, `phone` / `whatsapp` are used **instead of** the environment
 * fallback (`store.phone` / `store.whatsapp`). `undefined` means "use the env
 * config"; `null` means "explicitly none" (e.g. a resolved DB row with no
 * number). This lets server components pass the owner-maintained `store_settings`
 * numbers through to customers.
 */

/**
 * Call store / Chat on WhatsApp.
 *
 * HONESTY RULE (brief §47): a button must never do nothing. The owner has not
 * supplied a phone number or WhatsApp number yet, so these components render
 * **nothing** rather than a dead `tel:` link — and `ContactPanel` explains how to
 * enable them. As soon as `NEXT_PUBLIC_STORE_PHONE` /
 * `NEXT_PUBLIC_STORE_WHATSAPP` (or the future `private.shop_settings` fields) are
 * populated, the real buttons appear everywhere with no code change.
 */

const VARIANT_STYLES = {
  primary: { variant: "primary" as const, size: "md" as const },
  secondary: { variant: "secondary" as const, size: "md" as const },
  accent: { variant: "accent" as const, size: "md" as const },
  quiet: { variant: "quiet" as const, size: "sm" as const },
  outline: { variant: "outline" as const, size: "md" as const },
};

export function CallStoreButton({
  variant = "secondary",
  label,
  className,
  phone,
}: {
  variant?: keyof typeof VARIANT_STYLES;
  label?: string;
  className?: string;
  /** Override the env-config phone (from `store_settings`). */
  phone?: StoreContactChannel | null;
}) {
  const effective = phone !== undefined ? phone : store.phone;
  if (!effective) return null;
  const href = `tel:${effective.dial}`;
  const styles = VARIANT_STYLES[variant];

  return (
    <Button asChild {...styles} className={className}>
      <a href={href} aria-label={`Call ${store.name}`}>
        <Phone className="size-4" aria-hidden="true" />
        {label ?? `Call ${effective.display}`}
      </a>
    </Button>
  );
}

export function WhatsAppButton({
  variant = "quiet",
  label = "Chat on WhatsApp",
  message,
  className,
  whatsapp,
}: {
  variant?: keyof typeof VARIANT_STYLES;
  label?: string;
  message?: string;
  className?: string;
  /** Override the env-config WhatsApp number (from `store_settings`). */
  whatsapp?: StoreContactChannel | null;
}) {
  const effective = whatsapp !== undefined ? whatsapp : store.whatsapp;
  if (!effective) return null;
  const digits = effective.dial.replace(/\D/g, "");
  const href = `https://wa.me/${digits}?text=${encodeURIComponent(
    message ?? `Hi ${store.name}, I'd like to ask about my order.`,
  )}`;
  const styles = VARIANT_STYLES[variant];

  return (
    <Button asChild {...styles} className={className}>
      <a href={href} target="_blank" rel="noopener noreferrer">
        <MessageCircle className="size-4" aria-hidden="true" />
        {label}
      </a>
    </Button>
  );
}

/**
 * The contact panel used on the home page and `/contact`.
 * Explains the current state instead of pretending.
 */
export function ContactPanel({
  className,
  phone,
  whatsapp,
}: {
  className?: string;
  /** Override the env-config phone (from `store_settings`). */
  phone?: StoreContactChannel | null;
  /** Override the env-config WhatsApp number (from `store_settings`). */
  whatsapp?: StoreContactChannel | null;
}) {
  const phoneReady = (phone !== undefined ? phone : store.phone) !== null;
  const whatsappReady = (whatsapp !== undefined ? whatsapp : store.whatsapp) !== null;

  return (
    <div className={className}>
      {phoneReady && whatsappReady ? (
        <div className="flex flex-wrap items-center gap-2.5">
          <CallStoreButton variant="secondary" phone={phone} />
          <WhatsAppButton variant="outline" whatsapp={whatsapp} />
        </div>
      ) : (
        <div className="flex flex-col gap-2.5">
          <div className="flex flex-wrap items-center gap-2.5">
            {phoneReady ? (
              <CallStoreButton variant="secondary" phone={phone} />
            ) : null}
            {whatsappReady ? (
              <WhatsAppButton variant="outline" whatsapp={whatsapp} />
            ) : null}
            <Button asChild variant="outline">
              <Link href="/contact">Contact the store</Link>
            </Button>
          </div>
          <p className="max-w-[52ch] text-small text-ink-muted">
            Direct calling and WhatsApp are not enabled yet — the store&apos;s
            contact numbers have not been published to the app. You can still
            reach us from the{" "}
            <Link href="/contact" className="text-leaf-700 underline underline-offset-2">
              contact page
            </Link>
            , and we reply to every message there.
          </p>
        </div>
      )}
    </div>
  );
}
