import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { NavTabs } from "@/components/NavTabs";
import { isAdmin } from "@/lib/session";
import { getSession } from "@/lib/server/data";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("adminNav");
  return { title: { default: t("metaTitle"), template: t("metaTemplate") } };
}

/** The proxy already redirects non-administrators; this check covers direct server renders too. */
export default async function AdminLayout({ children }: { children: ReactNode }) {
  if (!isAdmin(await getSession())) {
    redirect("/");
  }
  const t = await getTranslations("adminNav");
  const tabs = [
    { href: "/admin", label: t("dashboard"), exact: true },
    { href: "/admin/orders", label: t("orders") },
    { href: "/admin/payments", label: t("payments") },
    { href: "/admin/coupons", label: t("coupons") },
    { href: "/admin/products", label: t("products") },
    { href: "/admin/reviews", label: t("reviews") },
    { href: "/admin/categories", label: t("categories") },
    { href: "/admin/inventory", label: t("inventory") },
    { href: "/admin/accounts", label: t("accounts") },
    { href: "/admin/notifications", label: t("notifications") },
  ];
  return (
    <div className="container-page grid gap-8 py-8 lg:grid-cols-[200px_1fr]">
      <aside>
        <p className="mb-3 hidden px-3 text-xs font-semibold tracking-wider text-slate-400 uppercase lg:block">{t("title")}</p>
        <NavTabs tabs={tabs} label={t("title")} />
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
