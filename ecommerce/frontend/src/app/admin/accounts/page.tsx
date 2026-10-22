import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AccountRow } from "@/components/admin/AccountRow";
import { Pagination, pageIndex } from "@/components/Pagination";
import { PageTitle } from "@/components/ui";
import { apiGet, getSession, query } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { Account, PageResponse } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminAccounts"))("title") };
}

export default async function AdminAccountsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const email = param(params, "email")?.slice(0, 255);
  const page = pageIndex(param(params, "page"));
  const [accounts, session, t, tCommon] = await Promise.all([
    apiGet<PageResponse<Account>>(`/api/auth/admin/accounts${query({ email, page, size: 20 })}`, { auth: true }),
    getSession(),
    getTranslations("adminAccounts"),
    getTranslations("common"),
  ]);

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <form method="get" className="mb-4 flex gap-2">
        <input
          name="email"
          defaultValue={email}
          maxLength={255}
          placeholder={t("searchPlaceholder")}
          className="input max-w-xs"
          aria-label={t("searchPlaceholder")}
        />
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          {tCommon("search")}
        </button>
      </form>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>{t("email")}</th>
              <th>{t("roles")}</th>
              <th>{t("status")}</th>
              <th>{t("lastLogin")}</th>
              <th>{t("createdAt")}</th>
            </tr>
          </thead>
          <tbody>
            {accounts.content.map((account) => (
              <AccountRow key={account.id} account={account} self={account.id === session?.userId} />
            ))}
          </tbody>
        </table>
        {accounts.content.length === 0 && <p className="p-4 text-sm text-slate-500">{t("empty")}</p>}
      </div>
      <Pagination page={accounts.page} totalPages={accounts.totalPages} basePath="/admin/accounts" params={{ email }} />
    </>
  );
}
