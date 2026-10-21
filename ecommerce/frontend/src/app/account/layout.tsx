import type { ReactNode } from "react";
import { NavTabs } from "@/components/NavTabs";

const TABS = [
  { href: "/account", label: "Hồ sơ", exact: true },
  { href: "/account/addresses", label: "Sổ địa chỉ" },
  { href: "/orders", label: "Đơn hàng" },
  { href: "/account/notifications", label: "Thông báo" },
  { href: "/account/password", label: "Đổi mật khẩu" },
];

export default function AccountLayout({ children }: { children: ReactNode }) {
  return (
    <div className="container-page grid gap-8 py-8 lg:grid-cols-[200px_1fr]">
      <aside>
        <NavTabs tabs={TABS} label="Tài khoản" />
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
