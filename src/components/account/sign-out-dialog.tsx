"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  SheetContent,
  SheetFooter,
  SheetHeader,
  SheetRoot,
} from "@/components/ui/sheet";
import { signOut } from "@/services/auth-service";

/**
 * Sign-out confirmation.
 *
 * Signing out used to happen on a single tap, which is easy to hit by accident
 * in a dropdown and costs the customer their session. A confirmation is the
 * standard guard for an action that is annoying to undo (they need their
 * password again).
 *
 * The dialog is presentational plus one service call; the trigger lives with
 * whichever surface opened it.
 */
export function SignOutDialog({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const router = useRouter();
  const [pending, setPending] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  async function confirm() {
    setPending(true);
    setError(null);

    const result = await signOut();
    if (!result.ok) {
      setError(result.error);
      setPending(false);
      return;
    }

    onOpenChange(false);
    // `refresh` matters as well as `push`: the header, cart and any server
    // component in the tree were rendered for a signed-in user.
    router.push("/");
    router.refresh();
  }

  return (
    <SheetRoot open={open} onOpenChange={onOpenChange}>
      <SheetContent side="center" title="Sign out of this account?">
        <SheetHeader
          title="Sign out?"
          description="You will need to sign in again to see your orders and basket."
        />

        <div className="px-5">
          {error ? (
            <p role="alert" className="mb-2 text-small text-danger">
              {error}
            </p>
          ) : (
            <p className="text-small text-ink-muted">
              Your saved addresses and order history stay on your account.
            </p>
          )}
        </div>

        <SheetFooter className="mt-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={pending}
          >
            Stay signed in
          </Button>
          <Button
            type="button"
            variant="danger"
            loading={pending}
            loadingLabel="Signing out…"
            onClick={() => void confirm()}
          >
            <LogOut className="size-4" aria-hidden="true" />
            Sign out
          </Button>
        </SheetFooter>
      </SheetContent>
    </SheetRoot>
  );
}

/** Standalone trigger, for surfaces that are not a menu item. */
export function SignOutButton({
  className,
  variant = "outline",
  size = "md",
  label = "Sign out",
}: {
  className?: string;
  // Derived from Button rather than re-declared, so adding a variant there
  // cannot silently leave this component unable to use it.
  variant?: React.ComponentProps<typeof Button>["variant"];
  size?: React.ComponentProps<typeof Button>["size"];
  label?: string;
}) {
  const [open, setOpen] = React.useState(false);

  return (
    <>
      <Button
        type="button"
        variant={variant}
        size={size}
        className={className}
        onClick={() => setOpen(true)}
      >
        <LogOut className="size-4" aria-hidden="true" />
        {label}
      </Button>
      <SignOutDialog open={open} onOpenChange={setOpen} />
    </>
  );
}
