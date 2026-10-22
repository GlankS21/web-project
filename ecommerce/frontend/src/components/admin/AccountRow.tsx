"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Badge } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { formatDate, formatDateTime } from "@/lib/format";
import type { Account, RoleName } from "@/lib/types";

/** One account with its role and enabled switches; an administrator cannot lock themselves out. */
export function AccountRow({ account, self }: { account: Account; self: boolean }) {
  const t = useTranslations("adminAccounts");
  const errorMessage = useErrorMessage();
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
    const question = admin
      ? t("revokeAdminConfirm", { email: account.email })
      : t("grantAdminConfirm", { email: account.email });
    if (window.confirm(question)) {
      void run(() => api.put(`/auth/admin/accounts/${account.id}/roles`, { roles }));
    }
  };

  const toggleEnabled = () => {
    const question = account.enabled
      ? t("lockConfirm", { email: account.email })
      : t("unlockConfirm", { email: account.email });
    if (window.confirm(question)) {
      void run(() => api.patch(`/auth/admin/accounts/${account.id}/status`, { enabled: !account.enabled }));
    }
  };

  return (
    <tr>
      <td>
        <Link href={`/admin/accounts/${account.id}`} className="font-medium text-slate-900 hover:text-brand-600">
          {account.email}
        </Link>
        {self && <span className="ml-2 text-xs text-slate-500">{t("you")}</span>}
        {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
      </td>
      <td>
        <div className="flex items-center gap-2">
          {admin ? <Badge tone="info">{t("admin")}</Badge> : <Badge>{t("customer")}</Badge>}
          {!self && (
            <button type="button" className="link text-xs" disabled={busy} onClick={toggleAdmin}>
              {admin ? t("revokeAdmin") : t("grantAdmin")}
            </button>
          )}
        </div>
      </td>
      <td>
        <div className="flex items-center gap-2">
          {account.enabled ? <Badge tone="success">{t("enabled")}</Badge> : <Badge tone="danger">{t("disabled")}</Badge>}
          {!self && (
            <button type="button" className="link text-xs" disabled={busy} onClick={toggleEnabled}>
              {account.enabled ? t("lock") : t("unlock")}
            </button>
          )}
        </div>
      </td>
      <td className="whitespace-nowrap text-slate-500">{account.lastLoginAt ? formatDateTime(account.lastLoginAt) : "—"}</td>
      <td className="whitespace-nowrap text-slate-500">{formatDate(account.createdAt)}</td>
    </tr>
  );
}
