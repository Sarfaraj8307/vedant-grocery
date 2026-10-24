"use client";

import * as React from "react";
import { Eye, EyeOff } from "lucide-react";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

/**
 * Password field with a show/hide toggle.
 *
 * The toggle is a real `<button type="button">` (never a submit) with
 * `aria-pressed` and a changing label, so a screen-reader user is told whether
 * the password is currently visible — an icon swap alone communicates nothing
 * to them.
 *
 * The field stays `type="password"` until the customer asks otherwise; it never
 * starts visible.
 */
export function PasswordInput({
  className,
  invalid,
  ...props
}: React.ComponentProps<"input"> & { invalid?: boolean }) {
  const [visible, setVisible] = React.useState(false);

  return (
    <div className="relative">
      <Input
        {...props}
        invalid={invalid}
        type={visible ? "text" : "password"}
        // Leave room for the toggle so a long password never runs under it.
        className={cn("pr-14", className)}
      />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? "Hide password" : "Show password"}
        aria-pressed={visible}
        className={cn(
          "absolute right-0 top-1/2 grid h-9 w-11 -translate-y-1/2 place-items-center rounded-r-xl",
          "border-l border-line text-ink-subtle transition-colors",
          "hover:text-ink focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-leaf-600/25",
        )}
      >
        {visible ? (
          <EyeOff className="size-4" aria-hidden="true" />
        ) : (
          <Eye className="size-4" aria-hidden="true" />
        )}
      </button>
    </div>
  );
}
