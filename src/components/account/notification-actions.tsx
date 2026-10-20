"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { CheckCheck } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { getSupabaseBrowserClient } from "@/lib/supabase/client";

/**
 * Mark all notifications as read.
 *
 * The UPDATE policy on `notifications` is `user_id = auth.uid()`, so this can
 * only ever touch the caller's own rows — the explicit `.is("read_at", null)`
 * filter is there for efficiency, not for safety.
 */
export function MarkAllReadButton({ hasUnread }: { hasUnread: boolean }) {
  const router = useRouter();
  const [working, setWorking] = React.useState(false);

  if (!hasUnread) return null;

  return (
    <Button
      type="button"
      variant="quiet"
      size="sm"
      loading={working}
      loadingLabel="Marking…"
      onClick={async () => {
        setWorking(true);
        const supabase = getSupabaseBrowserClient();
        const { error } = await supabase
          .from("notification_outbox")
          .update({ read_at: new Date().toISOString() })
          .eq("audience", "customer")
          .is("read_at", null);

        setWorking(false);
        if (error) {
          toast.error("We could not mark those as read.");
          return;
        }
        router.refresh();
      }}
    >
      <CheckCheck className="size-4" aria-hidden="true" />
      Mark all read
    </Button>
  );
}
