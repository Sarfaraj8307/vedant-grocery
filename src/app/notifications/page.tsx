import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight, Bell, BellRing } from "lucide-react";

import { MarkAllReadButton } from "@/components/account/notification-actions";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/misc";
import { requireUser } from "@/lib/auth";
import { countUnread, listNotifications } from "@/lib/notifications";
import { timeAgo } from "@/lib/format";
import { cn } from "@/lib/utils";

export const metadata: Metadata = {
  title: "Notifications",
  description: "Order updates and announcements from Vedant Fruit & Vegetables.",
  robots: { index: false, follow: false },
};

export const dynamic = "force-dynamic";

/**
 * Notifications inbox.
 *
 * The page states plainly how notifications arrive: an in-app inbox plus, on
 * supported browsers, web push. It does not claim push delivery works where the
 * platform or the customer's settings do not allow it (brief §26: "do not
 * promise push delivery where browser/platform support does not exist").
 */
export default async function NotificationsPage() {
  await requireUser("/notifications");
  const notifications = await listNotifications();
  const unread = countUnread(notifications);

  return (
    <div className="shell flex flex-col gap-5 py-6 sm:py-8">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="font-display text-h1 text-ink">Notifications</h1>
          <p className="mt-1 text-small text-ink-muted">
            {notifications.length === 0
              ? "Order updates from the shop appear here."
              : `${notifications.length} ${
                  notifications.length === 1 ? "message" : "messages"
                }${unread > 0 ? ` · ${unread} unread` : " · all read"}`}
          </p>
        </div>
        <MarkAllReadButton hasUnread={unread > 0} />
      </header>

      {notifications.length === 0 ? (
        <EmptyState
          className="rounded-lg border border-line bg-surface shadow-xs"
          illustration={
            <span className="grid size-16 place-items-center rounded-full bg-surface-muted">
              <Bell className="size-7 text-leaf-500" aria-hidden="true" />
            </span>
          }
          title="Nothing to read yet"
          description="When you place an order and the shop confirms, packs or dispatches it, the updates land here. You will also see store announcements."
          action={
            <Button asChild>
              <Link href="/categories">
                Start shopping
                <ArrowRight className="size-4" aria-hidden="true" />
              </Link>
            </Button>
          }
        />
      ) : (
        <ul className="flex flex-col gap-2">
          {notifications.map((notification) => {
            const unreadItem = notification.read_at === null;
            return (
              <li
                key={notification.id}
                className={cn(
                  "rounded-lg border bg-surface p-4 shadow-xs",
                  unreadItem ? "border-leaf-600/35" : "border-line",
                )}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <h2 className="flex items-center gap-2 text-body font-medium text-ink">
                    {unreadItem ? (
                      <BellRing
                        className="size-4 text-leaf-600"
                        aria-hidden="true"
                      />
                    ) : (
                      <Bell className="size-4 text-ink-subtle" aria-hidden="true" />
                    )}
                    {notification.title}
                  </h2>
                  <div className="flex items-center gap-2">
                    {unreadItem ? (
                      <Badge variant="leaf" size="sm">
                        New
                      </Badge>
                    ) : null}
                    <time
                      dateTime={notification.created_at}
                      className="text-small text-ink-subtle"
                    >
                      {timeAgo(notification.created_at)}
                    </time>
                  </div>
                </div>
                <p className="mt-1.5 max-w-[68ch] text-small text-ink-muted">
                  {notification.body}
                </p>
              </li>
            );
          })}
        </ul>
      )}

      <p className="rounded-md border border-line bg-surface-muted px-4 py-3 text-small text-ink-muted">
        <strong className="font-medium text-ink">How you are notified.</strong>{" "}
        Updates always appear on this page and on the order&apos;s tracking
        screen. On supported browsers you can also allow notifications to receive
        them outside the app. We never send marketing messages you did not ask
        for.
      </p>
    </div>
  );
}
