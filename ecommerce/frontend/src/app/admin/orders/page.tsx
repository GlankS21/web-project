import type { Metadata } from "next";
import { Pagination, pageIndex } from "@/components/Pagination";
import { OrderTable } from "@/components/order/OrderTable";
import { PageTitle } from "@/components/ui";
import { ORDER_STATUS } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { oneOf, param, type SearchParams } from "@/lib/search-params";
import type { OrderStatus, OrderSummary, PageResponse } from "@/lib/types";

export const metadata: Metadata = { title: "Đơn hàng" };

const STATUSES = Object.keys(ORDER_STATUS) as OrderStatus[];

export default async function AdminOrdersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const status = oneOf(param(params, "status"), STATUSES);
  const keyword = param(params, "keyword")?.slice(0, 100);
  const page = pageIndex(param(params, "page"));
  const orders = await apiGet<PageResponse<OrderSummary>>(
    `/api/orders/admin${query({ status, keyword, page, size: 20, sort: "createdAt,desc" })}`,
    { auth: true },
  );

  return (
    <>
      <PageTitle>Đơn hàng</PageTitle>
      <form method="get" className="mb-4 flex flex-wrap gap-2">
        <input
          name="keyword"
          defaultValue={keyword}
          maxLength={100}
          placeholder="Mã đơn hoặc e-mail khách hàng"
          className="input max-w-xs"
          aria-label="Tìm đơn hàng"
        />
        <select name="status" defaultValue={status ?? ""} className="input w-auto" aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {ORDER_STATUS[value].label}
            </option>
          ))}
        </select>
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          Lọc
        </button>
      </form>
      <p className="mb-2 text-sm text-slate-500">{orders.totalElements} đơn hàng</p>
      <div className="card overflow-x-auto">
        <OrderTable orders={orders.content} />
      </div>
      <Pagination page={orders.page} totalPages={orders.totalPages} basePath="/admin/orders" params={{ status, keyword }} />
    </>
  );
}
