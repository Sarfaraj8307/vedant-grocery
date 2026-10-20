"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, LayoutGrid, Search, ReceiptText, User } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * Mobile bottom navigation — DESIGN_SYSTEM §11.
 *
 * Visible below `lg` only (the header carries navigation on desktop). Every
 * destination is an ordinary `<Link>`: prefetched, crawlable, and it works with
 * a keyboard, a screen reader and a middle-click.
 *
 * The bar is `position: fixed` with safe-area padding, and the page content
 * reserves room for it via the `pb-nav` utility so nothing is ever hidden behind
 * it. It does **not** animate on navigation — only the active pill changes.
 */

const ITEMS = [
  { href: "/", label: "Home", icon: Home },
  { href: "/categories", label: "Categories", icon: LayoutGrid },
  { href: "/search", label: "Search", icon: Search },
  { href: "/orders", label: "Orders", icon: ReceiptText },
  { href: "/account", label: "Account", icon: User },
] as const;

function isActive(pathname: string, href: string): boolean {
  if (href === "/") return pathname === "/";
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function BottomNav() {
  const pathname = usePathname();

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/95 backdrop-blur-sm lg:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <ul className="flex items-stretch">
        {ITEMS.map(({ href, label, icon: Icon }) => {
          const active = isActive(pathname, href);
          return (
            <li key={href} className="flex-1">
              <Link
                href={href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex h-14 flex-col items-center justify-center gap-1 rounded-md focus-visible:ring-2 focus-visible:ring-leaf-600/25 focus-visible:outline-none",
                  "text-[10px] font-medium tracking-[0.02em]",
                  "transition-colors duration-[var(--motion-instant)]",
                  active ? "text-leaf-700" : "text-ink-subtle hover:text-ink",
                )}
              >
                <span
                  className={cn(
                    "grid h-7 w-12 place-items-center rounded-pill transition-colors duration-[var(--motion-fast)]",
                    active && "bg-leaf-100",
                  )}
                >
                  <Icon
                    className="size-[19px]"
                    strokeWidth={active ? 2.3 : 1.9}
                    aria-hidden="true"
                  />
                </span>
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
