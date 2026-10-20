"use client";

import * as React from "react";
import Link from "next/link";
import { Clock, X } from "lucide-react";
import { RECENT_SEARCH_KEY } from "@/config/catalogue";

/**
 * Recent searches.
 *
 * Stored in `localStorage` only — never sent to the server, never written to a
 * customer record. Search history is incidental data, so keeping it on-device is
 * both the privacy-correct and the simplest choice.
 *
 * `localStorage` is an *external store*, so it is read through
 * `useSyncExternalStore` rather than mirrored into state from an effect. That
 * gives a correct server snapshot (`SERVER_SNAPSHOT`, always empty) with no
 * hydration mismatch, no cascading render on mount, and automatic updates when
 * another tab changes the value.
 */

const MAX_RECENT = 6;
const SERVER_SNAPSHOT: readonly string[] = [];

/** Cached parse, so `getSnapshot` returns a referentially stable value. */
let cachedRaw: string | null = null;
let cachedValue: readonly string[] = SERVER_SNAPSHOT;

const listeners = new Set<() => void>();

function parse(raw: string | null): readonly string[] {
  if (!raw) return SERVER_SNAPSHOT;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return SERVER_SNAPSHOT;
    const terms = parsed
      .filter((item): item is string => typeof item === "string" && item.length > 0)
      .slice(0, MAX_RECENT);
    return terms.length > 0 ? terms : SERVER_SNAPSHOT;
  } catch {
    return SERVER_SNAPSHOT;
  }
}

function getSnapshot(): readonly string[] {
  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(RECENT_SEARCH_KEY);
  } catch {
    // Storage can be blocked (private mode, enterprise policy). Treat as empty.
    return SERVER_SNAPSHOT;
  }
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cachedValue = parse(raw);
  }
  return cachedValue;
}

function getServerSnapshot(): readonly string[] {
  return SERVER_SNAPSHOT;
}

function emit() {
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  // `storage` fires for changes made by other tabs.
  window.addEventListener("storage", listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

function write(terms: readonly string[]) {
  try {
    if (terms.length === 0) {
      window.localStorage.removeItem(RECENT_SEARCH_KEY);
    } else {
      window.localStorage.setItem(RECENT_SEARCH_KEY, JSON.stringify(terms));
    }
  } catch {
    // Non-fatal: the feature degrades to "no recent searches".
  }
  emit();
}

export function RecentSearches({
  currentQuery,
  className,
}: {
  /** The active query; recorded so it appears in the list next time. */
  currentQuery?: string;
  className?: string;
}) {
  const terms = React.useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  // Writing to an external store from an effect is exactly what effects are for.
  // No state is set here, so there is no cascading render.
  React.useEffect(() => {
    if (!currentQuery) return;
    const existing = getSnapshot();
    if (existing[0] === currentQuery) return;
    write([currentQuery, ...existing.filter((term) => term !== currentQuery)]);
  }, [currentQuery]);

  if (terms.length === 0) return null;

  return (
    <div className={className}>
      <div className="flex items-center justify-between gap-3">
        <p className="text-caption text-ink-subtle">Recent searches</p>
        <button
          type="button"
          onClick={() => write([])}
          className="inline-flex items-center gap-1 rounded-xs text-small text-ink-subtle transition-colors hover:text-ink"
        >
          <X className="size-3.5" aria-hidden="true" />
          Clear
        </button>
      </div>
      <ul className="mt-2 flex flex-wrap gap-2">
        {terms.map((term) => (
          <li key={term}>
            <Link
              href={`/search?q=${encodeURIComponent(term)}`}
              className="inline-flex h-8 items-center gap-1.5 rounded-pill border border-line-strong bg-surface px-3 text-small text-ink-muted transition-colors hover:border-ink-subtle hover:text-ink"
            >
              <Clock className="size-3.5 text-ink-subtle" aria-hidden="true" />
              {term}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
