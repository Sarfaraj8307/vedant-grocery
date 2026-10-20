"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Send } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/menu";
import { complaintSchema, type ComplaintInput } from "@/lib/validation";
import { createComplaint, replyToComplaint } from "@/services/support-service";
import { formatDateTime } from "@/lib/format";
import type { ComplaintMessageRow, OrderRow } from "@/types/database";

/** New support thread. */
export function ComplaintForm({
  orders,
  defaultOrderId,
}: {
  orders: Pick<OrderRow, "id" | "placed_at" | "final_total">[];
  defaultOrderId?: string;
}) {
  const router = useRouter();
  const [sent, setSent] = React.useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm<ComplaintInput>({
    resolver: zodResolver(complaintSchema),
    defaultValues: { subject: "", description: "", orderId: defaultOrderId ?? "" },
  });

  // Keeps the `orderId` field registered with the resolver while the Radix
  // Select drives its value.
  const orderIdField = register("orderId");

  // The Radix Select is not a native input, so its value is held in local state
  // and mirrored into the form on change. Using react-hook-form's `watch()` here
  // would make the React Compiler skip memoising this whole component.
  const [orderId, setOrderId] = React.useState(defaultOrderId ?? "");

  async function onSubmit(values: ComplaintInput) {
    const result = await createComplaint({
      subject: values.subject,
      description: values.description,
      orderId: values.orderId || null,
    });

    if (!result.ok) {
      setError("root", { message: result.error });
      return;
    }
    setSent(true);
    toast.success("Message sent — we will reply here");
    router.refresh();
  }

  if (sent) {
    return (
      <p
        role="status"
        className="rounded-md border border-success-bg bg-success-bg px-3.5 py-3 text-small text-success"
      >
        Thank you — your message is with the shop. We reply from this page, so keep
        an eye on it.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {errors.root ? (
        <p
          role="alert"
          className="mb-4 rounded-md border border-danger-bg bg-danger-bg px-3 py-2.5 text-small text-danger"
        >
          {errors.root.message}
        </p>
      ) : null}

      <Field
        label="What is this about?"
        htmlFor="subject"
        error={errors.subject?.message}
        required
      >
        <Input
          id="subject"
          placeholder="Missing item, quality, delivery…"
          invalid={Boolean(errors.subject)}
          {...register("subject")}
        />
      </Field>

      <Field
        label="Tell us what happened"
        htmlFor="description"
        error={errors.description?.message}
        hint="The more detail you give, the faster we can fix it."
        required
      >
        <Textarea
          id="description"
          rows={5}
          invalid={Boolean(errors.description)}
          {...register("description")}
        />
      </Field>

      {/* Registered so the resolver always sees the current order id. */}
      <input type="hidden" {...orderIdField} />

      {orders.length > 0 ? (
        <Field
          label="Related order"
          htmlFor="orderId"
          error={errors.orderId?.message}
          hint="Optional — helps us find the order instantly."
        >
          <Select
            value={orderId}
            onValueChange={(value) => {
              const next = value === "none" ? "" : value;
              setOrderId(next);
              // `setValue` on an unregistered field still updates the resolver's
              // value, and the hidden input keeps the field registered.
              orderIdField.onChange({ target: { value: next, name: "orderId" } });
            }}
          >
            <SelectTrigger id="orderId">
              <SelectValue placeholder="Not about a specific order" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="none">Not about a specific order</SelectItem>
              {orders.map((order) => (
                <SelectItem key={order.id} value={order.id}>
                  {formatDateTime(order.placed_at)} · ₹{order.final_total}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      ) : null}

      <Button type="submit" loading={isSubmitting} loadingLabel="Sending…">
        <Send className="size-4" aria-hidden="true" />
        Send message
      </Button>
    </form>
  );
}

/** Reply box inside a thread. */
export function ComplaintReplyBox({ complaintId }: { complaintId: string }) {
  const router = useRouter();
  const [body, setBody] = React.useState("");
  const [sending, setSending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  async function send(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = body.trim();
    if (trimmed.length < 1) {
      setError("Please type a message first.");
      return;
    }
    if (trimmed.length > 2000) {
      // Mirrors the `complaint_messages_body_check` constraint.
      setError("Please keep replies under 2000 characters.");
      return;
    }

    setSending(true);
    const result = await replyToComplaint(complaintId, trimmed);
    setSending(false);

    if (!result.ok) {
      setError(result.error);
      return;
    }
    setBody("");
    setError(null);
    router.refresh();
  }

  return (
    <form onSubmit={send} className="border-t border-line pt-4">
      <label htmlFor="reply" className="text-small font-medium text-ink">
        Add a reply
      </label>
      <Textarea
        id="reply"
        rows={3}
        value={body}
        onChange={(event) => setBody(event.target.value)}
        invalid={Boolean(error)}
        className="mt-1.5"
        placeholder="Type your message…"
      />
      <div className="mt-2 flex items-center justify-between gap-3">
        <span className="text-small text-ink-subtle">
          {body.length}/2000
        </span>
        <Button type="submit" size="sm" loading={sending} loadingLabel="Sending…">
          <Send className="size-3.5" aria-hidden="true" />
          Send reply
        </Button>
      </div>
      {error ? (
        <p role="alert" className="mt-1.5 text-small text-danger">
          {error}
        </p>
      ) : null}
    </form>
  );
}

export type { ComplaintMessageRow };
