"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { Search, X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Search field.
 *
 * A real `<form>` with `method="get"` semantics via `onSubmit`: pressing Enter
 * navigates to `/search?q=…`, which means the result page is a normal,
 * shareable, back-button-friendly URL, and search works with JavaScript
 * partially unavailable (the form falls back to a native GET on `/search`).
 *
 * `role="searchbox"` semantics come from `type="search"`; no ARIA needed.
 */
export function SearchField({
  defaultValue = "",
  placeholder = "Search fruits & vegetables",
  className,
  autoFocus = false,
  onSubmitted,
  showClear = true,
  size = "md",
}: {
  defaultValue?: string;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
  onSubmitted?: () => void;
  showClear?: boolean;
  size?: "md" | "lg";
}) {
  const router = useRouter();
  /**
   * Uncontrolled-after-mount by design. The input is seeded from `defaultValue`
   * once, and callers that need it to follow a changing URL pass a React `key`
   * (see `/search`) so the field remounts closed — the declarative alternative to
   * syncing state from a prop inside an effect.
   */
  const [value, setValue] = React.useState(defaultValue);
  const inputRef = React.useRef<HTMLInputElement>(null);

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const term = value.trim();
    router.push(term ? `/search?q=${encodeURIComponent(term)}` : "/search");
    onSubmitted?.();
  }

  return (
    <form
      role="search"
      action="/search"
      method="get"
      onSubmit={submit}
      className={cn("relative flex items-center", className)}
    >
      <Search
        className={cn(
          "pointer-events-none absolute text-ink-subtle",
          size === "lg" ? "left-5 size-5" : "left-4 size-[18px]",
        )}
        aria-hidden="true"
      />
      <input
        ref={inputRef}
        type="search"
        name="q"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        autoComplete="off"
        aria-label="Search products"
        enterKeyHint="search"
        className={cn(
          "w-full rounded-full border-[1.5px] border-line-strong bg-surface text-ink",
          "shadow-[0_1px_2px_rgba(26,46,26,0.08)]",
          "placeholder:text-ink-subtle hover:border-line-strong",
          "transition-[border-color,box-shadow] duration-[var(--motion-fast)]",
          "focus:border-leaf-600 focus:outline-none focus-visible:outline-none focus:ring-4 focus:ring-leaf-600/15",
          // Hide the WebKit/Blink native clear button; we draw our own so it is
          // the same size as every other control.
          "[&::-webkit-search-cancel-button]:appearance-none",
          size === "lg"
            ? "h-14 rounded-[1.75rem] pl-12 pr-12 text-body-lg"
            : "h-12 rounded-full pl-11 pr-11 text-body",
          className,
        )}
      />
      {showClear && value ? (
        <button
          type="button"
          onClick={() => {
            setValue("");
            inputRef.current?.focus();
          }}
          className="absolute right-2 grid size-8 place-items-center rounded-full text-ink-subtle transition-colors hover:bg-surface-muted hover:text-ink"
        >
          <X className="size-4" aria-hidden="true" />
          <span className="sr-only">Clear search</span>
        </button>
      ) : null}
    </form>
  );
}
