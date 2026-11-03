"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Paperclip, Send, X } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/menu";
import { complaintSchema, type ComplaintInput } from "@/lib/validation";
import { createComplaint, replyToComplaint, uploadComplaintAttachment, validateComplaintAttachment } from "@/services/support-service";
import { formatDateTime } from "@/lib/format";
import type { ComplaintMessageRow, OrderRow } from "@/types/database";

/** Max images a customer can attach to one report. */
const MAX_ATTACHMENTS = 5;

type PendingAttachment = { file: File; url: string };

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

  const [attachments, setAttachments] = React.useState<PendingAttachment[]>([]);
  const [attachmentError, setAttachmentError] = React.useState<string | null>(null);

  // Always-current mirror of `attachments` so the unmount cleanup can revoke
  // the latest object URLs without depending on a stale render closure.
  const attachmentsRef = React.useRef<PendingAttachment[]>([]);
  React.useEffect(() => {
    attachmentsRef.current = attachments;
  });
  React.useEffect(() => {
    return () => {
      for (const attachment of attachmentsRef.current) {
        URL.revokeObjectURL(attachment.url);
      }
    };
  }, []);

  function handleFilesSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const selected = Array.from(event.target.files ?? []);
    // Reset the input so re-selecting the same file still fires `change`.
    event.target.value = "";

    if (attachments.length >= MAX_ATTACHMENTS) {
      setAttachmentError(`You can attach up to ${MAX_ATTACHMENTS} photos.`);
      return;
    }

    const next = [...attachments];
    let error: string | null = null;
    for (const file of selected) {
      if (next.length >= MAX_ATTACHMENTS) {
        error = `You can attach up to ${MAX_ATTACHMENTS} photos.`;
        break;
      }
      const invalid = validateComplaintAttachment(file);
      if (invalid) {
        error = invalid;
        continue;
      }
      next.push({ file, url: URL.createObjectURL(file) });
    }
    setAttachments(next);
    setAttachmentError(error);
  }

  function removeAttachment(index: number) {
    setAttachments((prev) => {
      const removed = prev[index];
      if (removed) URL.revokeObjectURL(removed.url);
      return prev.filter((_, i) => i !== index);
    });
  }

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

    // The complaint is sent; now try to upload each attached photo (if any).
    // A failed upload never fails the whole submission — the message is already
    // recorded, so we only surface a calm note that some photos did not make it.
    const complaintId = result.data;
    let failedPhotos = 0;
    for (const attachment of attachments) {
      const uploadResult = await uploadComplaintAttachment(complaintId, attachment.file);
      if (!uploadResult.ok) failedPhotos += 1;
    }

    // Release the object URLs now that the previews are no longer needed.
    for (const attachment of attachments) {
      URL.revokeObjectURL(attachment.url);
    }
    setAttachments([]);
    setAttachmentError(null);

    setSent(true);
    toast.success("Message sent — we will reply here");
    if (failedPhotos > 0) {
      toast.info("Message sent — some photos could not be uploaded");
    }
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

      <div className="mt-4">
        <span id="attachment-label" className="text-small font-medium text-ink">
          Photos (optional)
        </span>
        <label
          htmlFor="attachment-input"
          className="mt-1.5 inline-flex cursor-pointer items-center gap-2 rounded-md border border-line bg-surface-muted px-3 py-2 text-small font-medium text-ink transition-colors hover:bg-surface focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600"
        >
          <Paperclip className="size-4" aria-hidden="true" />
          Attach photos
          <input
            id="attachment-input"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="sr-only"
            onChange={handleFilesSelected}
            disabled={isSubmitting}
          />
        </label>
        <p className="mt-1 text-caption text-ink-subtle">
          Up to {MAX_ATTACHMENTS} images — JPG, PNG or WebP, 5 MB each.
        </p>
        {attachmentError ? (
          <p role="alert" className="mt-1.5 text-small text-danger">
            {attachmentError}
          </p>
        ) : null}
        {attachments.length > 0 ? (
          <ul
            aria-label="Selected photos"
            className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-4"
          >
            {attachments.map((attachment, index) => (
              <li
                key={attachment.url}
                className="group relative aspect-square overflow-hidden rounded-md border border-line"
              >
                <img
                  src={attachment.url}
                  alt={`Selected photo ${index + 1}`}
                  className="h-full w-full object-cover"
                />
                <button
                  type="button"
                  onClick={() => removeAttachment(index)}
                  disabled={isSubmitting}
                  aria-label={`Remove photo ${index + 1}`}
                  className="absolute right-1 top-1 rounded-full bg-ink/70 p-1 text-white transition-colors hover:bg-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600"
                >
                  <X className="size-3.5" aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </div>

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
