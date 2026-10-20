import type { Metadata } from "next";

import { LegalList, LegalPage } from "@/components/legal/legal-page";
import { SHOP_SETTINGS_PREVIEW } from "@/lib/pricing";
import { formatMoney } from "@/lib/money";
import { store } from "@/config/store";

export const metadata: Metadata = {
  title: "Terms & conditions",
  description: `The terms on which ${store.name} accepts and delivers your grocery order.`,
  alternates: { canonical: "/terms" },
};

export default function TermsPage() {
  const freeOver = formatMoney(SHOP_SETTINGS_PREVIEW.freeDeliveryThreshold);
  const charge = formatMoney(SHOP_SETTINGS_PREVIEW.deliveryCharge);

  return (
    <LegalPage
      title="Terms & conditions"
      updated="7 October 2026"
      intro={`These terms cover ordering from ${store.name} through this website. They describe how our ordering, pricing and delivery actually work.`}
      sections={[
        {
          id: "ordering",
          heading: "Placing an order",
          body: (
            <>
              <p>
                You build a basket, choose the pack size for each item, pick a
                delivery address and a delivery slot, and submit the order. The order
                is a request: it becomes binding when the shop accepts it.
              </p>
              <p>
                You will need an account to place an order, so we can reach you about
                it and so you can track it.
              </p>
            </>
          ),
        },
        {
          id: "weights-and-pricing",
          heading: "Weights, prices and availability",
          body: (
            <>
              <p>
                Fresh produce is sold by weight or by unit, as shown on each item.
                The price you see is our current price for the pack size you
                selected, after any offer that is live at that moment.
              </p>
              <p>
                Fruit and vegetables are traded daily, so prices and availability can
                change. We confirm the price of every line when we accept your order.
                If an item has become unavailable, or we cannot supply the weight you
                asked for, we will not charge you for it: the item is removed from
                the order, the reason is shown on your order page, and your total is
                reduced. Your payable amount only ever goes down as a result of an
                adjustment — it never increases after you place the order.
              </p>
              <p>
                The total you see at checkout is our best current estimate. It is
                confirmed when the order is placed and adjusted only in the
                circumstances above.
              </p>
            </>
          ),
        },
        {
          id: "delivery",
          heading: "Delivery",
          body: (
            <>
              <p>
                Delivery is available in our local area to the address you select.
                Free on orders over {freeOver}; otherwise a {charge} delivery charge
                applies. Delivery slots are limited and are confirmed when your order
                is placed.
              </p>
              <p>
                Please give an accurate address and a phone number the delivery
                partner can reach. If we cannot deliver because an address or number
                is wrong or nobody is available, we will contact you to rearrange.
              </p>
            </>
          ),
        },
        {
          id: "payment",
          heading: "Payment",
          body: (
            <p>
              This website does not collect payment and does not store card details.
              You pay the shop by the method agreed with them — typically on
              delivery. The amount payable is the final total shown on your order
              after any adjustments.
            </p>
          ),
        },
        {
          id: "cancellation",
          heading: "Cancelling and changing an order",
          body: (
            <>
              <p>
                If you need to change or cancel an order, contact the shop as soon as
                possible — before it is packed. Once produce has been cut and packed
                to your order, it cannot be returned to the shelf, so we may not be
                able to cancel it.
              </p>
              <p>
                We may cancel an order if we cannot fulfil it (for example, a slot
                becomes unavailable or the shop is closed). If we cancel, nothing is
                payable by you.
              </p>
            </>
          ),
        },
        {
          id: "freshness",
          heading: "Quality and complaints",
          body: (
            <>
              <p>
                We buy fresh and check produce before it is packed. If something is
                not right — missing, damaged, or below the standard you expect —
                tell us through the support page, preferably with a photo. We will
                replace it or take it off your bill.
              </p>
              <LegalList
                items={[
                  "Report a problem the same day or the next day, while we still have the batch details.",
                  "Keep the item until we have responded, if you can.",
                  "We will always tell you the outcome; if we disagree, we will explain why.",
                ]}
              />
            </>
          ),
        },
        {
          id: "your-account",
          heading: "Your account",
          body: (
            <LegalList
              items={[
                "Keep your password private and tell us if you think it has been used by someone else.",
                "Give accurate details, especially delivery addresses and phone numbers.",
                "Do not use the service for anything unlawful or to place fraudulent orders.",
                "We may suspend an account that abuses the service or repeatedly refuses deliveries.",
              ]}
            />
          ),
        },
        {
          id: "liability",
          heading: "Our responsibility",
          body: (
            <p>
              We are responsible for supplying the produce you ordered in good
              condition and delivering it within your slot, as far as we reasonably
              can. Our liability for any order is limited to the value of that order.
              We are not responsible for delays outside our control, such as severe
              weather, road closures or a failure of our hosting provider.
            </p>
          ),
        },
        {
          id: "changes",
          heading: "Changes to these terms",
          body: (
            <p>
              These terms may be updated; the version published here at the time you
              order is the version that applies to that order. The date at the top of
              this page shows when it last changed.
            </p>
          ),
        },
        {
          id: "contact",
          heading: "Governing law and contact",
          body: (
            <p>
              These terms are governed by the laws of India. Questions about them, or
              about an order, go to{" "}
              <a
                href="/contact"
                className="text-leaf-700 underline underline-offset-2"
              >
                our contact page
              </a>
              . {store.name} is the seller of all goods on this website.
            </p>
          ),
        },
      ]}
    />
  );
}
