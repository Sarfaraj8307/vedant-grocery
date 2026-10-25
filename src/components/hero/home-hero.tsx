import Image from "next/image";
import Link from "next/link";
import { ArrowRight, Clock, Scale, Truck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { CallStoreButton, WhatsAppButton } from "@/components/store/contact-buttons";
import { SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import { formatMoney } from "@/lib/money";
import { store } from "@/config/store";

/**
 * Home hero — static, appetising photography.
 * No 3D: fast paint, real produce, mobile-first.
 */
export function HomeHero() {
  const freeOver = formatMoney(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold);
  const charge = formatMoney(SHOP_SETTINGS_PREVIEW.deliveryCharge);

  return (
    <section className="relative overflow-hidden border-b border-line">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(60%_70%_at_85%_10%,var(--color-harvest-100)_0%,transparent_70%),radial-gradient(50%_60%_at_5%_90%,var(--color-leaf-100)_0%,transparent_70%),linear-gradient(180deg,var(--color-paper)_0%,var(--color-earth-50)_100%)]"
      />

      <div className="shell relative grid items-center gap-8 py-8 sm:py-12 lg:grid-cols-[1.05fr_0.95fr] lg:gap-12 lg:py-20">
        {/* Visual first on mobile — appetising before readable. */}
        <div className="order-1 w-full animate-reveal motion-reduce:animate-none lg:order-2 lg:justify-self-end">
          <div className="relative mx-auto w-full max-w-[26rem] lg:max-w-[32rem]">
            <div className="overflow-hidden rounded-2xl border border-line bg-surface shadow-lg">
              <Image
                src="https://images.unsplash.com/photo-1610832958506-aa56368176cf?auto=format&fit=crop&w=900&q=80"
                alt="Fresh fruits and vegetables"
                width={640}
                height={560}
                priority
                sizes="(max-width:1024px) 100vw, 640px"
                className="aspect-[1.15] w-full object-cover"
              />
            </div>
            <div className="absolute -bottom-4 -left-3 flex items-center gap-3 rounded-xl border border-line bg-surface px-4 py-3 shadow-md animate-drift motion-reduce:animate-none sm:-left-6">
              <span className="grid size-10 place-items-center rounded-full bg-leaf-100 text-leaf-700">
                <Scale className="size-5" aria-hidden="true" />
              </span>
              <span className="text-small">
                <span className="block font-semibold text-ink">Lemon (Nimbu)</span>
                <span className="text-ink-muted">250 g · 500 g · 1 kg</span>
              </span>
            </div>
          </div>
        </div>

        <div className="order-2 flex flex-col items-start lg:order-1">
          <p className="inline-flex animate-reveal items-center gap-2 rounded-pill border border-marigold-200 bg-surface px-2.5 py-1 text-caption text-marigold-700 motion-reduce:animate-none">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-leaf-500" />
            {store.name}
          </p>

          <h1 className="mt-4 max-w-[24ch] animate-reveal text-display text-ink text-balance-tight [animation-delay:80ms] motion-reduce:animate-none">
            Fresh <span className="text-leaf-700">fruits</span>
            <br />
            &amp; <span className="text-beet-600">veggies</span>,
            <br />
            <span className="text-marigold-700">picked fresh today.</span>
          </h1>

          <p className="mt-4 max-w-[46ch] animate-reveal text-body-lg text-ink-muted [animation-delay:160ms] motion-reduce:animate-none">
            Picked at the market this morning, weighed to your choice of
            250&nbsp;g, 500&nbsp;g or 1&nbsp;kg, and at your door the same day.
            The first bite is the proof.
          </p>

          <div className="mt-6 flex w-full flex-col gap-2.5 sm:w-auto sm:flex-row sm:items-center">
            <Button asChild size="lg" block className="sm:w-auto">
              <Link href="/categories">
                Start shopping
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </Button>
            <Button asChild size="lg" variant="secondary" block className="sm:w-auto">
              <Link href="/offers">Today&apos;s offers</Link>
            </Button>
          </div>

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <CallStoreButton variant="quiet" />
            <WhatsAppButton variant="quiet" />
          </div>

          <dl className="mt-7 grid w-full grid-cols-1 gap-x-6 gap-y-3 border-t border-line pt-5 sm:grid-cols-3">
            <TrustItem icon={<Truck className="size-4" aria-hidden="true" />} term="Free delivery" detail={`On orders over ${freeOver}`} />
            <TrustItem icon={<Scale className="size-4" aria-hidden="true" />} term="Exact weights" detail="You pick the pack size" />
            <TrustItem icon={<Clock className="size-4" aria-hidden="true" />} term="Same-day slots" detail={`${charge} delivery below ${freeOver}`} />
          </dl>
        </div>
      </div>
    </section>
  );
}

function TrustItem({ icon, term, detail }: { icon: React.ReactNode; term: string; detail: string }) {
  return (
    <div className="flex items-start gap-2.5">
      <span className="mt-0.5 grid size-7 shrink-0 place-items-center rounded-full bg-leaf-100 text-leaf-700">{icon}</span>
      <div className="min-w-0">
        <dt className="text-small font-semibold text-ink">{term}</dt>
        <dd className="text-small text-ink-muted">{detail}</dd>
      </div>
    </div>
  );
}
