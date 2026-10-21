import type { Metadata } from "next";
import { AccountRow } from "@/components/admin/AccountRow";
import { Pagination, pageIndex } from "@/components/Pagination";
import { PageTitle } from "@/components/ui";
import { apiGet, getSession, query } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { Account, PageResponse } from "@/lib/types";

export const metadata: Metadata = { title: "Tài khoản" };

export default async function AdminAccountsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const email = param(params, "email")?.slice(0, 255);
  const page = pageIndex(param(params, "page"));
  const [accounts, session] = await Promise.all([
    apiGet<PageResponse<Account>>(`/api/auth/admin/accounts${query({ email, page, size: 20 })}`, { auth: true }),
    getSession(),
  ]);

  return (
    <>
      <PageTitle>Tài khoản</PageTitle>
      <form method="get" className="mb-4 flex gap-2">
        <input name="email" defaultValue={email} maxLength={255} placeholder="Tìm theo e-mail" className="input max-w-xs" aria-label="E-mail" />
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          Tìm
        </button>
      </form>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>E-mail</th>
              <th>Quyền</th>
              <th>Trạng thái</th>
              <th>Đăng nhập gần nhất</th>
              <th>Ngày tạo</th>
            </tr>
          </thead>
          <tbody>
            {accounts.content.map((account) => (
              <AccountRow key={account.id} account={account} self={account.id === session?.userId} />
            ))}
          </tbody>
        </table>
        {accounts.content.length === 0 && <p className="p-4 text-sm text-slate-500">Không có tài khoản.</p>}
      </div>
      <Pagination page={accounts.page} totalPages={accounts.totalPages} basePath="/admin/accounts" params={{ email }} />
    </>
  );
}
