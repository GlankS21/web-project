import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Pagination, pageIndex } from "@/components/Pagination";
import { OrderTable } from "@/components/order/OrderTable";
import { PageTitle } from "@/components/ui";
import { ORDER_STATUS_TONE } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { isUuid, oneOf, param, type SearchParams } from "@/lib/search-params";
import type { OrderStatus, OrderSummary, PageResponse } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminOrders"))("title") };
}

const STATUSES = Object.keys(ORDER_STATUS_TONE) as OrderStatus[];

export default async function AdminOrdersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const status = oneOf(param(params, "status"), STATUSES);
  const keyword = param(params, "keyword")?.slice(0, 100);
  // Orders of one customer, from the account page
  const requestedUser = param(params, "userId");
  const userId = requestedUser && isUuid(requestedUser) ? requestedUser : undefined;
  const page = pageIndex(param(params, "page"));
  const [orders, t, tStatus] = await Promise.all([
    apiGet<PageResponse<OrderSummary>>(
      `/api/orders/admin${query({ status, keyword, userId, page, size: 20, sort: "createdAt,desc" })}`,
      { auth: true },
    ),
    getTranslations("adminOrders"),
    getTranslations("orderStatus"),
  ]);

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <form method="get" className="mb-4 flex flex-wrap gap-2">
        {userId && <input type="hidden" name="userId" value={userId} />}
        <input
          name="keyword"
          defaultValue={keyword}
          maxLength={100}
          placeholder={t("searchPlaceholder")}
          className="input max-w-xs"
          aria-label={t("searchLabel")}
        />
        <select name="status" defaultValue={status ?? ""} className="input w-auto" aria-label={t("status")}>
          <option value="">{t("allStatuses")}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {tStatus(value)}
            </option>
          ))}
        </select>
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          {t("filter")}
        </button>
      </form>
      <p className="mb-2 text-sm text-slate-500">{t("count", { count: orders.totalElements })}</p>
      <div className="card overflow-x-auto">
        <OrderTable orders={orders.content} />
      </div>
      <Pagination page={orders.page} totalPages={orders.totalPages} basePath="/admin/orders" params={{ status, keyword, userId }} />
    </>
  );
}
