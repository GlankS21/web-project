import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { NavTabs } from "@/components/NavTabs";

export default function AccountLayout({ children }: { children: ReactNode }) {
  const t = useTranslations("account");
  const tabs = [
    { href: "/account", label: t("tabProfile"), exact: true },
    { href: "/account/addresses", label: t("tabAddresses") },
    { href: "/orders", label: t("tabOrders") },
    { href: "/account/notifications", label: t("tabNotifications") },
    { href: "/account/password", label: t("tabPassword") },
  ];
  return (
    <div className="container-page grid gap-8 py-8 lg:grid-cols-[200px_1fr]">
      <aside>
        <NavTabs tabs={tabs} label={t("navLabel")} />
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
