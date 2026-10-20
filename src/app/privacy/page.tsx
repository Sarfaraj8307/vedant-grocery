import type { Metadata } from "next";

import { LegalList, LegalPage } from "@/components/legal/legal-page";
import { store } from "@/config/store";

export const metadata: Metadata = {
  title: "Privacy policy",
  description: `How ${store.name} collects, uses and protects your personal information.`,
  alternates: { canonical: "/privacy" },
};

export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy policy"
      updated="7 October 2026"
      intro={`${store.name} needs a small amount of information to deliver your order. This policy explains exactly what we hold, why, and what you can do about it. It is written to match what the application actually stores.`}
      sections={[
        {
          id: "what-we-collect",
          heading: "What we collect",
          body: (
            <>
              <p>We collect only what an order requires:</p>
              <LegalList
                items={[
                  "Account details — your email address, and optionally your first name, last name and phone number.",
                  "A username, used by the shop to identify your account.",
                  "Delivery addresses — the name, phone number, street address, city, state and pincode you enter.",
                  "Your basket — the products, chosen pack sizes and quantities you add.",
                  "Orders — the items, quantities, prices, delivery address, chosen delivery slot, order status and any adjustments.",
                  "Messages you send us through the support page.",
                ]}
              />
              <p>
                We do <strong className="font-medium text-ink">not</strong> collect
                payment card details, because this application does not take
                payment. There is no advertising or analytics tracker on the site.
              </p>
            </>
          ),
        },
        {
          id: "where-data-lives",
          heading: "Where your data lives",
          body: (
            <>
              <p>
                Your account, addresses and orders are stored in our database
                (PostgreSQL, hosted by Supabase in the Asia-Pacific region).
                Passwords are handled by our authentication provider and are never
                visible to us or stored by this application.
              </p>
              <p>
                Our database enforces row-level security: every table containing
                personal data is restricted by policy to the authenticated customer
                who owns the row. Another customer cannot read your addresses,
                basket, orders or messages even if they guess an identifier — the
                database refuses the query regardless of what the application asks
                for.
              </p>
            </>
          ),
        },
        {
          id: "how-we-use-it",
          heading: "How we use it",
          body: (
            <LegalList
              items={[
                "To pick, pack and deliver your order, and to contact you about it.",
                "To show you your own order history and let you reorder.",
                "To answer messages you send us.",
                "To keep accounting records of orders placed.",
              ]}
            />
          ),
        },
        {
          id: "who-can-see-it",
          heading: "Who can see it",
          body: (
            <>
              <p>
                Only you and the shop&apos;s own staff can see your personal data.
                The shop sees your order, the delivery address and your contact
                number so it can deliver. We do not sell or rent your information,
                and we do not share it with advertisers.
              </p>
              <p>
                Our infrastructure providers (database and hosting) process data on
                our behalf under their own terms.
              </p>
            </>
          ),
        },
        {
          id: "on-your-device",
          heading: "What is stored on your device",
          body: (
            <>
              <p>
                If you browse without signing in, your basket and your recent
                searches are kept in your browser&apos;s local storage so they
                survive a refresh. Nothing from a guest basket is sent to our
                servers. When you sign in, your basket is merged into your account
                so it follows you between devices.
              </p>
              <p>
                If you install the app, a service worker caches pages and images on
                your device so the app opens faster and shows something useful when
                you are offline. Clearing your browser data removes all of this.
              </p>
            </>
          ),
        },
        {
          id: "your-rights",
          heading: "Your choices and rights",
          body: (
            <>
              <LegalList
                items={[
                  "Erase your saved addresses and basket yourself, any time, from the account page.",
                  "Change your name and phone number from the account page.",
                  "Ask us to delete your account entirely — message the shop and we will remove it within 7 days.",
                  "Ask what we hold about you, and ask us to correct it.",
                ]}
              />
              <p>
                Past order records are kept for accounting and to resolve disputes,
                even after an account is deleted. They are reduced to what is
                required for that purpose.
              </p>
            </>
          ),
        },
        {
          id: "security",
          heading: "How we protect it",
          body: (
            <LegalList
              items={[
                "All traffic is encrypted in transit over HTTPS.",
                "Database access is restricted per customer by row-level security policies.",
                "The customer app never holds an administrative key, so it cannot bypass those policies even if it is compromised.",
                "Administrative access is granted per person on the server side and is not available from this application.",
              ]}
            />
          ),
        },
        {
          id: "children",
          heading: "Children",
          body: (
            <p>
              This service is intended for adults placing grocery orders. We do not
              knowingly create accounts for children.
            </p>
          ),
        },
        {
          id: "changes",
          heading: "Changes to this policy",
          body: (
            <p>
              If this policy changes in a way that affects you, the updated version
              will replace this page with a new date at the top. Continuing to use
              the service after that means you accept the change.
            </p>
          ),
        },
        {
          id: "contact",
          heading: "Contacting us",
          body: (
            <p>
              For any privacy question or request, use the{" "}
              <a
                href="/contact"
                className="text-leaf-700 underline underline-offset-2"
              >
                contact page
              </a>
              . Please include the email address on your account so we can find it.
            </p>
          ),
        },
      ]}
    />
  );
}
