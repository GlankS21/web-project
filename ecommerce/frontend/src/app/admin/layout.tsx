import type { Metadata } from "next";
import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { NavTabs } from "@/components/NavTabs";
import { isAdmin } from "@/lib/session";
import { getSession } from "@/lib/server/data";

export const metadata: Metadata = { title: { default: "Quản trị", template: "%s | Quản trị TechShop" } };

const TABS = [
  { href: "/admin", label: "Tổng quan", exact: true },
  { href: "/admin/orders", label: "Đơn hàng" },
  { href: "/admin/products", label: "Sản phẩm" },
  { href: "/admin/categories", label: "Danh mục" },
  { href: "/admin/inventory", label: "Tồn kho" },
  { href: "/admin/accounts", label: "Tài khoản" },
  { href: "/admin/notifications", label: "E-mail" },
];

/** The proxy already redirects non-administrators; this check covers direct server renders too. */
export default async function AdminLayout({ children }: { children: ReactNode }) {
  if (!isAdmin(await getSession())) {
    redirect("/");
  }
  return (
    <div className="container-page grid gap-8 py-8 lg:grid-cols-[200px_1fr]">
      <aside>
        <p className="mb-3 hidden px-3 text-xs font-semibold tracking-wider text-slate-400 uppercase lg:block">Quản trị</p>
        <NavTabs tabs={TABS} label="Quản trị" />
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
