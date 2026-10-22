"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, X } from "lucide-react";

import { cancelOrder } from "@/app/orders/actions";
import { customerMayCancel } from "@/lib/order-cancel";
import { Button } from "@/components/ui/button";
import type { OrderStatus } from "@/types/database";

/**
 * Customer cancellation (brief: cancel only until the order is being packed).
 *
 * Two distinct states, and the distinction matters:
 *
 *  • **Cancellable** — a plain "Cancel order" button that reveals a short reason
 *    field. The reason is required by the database, so it is required here too
 *    rather than being collected and silently dropped.
 *
 *  • **Too late** — no button at all. Offering a disabled button would imply the
 *    customer is blocked by a glitch; the honest message is that the order is
 *    already being packed and they should contact the store. This is the case the
 *    owner specifically asked for.
 *
 * The window itself is enforced in the database (`customer_cancel_order`), not
 * here — this component only decides what to render.
 */
export function CancelOrderPanel({
  orderId,
  status,
}: {
  orderId: string;
  status: OrderStatus;
}) {
  const router = useRouter();
  const [open, setOpen] = React.useState(false);
  const [reason, setReason] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [pending, setPending] = React.useState(false);

  // Nothing to offer once the order is finished or already cancelled.
  if (status === "CANCELLED" || status === "DELIVERED") return null;

  if (!customerMayCancel(status)) {
    return (
      <p className="flex items-start gap-2 rounded-md border border-line bg-surface-muted px-3.5 py-3 text-small text-ink-muted">
        <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
        <span>
          This order is already being packed, so it can no longer be cancelled
          here.{" "}
          <a
            href="/contact"
            className="font-medium text-ink underline underline-offset-2"
          >
            Contact the store
          </a>{" "}
          and we&apos;ll sort it out for you.
        </span>
      </p>
    );
  }

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (pending) return;
    setPending(true);
    setError(null);

    const result = await cancelOrder(orderId, reason);
    if (result.ok) {
      // The server action revalidates; a refresh re-renders the cancelled state.
      setOpen(false);
      router.refresh();
    } else {
      setError(result.error);
    }
    setPending(false);
  }

  if (!open) {
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={() => setOpen(true)}
      >
        <X className="size-4" aria-hidden="true" />
        Cancel order
      </Button>
    );
  }

  return (
    <form
      onSubmit={onSubmit}
      className="rounded-md border border-line bg-surface p-3.5"
    >
      <p className="text-small font-medium text-ink">Cancel this order?</p>
      <p className="mt-0.5 text-small text-ink-muted">
        This cannot be undone. The items go back on sale and you will not be
        charged.
      </p>

      <label
        htmlFor="cancel-reason"
        className="mt-3 block text-small font-medium text-ink"
      >
        Why are you cancelling?
      </label>
      <input
        id="cancel-reason"
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        placeholder="Ordered by mistake"
        maxLength={200}
        autoComplete="off"
        className="mt-1 w-full rounded-md border border-line bg-surface px-3 py-2 text-small text-ink outline-none focus:border-ink-muted"
      />

      {error ? (
        <p role="alert" className="mt-2 text-small text-danger">
          {error}
        </p>
      ) : null}

      <div className="mt-3 flex flex-wrap gap-2">
        <Button type="submit" variant="danger" size="sm" loading={pending} loadingLabel="Cancelling…">
          Yes, cancel it
        </Button>
        <Button
          type="button"
          variant="quiet"
          size="sm"
          onClick={() => {
            setOpen(false);
            setError(null);
          }}
          disabled={pending}
        >
          Keep my order
        </Button>
      </div>
    </form>
  );
}
