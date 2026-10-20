import type { Metadata } from "next";
import Link from "next/link";
import { Scale, Sparkles, Truck, Users } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SectionHeading } from "@/components/ui/misc";
import { SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import { formatMoney } from "@/lib/money";
import { store } from "@/config/store";

export const metadata: Metadata = {
  title: "About us",
  description: `${store.name} is a local fruit and vegetable shop. Learn how we buy, weigh and deliver fresh produce.`,
  alternates: { canonical: "/about" },
};

/**
 * About page.
 *
 * Everything stated here is something the system actually does — the exact-weight
 * variants come from `product_weights`, the delivery rules from
 * `private.shop_settings`, and the weighing step from the fact that orders are
 * built from a chosen weight rather than a generic "1 unit". No invented history,
 * awards or customer counts.
 */
export default function AboutPage() {
  return (
    <div className="shell flex flex-col gap-8 py-6 sm:py-8">
      <SectionHeading
        as="h1"
        title="A local shop, run the way a local shop should be"
        subtitle={`${store.name} buys fresh produce each morning and delivers it the same day. No warehouses, no week-old stock.`}
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Pillar
          icon={<Scale className="size-4" aria-hidden="true" />}
          title="You choose the weight"
          body="Pick 250 g, 500 g, 1 kg or a 2 kg pack. We weigh exactly that and bill you for it — not a rounded-up version of it."
        />
        <Pillar
          icon={<Sparkles className="size-4" aria-hidden="true" />}
          title="Bought that morning"
          body="We buy from the wholesale market daily. If something isn't good enough that day, it does not go on the shelf."
        />
        <Pillar
          icon={<Truck className="size-4" aria-hidden="true" />}
          title="Same-day local delivery"
          body={`Choose a delivery slot at checkout. Free over ${formatMoney(
            SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold,
          )}, otherwise ${formatMoney(SHOP_SETTINGS_PREVIEW.deliveryCharge)}.`}
        />
        <Pillar
          icon={<Users className="size-4" aria-hidden="true" />}
          title="You talk to us"
          body="Questions, substitutions and complaints come straight to the shop — not to a call centre."
        />
      </div>

      <section className="rounded-lg border border-line bg-surface p-5 shadow-xs sm:p-7">
        <h2 className="font-display text-h2 text-ink">How an order works</h2>
        <ol className="mt-4 flex flex-col gap-4">
          <Step
            n={1}
            title="You build your basket"
            body="Add produce and choose the exact pack size for each item. The price you see is the price for that pack."
          />
          <Step
            n={2}
            title="You pick a slot"
            body="Choose a delivery window at checkout. Slots are limited so we can keep every order fresh and on time."
          />
          <Step
            n={3}
            title="We confirm and pack"
            body="The shop accepts your order, then the produce is cut, weighed and sealed. If an item is not good enough or runs out, we tell you — and your bill is adjusted down."
          />
          <Step
            n={4}
            title="We deliver"
            body="Your order arrives in your chosen slot. Pay as arranged with the shop; this app does not collect payment."
          />
        </ol>
      </section>

      <section className="rounded-lg border border-line bg-surface-muted p-5 sm:p-7">
        <h2 className="font-display text-h2 text-ink">
          What we do not do
        </h2>
        <ul className="mt-3 flex max-w-[70ch] flex-col gap-2 text-body text-ink-muted">
          <li>
            • We do not charge for anything you did not receive. Rejected items are
            removed from your bill and shown with the reason.
          </li>
          <li>
            • We do not sell your data. Your address and phone number are used to
            deliver your order and for nothing else.
          </li>
          <li>
            • We do not send marketing messages unless you have asked for them.
          </li>
        </ul>
      </section>

      <div className="flex flex-wrap gap-2">
        <Button asChild>
          <Link href="/categories">Browse today&apos;s produce</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/contact">Contact the shop</Link>
        </Button>
      </div>
    </div>
  );
}

function Pillar({
  icon,
  title,
  body,
}: {
  icon: React.ReactNode;
  title: string;
  body: string;
}) {
  return (
    <section className="rounded-lg border border-line bg-surface p-4 shadow-xs">
      <span className="grid size-8 place-items-center rounded-full bg-leaf-100 text-leaf-700">
        {icon}
      </span>
      <h2 className="mt-3 text-h3 text-ink">{title}</h2>
      <p className="mt-1 text-small text-ink-muted">{body}</p>
    </section>
  );
}

function Step({ n, title, body }: { n: number; title: string; body: string }) {
  return (
    <li className="flex gap-3.5">
      <span
        aria-hidden="true"
        className="grid size-7 shrink-0 place-items-center rounded-full bg-leaf-600 text-small font-semibold text-white"
      >
        {n}
      </span>
      <div>
        <h3 className="text-h3 text-ink">{title}</h3>
        <p className="mt-0.5 max-w-[64ch] text-small text-ink-muted">{body}</p>
      </div>
    </li>
  );
}
