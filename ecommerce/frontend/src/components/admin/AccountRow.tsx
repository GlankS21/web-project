"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Badge } from "@/components/ui";
import { api } from "@/lib/client/api";
import { errorMessage } from "@/lib/errors";
import { formatDate, formatDateTime } from "@/lib/format";
import type { Account, RoleName } from "@/lib/types";

/** One account with its role and enabled switches; an administrator cannot lock themselves out. */
export function AccountRow({ account, self }: { account: Account; self: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const admin = account.roles.includes("ROLE_ADMIN");

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  const toggleAdmin = () => {
    const roles: RoleName[] = admin ? ["ROLE_CUSTOMER"] : ["ROLE_CUSTOMER", "ROLE_ADMIN"];
    const question = admin ? `Thu hồi quyền quản trị của ${account.email}?` : `Cấp quyền quản trị cho ${account.email}?`;
    if (window.confirm(question)) {
      void run(() => api.put(`/auth/admin/accounts/${account.id}/roles`, { roles }));
    }
  };

  const toggleEnabled = () => {
    const question = account.enabled
      ? `Khoá tài khoản ${account.email}? Mọi phiên đăng nhập của tài khoản sẽ kết thúc.`
      : `Mở khoá tài khoản ${account.email}?`;
    if (window.confirm(question)) {
      void run(() => api.patch(`/auth/admin/accounts/${account.id}/status`, { enabled: !account.enabled }));
    }
  };

  return (
    <tr>
      <td>
        <span className="font-medium text-slate-900">{account.email}</span>
        {self && <span className="ml-2 text-xs text-slate-500">(bạn)</span>}
        {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
      </td>
      <td>
        <div className="flex items-center gap-2">
          {admin ? <Badge tone="info">Quản trị</Badge> : <Badge>Khách hàng</Badge>}
          {!self && (
            <button type="button" className="link text-xs" disabled={busy} onClick={toggleAdmin}>
              {admin ? "Thu hồi" : "Cấp quyền"}
            </button>
          )}
        </div>
      </td>
      <td>
        <div className="flex items-center gap-2">
          {account.enabled ? <Badge tone="success">Hoạt động</Badge> : <Badge tone="danger">Đã khoá</Badge>}
          {!self && (
            <button type="button" className="link text-xs" disabled={busy} onClick={toggleEnabled}>
              {account.enabled ? "Khoá" : "Mở khoá"}
            </button>
          )}
        </div>
      </td>
      <td className="whitespace-nowrap text-slate-500">{account.lastLoginAt ? formatDateTime(account.lastLoginAt) : "—"}</td>
      <td className="whitespace-nowrap text-slate-500">{formatDate(account.createdAt)}</td>
    </tr>
  );
}
