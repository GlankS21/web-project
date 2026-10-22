"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { useCart, useSession } from "@/components/providers";
import { isAdmin } from "@/lib/session";

export function CartLink() {
  const t = useTranslations("nav");
  const tCommon = useTranslations("common");
  const { count } = useCart();
  return (
    <Link
      href="/cart"
      className="relative inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
      aria-label={count ? t("cartWithCount", { items: tCommon("cartItems", { count }) }) : t("cart")}
    >
      <svg className="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
        <path d="M3 3h2l2.4 12.2a2 2 0 0 0 2 1.6h8.9a2 2 0 0 0 2-1.6L22 7H6" strokeLinecap="round" strokeLinejoin="round" />
        <circle cx="9.5" cy="20.5" r="1.3" />
        <circle cx="18" cy="20.5" r="1.3" />
      </svg>
      <span className="hidden sm:inline">{t("cart")}</span>
      {count !== null && count > 0 && (
        <span className="absolute -top-0.5 left-6 min-w-5 rounded-full bg-accent-500 px-1.5 text-center text-[11px] leading-5 font-semibold text-white">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

export function AccountMenu() {
  const t = useTranslations("nav");
  const session = useSession();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const close = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const escape = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  if (!session) {
    return (
      <div className="flex items-center gap-1">
        <Link href="/login" className="rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100">
          {t("login")}
        </Link>
        <Link
          href="/register"
          className="hidden rounded-lg bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 sm:inline-block"
        >
          {t("register")}
        </Link>
      </div>
    );
  }

  const logout = async () => {
    setOpen(false);
    await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    router.push("/");
    router.refresh();
  };

  const itemClass = "block w-full px-4 py-2 text-left text-sm text-slate-700 hover:bg-slate-50";
  return (
    <div className="relative" ref={menuRef}>
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={t("accountMenu", { email: session.email })}
        className="inline-flex max-w-56 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-semibold text-brand-700 uppercase">
          {session.email.charAt(0) || "?"}
        </span>
        <span className="hidden truncate md:inline">{session.email}</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-30 mt-1 w-56 overflow-hidden rounded-lg border border-slate-200 bg-white py-1 shadow-lg">
          {isAdmin(session) && (
            <Link href="/admin" role="menuitem" className={`${itemClass} font-medium text-brand-700`} onClick={() => setOpen(false)}>
              {t("admin")}
            </Link>
          )}
          <Link href="/orders" role="menuitem" className={itemClass} onClick={() => setOpen(false)}>
            {t("orders")}
          </Link>
          <Link href="/account" role="menuitem" className={itemClass} onClick={() => setOpen(false)}>
            {t("account")}
          </Link>
          <Link href="/account/notifications" role="menuitem" className={itemClass} onClick={() => setOpen(false)}>
            {t("notifications")}
          </Link>
          <button type="button" role="menuitem" className={`${itemClass} border-t border-slate-100 text-rose-600`} onClick={logout}>
            {t("logout")}
          </button>
        </div>
      )}
    </div>
  );
}

export function SearchBox({ initial = "" }: { initial?: string }) {
  const t = useTranslations("nav");
  const router = useRouter();
  const [keyword, setKeyword] = useState(initial);
  return (
    <form
      role="search"
      className="flex-1"
      onSubmit={(event) => {
        event.preventDefault();
        const value = keyword.trim();
        router.push(value ? `/products?keyword=${encodeURIComponent(value)}` : "/products");
      }}
    >
      <label htmlFor="site-search" className="sr-only">
        {t("searchLabel")}
      </label>
      <input
        id="site-search"
        type="search"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        placeholder={t("searchPlaceholder")}
        maxLength={100}
        className="input rounded-full bg-slate-50"
      />
    </form>
  );
}
