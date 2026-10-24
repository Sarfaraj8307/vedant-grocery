"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ChevronDown,
  LogOut,
  MapPin,
  Package,
  Receipt,
  Search,
  ShoppingBag,
  Sparkles,
  UserRound,
  Wallet,
} from "lucide-react";
import { BrandLogo } from "@/components/brand/logo";
import { SearchField } from "@/components/search/search-field";
import { SignOutDialog } from "@/components/account/sign-out-dialog";
import { UserAvatar } from "@/components/account/user-avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/menu";
import {
  SheetBody,
  SheetClose,
  SheetContent,
  SheetRoot,
  SheetTrigger,
} from "@/components/ui/sheet";
import { useCart } from "@/hooks/use-cart";
import { cn } from "@/lib/utils";

/**
 * Site header — DESIGN_SYSTEM §11.
 *
 * Mobile (<lg): brand + a full-width search field + cart. Navigation lives in
 * the bottom nav. A compact search *field* rather than an icon is deliberate:
 * search is the highest-frequency action in a grocery app, and hiding it behind
 * a tap costs a conversion.
 *
 * Desktop (≥lg): brand · delivery-address chip · search · section nav · account
 * · cart.
 */

export type HeaderCategory = {
  id: string;
  name: string;
  glyph: string;
};

const DESKTOP_NAV = [
  { href: "/categories", label: "Categories" },
  { href: "/offers", label: "Offers" },
  { href: "/orders", label: "Orders" },
] as const;

export function SiteHeader({
  categories,
  user,
  displayName,
  avatarUrl,
  deliveryLabel,
}: {
  categories: HeaderCategory[];
  user: { id: string; email: string | null } | null;
  displayName: string;
  avatarUrl: string | null;
  deliveryLabel: string | null;
}) {
  const pathname = usePathname();

  /*
   * The sign-out confirmation is owned here, not by the menu or the sheet that
   * opens it. A dialog rendered *inside* the mobile sheet would be dismissed by
   * the sheet's own outside-click handling before it could be answered — the
   * sheet treats the portalled dialog as "outside". Hoisting it to the header
   * keeps one dialog, one owner, and works for both surfaces.
   */
  const [signOutOpen, setSignOutOpen] = React.useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-paper/85 backdrop-blur-md">
      <div className="shell flex h-14 items-center gap-2.5 sm:gap-4 lg:h-[72px]">
        <Link
          href="/"
          className="shrink-0 rounded-md focus-visible:ring-2 focus-visible:ring-leaf-600/25 focus-visible:outline-none"
          aria-label="Vedant Fruit & Vegetables — home"
        >
          <BrandLogo className="hidden sm:flex" />
          <BrandLogo className="sm:hidden" compact showTagline={false} />
        </Link>

        {/* Delivery address chip — desktop only; it is a low-frequency action. */}
        <Link
          href="/account/addresses"
          className="hidden shrink-0 items-center gap-2 rounded-md border border-line bg-surface px-3 py-2 text-small transition-colors hover:border-line-strong lg:flex"
        >
          <MapPin className="size-4 text-leaf-600" aria-hidden="true" />
          <span className="flex flex-col leading-tight">
            <span className="text-[10px] font-semibold uppercase tracking-[0.09em] text-ink-subtle">
              Deliver to
            </span>
            <span className="max-w-[13rem] truncate text-ink">
              {deliveryLabel ?? "Set your address"}
            </span>
          </span>
          <ChevronDown className="size-3.5 text-ink-subtle" aria-hidden="true" />
        </Link>

        <div className="min-w-0 flex-1 lg:max-w-xl">
          <SearchField />
        </div>

        <nav aria-label="Sections" className="hidden items-center gap-0.5 lg:flex">
          {DESKTOP_NAV.map((item) => {
            const active =
              pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "rounded-md px-3 py-2 text-small font-medium transition-colors",
                  active
                    ? "bg-leaf-100 text-leaf-700"
                    : "text-ink-muted hover:bg-surface-muted hover:text-ink",
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        {user ? (
          <AccountMenu
            displayName={displayName}
            email={user.email}
            avatarUrl={avatarUrl}
            onRequestSignOut={() => setSignOutOpen(true)}
          />
        ) : (
          <Link
            href="/login"
            className="hidden shrink-0 rounded-md px-3 py-2 text-small font-medium text-ink-muted transition-colors hover:bg-surface-muted hover:text-ink lg:block"
          >
            Sign in
          </Link>
        )}

        <CartButton />

        {/* Mobile: a signed-in customer sees their own face, not a "Sign in"
            link. It doubles as the shortcut to their account. */}
        {user ? (
          <Link
            href="/account"
            aria-label={`Signed in as ${displayName}. Open your account`}
            className="grid shrink-0 place-items-center rounded-full focus-visible:outline-none lg:hidden"
          >
            <UserAvatar name={displayName} url={avatarUrl} size={30} />
          </Link>
        ) : null}

        {/* Mobile-only category quick access.
            Keyed on the pathname so navigating remounts the sheet closed — the
            declarative alternative to closing it in an effect. */}
        <SheetRoot key={pathname}>
          <SheetTrigger
            className="grid size-9 shrink-0 place-items-center rounded-md text-ink-muted transition-colors hover:bg-surface-muted hover:text-ink lg:hidden"
            aria-label="Browse categories and help"
          >
            <ChevronDown className="size-4" aria-hidden="true" />
          </SheetTrigger>
          <SheetContent side="bottom" title="Browse Vedant">
            <MobileMenuPanel
              categories={categories}
              user={user}
              displayName={displayName}
              avatarUrl={avatarUrl}
              onRequestSignOut={() => setSignOutOpen(true)}
            />
          </SheetContent>
        </SheetRoot>
      </div>

      <SignOutDialog open={signOutOpen} onOpenChange={setSignOutOpen} />

      {/*
       * Mobile delivery location — a slim second row so the location stays
       * reachable on a phone without crowding the search field. It is part of
       * the sticky header (so it never scrolls away) and is hidden on desktop,
       * where the full chip above already carries it. The sticky header is
       * taller on mobile because of this row — see `--header-h` in globals.css.
       */}
      <div className="border-t border-line lg:hidden">
        <div className="shell flex h-9 items-center gap-1.5">
          <MapPin className="size-3.5 shrink-0 text-leaf-600" aria-hidden="true" />
          <Link
            href="/account/addresses"
            className="flex min-w-0 flex-1 items-center gap-1.5 rounded-sm text-small"
            aria-label={
              deliveryLabel
                ? `Delivering to ${deliveryLabel}. Change delivery address`
                : "Set your delivery location"
            }
          >
            <span className="shrink-0 text-ink-subtle">Deliver to</span>
            <span className="truncate font-medium text-ink">
              {deliveryLabel ?? "Set your location"}
            </span>
            <ChevronDown className="size-3.5 shrink-0 text-ink-subtle" aria-hidden="true" />
          </Link>
        </div>
      </div>
    </header>
  );
}

/* -------------------------------------------------------------------------- */
/* Cart button                                                                */
/* -------------------------------------------------------------------------- */

function CartButton() {
  const { count } = useCart();

  return (
    <Link
      href="/cart"
      aria-label={
        count > 0 ? `Basket, ${count} items` : "Basket, empty"
      }
      className={cn(
        "relative grid size-9 shrink-0 place-items-center rounded-md",
        "text-ink-muted transition-colors hover:bg-surface-muted hover:text-ink",
      )}
    >
      <ShoppingBag className="size-5" aria-hidden="true" />
      {count > 0 ? (
        <span
          // Announce the change politely for screen-reader users without
          // stealing focus mid-typing in the search field.
          aria-live="polite"
          className="absolute -right-0.5 -top-0.5 grid min-w-[18px] place-items-center rounded-full bg-leaf-600 px-1 py-px text-[10px] font-semibold leading-none text-white tabular-nums"
        >
          {count > 99 ? "99+" : count}
        </span>
      ) : null}
    </Link>
  );
}

/* -------------------------------------------------------------------------- */
/* Account menu                                                               */
/* -------------------------------------------------------------------------- */

function AccountMenu({
  displayName,
  email,
  avatarUrl,
  onRequestSignOut,
}: {
  displayName: string;
  email: string | null;
  avatarUrl: string | null;
  onRequestSignOut: () => void;
}) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        className="hidden shrink-0 items-center gap-2 rounded-full border border-line bg-surface py-1 pl-1 pr-2.5 transition-colors hover:border-line-strong hover:bg-surface-muted focus-visible:outline-none lg:flex"
        aria-label={`Account menu for ${displayName}`}
      >
        <UserAvatar name={displayName} url={avatarUrl} size={28} />
        <span className="max-w-[8rem] truncate text-small font-medium text-ink">
          {displayName}
        </span>
        <ChevronDown className="size-3.5 shrink-0 text-ink-subtle" aria-hidden="true" />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        <DropdownMenuLabel className="normal-case">
          <span className="block text-caption text-ink-subtle">Signed in as</span>
          <span className="mt-0.5 block truncate text-small font-medium text-ink">
            {displayName}
          </span>
          {email ? (
            <span className="block truncate text-caption text-ink-subtle">{email}</span>
          ) : null}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/account">
            <UserRound aria-hidden="true" /> Your account
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/orders">
            <Receipt aria-hidden="true" /> Your orders
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/account/addresses">
            <MapPin aria-hidden="true" /> Saved addresses
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/notifications">
            <Sparkles aria-hidden="true" /> Notifications
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/contact">
            <Wallet aria-hidden="true" /> Help &amp; support
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          // No `preventDefault`: the menu closes first, then the confirmation
          // opens. Keeping the menu mounted under a modal leaves two overlays
          // fighting over focus.
          onSelect={() => onRequestSignOut()}
          className="text-danger focus:bg-danger-bg [&_svg]:text-danger"
        >
          <LogOut aria-hidden="true" /> Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

/* -------------------------------------------------------------------------- */
/* Mobile menu panel                                                          */
/* -------------------------------------------------------------------------- */

function MobileMenuPanel({
  categories,
  user,
  displayName,
  avatarUrl,
  onRequestSignOut,
}: {
  categories: HeaderCategory[];
  user: { id: string; email: string | null } | null;
  displayName: string;
  avatarUrl: string | null;
  onRequestSignOut: () => void;
}) {
  return (
    <SheetBody className="pt-2 pb-6">
      {/*
        Who is signed in. This replaced a "Sign in or create account" row that
        stayed on screen for signed-in customers, which read as if the session
        had been lost.
      */}
      {user ? (
        <div className="mb-5 flex items-center gap-3 rounded-lg border border-line bg-surface-muted px-3 py-3">
          <UserAvatar name={displayName} url={avatarUrl} size={44} />
          <div className="min-w-0">
            <p className="truncate text-body font-medium text-ink">{displayName}</p>
            {user.email ? (
              <p className="truncate text-small text-ink-muted">{user.email}</p>
            ) : null}
            <p className="mt-0.5 flex items-center gap-1 text-caption font-medium text-leaf-700">
              <span
                aria-hidden="true"
                className="inline-block size-1.5 rounded-full bg-leaf-600"
              />
              Signed in
            </p>
          </div>
        </div>
      ) : null}

      <p className="text-caption text-ink-subtle">Shop by category</p>
      <ul className="mt-2.5 grid grid-cols-2 gap-2">
        {categories.map((category) => (
          <li key={category.id}>
            <Link
              href={`/categories/${category.id}`}
              className="flex items-center gap-2.5 rounded-md border border-line bg-surface px-3 py-2.5 text-small font-medium text-ink transition-colors hover:border-leaf-600/40 hover:bg-leaf-50"
            >
              <span aria-hidden="true" className="text-lg">
                {category.glyph}
              </span>
              <span className="truncate">{category.name}</span>
            </Link>
          </li>
        ))}
      </ul>

      <p className="mt-6 text-caption text-ink-subtle">Shortcuts</p>
      <ul className="mt-2.5 flex flex-col gap-1">
        <MenuLink href="/offers" icon={<Sparkles aria-hidden="true" />}>
          Today&apos;s offers
        </MenuLink>
        <MenuLink href="/search" icon={<Search aria-hidden="true" />}>
          Search everything
        </MenuLink>
        <MenuLink href="/orders" icon={<Receipt aria-hidden="true" />}>
          Order history
        </MenuLink>
        <MenuLink href="/contact" icon={<Package aria-hidden="true" />}>
          Help &amp; support
        </MenuLink>
        {user ? (
          <>
            <MenuLink href="/account" icon={<UserRound aria-hidden="true" />}>
              Your account
            </MenuLink>
            <li>
              {/* `SheetClose` dismisses the sheet in the same tap; the dialog
                  itself lives in the header so it survives that unmount. */}
              <SheetClose asChild>
                <button
                  type="button"
                  onClick={onRequestSignOut}
                  className="flex w-full items-center gap-3 rounded-md px-3 py-2.5 text-left text-body text-danger transition-colors hover:bg-danger-bg"
                >
                  <LogOut className="size-[17px]" aria-hidden="true" />
                  Sign out
                </button>
              </SheetClose>
            </li>
          </>
        ) : (
          <MenuLink href="/login" icon={<UserRound aria-hidden="true" />}>
            Sign in or create account
          </MenuLink>
        )}
      </ul>
    </SheetBody>
  );
}

function MenuLink({
  href,
  icon,
  children,
}: {
  href: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <li>
      <Link
        href={href}
        className="flex items-center gap-3 rounded-md px-3 py-2.5 text-body text-ink transition-colors hover:bg-surface-muted [&_svg]:size-[17px] [&_svg]:text-ink-muted"
      >
        {icon}
        {children}
      </Link>
    </li>
  );
}
