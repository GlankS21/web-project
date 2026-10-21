import type { Metadata } from "next";
import Link from "next/link";
import { Pagination, pageIndex } from "@/components/Pagination";
import { ButtonLink, EmptyState, PageTitle, StatusBadge, Thumb } from "@/components/ui";
import { formatDateTime, formatMoney, ORDER_STATUS } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { oneOf, param, type SearchParams } from "@/lib/search-params";
import type { OrderStatus, OrderSummary, PageResponse } from "@/lib/types";

export const metadata: Metadata = { title: "Đơn hàng của tôi" };

const STATUSES = Object.keys(ORDER_STATUS) as OrderStatus[];

export default async function OrdersPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const status = oneOf(param(params, "status"), STATUSES);
  const page = pageIndex(param(params, "page"));
  const orders = await apiGet<PageResponse<OrderSummary>>(
    `/api/orders${query({ status, page, size: 10, sort: "createdAt,desc" })}`,
    { auth: true },
  );

  const tabClass = (active: boolean) =>
    `rounded-full px-3 py-1.5 text-sm whitespace-nowrap ${active ? "bg-brand-600 text-white" : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50"}`;

  return (
    <div className="container-page py-8">
      <PageTitle>Đơn hàng của tôi</PageTitle>
      <nav aria-label="Lọc theo trạng thái" className="mb-6 flex gap-2 overflow-x-auto pb-1">
        <Link href="/orders" className={tabClass(!status)}>
          Tất cả
        </Link>
        {STATUSES.map((value) => (
          <Link key={value} href={`/orders?status=${value}`} className={tabClass(status === value)}>
            {ORDER_STATUS[value].label}
          </Link>
        ))}
      </nav>

      {orders.content.length === 0 ? (
        <EmptyState title={status ? "Không có đơn hàng ở trạng thái này" : "Bạn chưa có đơn hàng nào"}>
          <ButtonLink href="/products">Mua sắm ngay</ButtonLink>
        </EmptyState>
      ) : (
        <ul className="flex flex-col gap-3">
          {orders.content.map((order) => (
            <li key={order.id}>
              <Link href={`/orders/${order.id}`} className="card flex items-center gap-4 p-4 transition-shadow hover:shadow-md">
                <Thumb src={order.firstItemThumbnailUrl} alt={order.firstItemName ?? order.orderNumber} />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-medium text-slate-900">{order.orderNumber}</span>
                    <StatusBadge value={order.status} labels={ORDER_STATUS} />
                  </div>
                  <p className="mt-1 truncate text-sm text-slate-600">
                    {order.firstItemName}
                    {order.itemCount > 1 && ` và ${order.itemCount - 1} sản phẩm khác`}
                  </p>
                  <p className="text-xs text-slate-500">{formatDateTime(order.createdAt)}</p>
                </div>
                <p className="font-semibold whitespace-nowrap text-slate-900">{formatMoney(order.totalAmount, order.currency)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={orders.page} totalPages={orders.totalPages} basePath="/orders" params={{ status }} />
    </div>
  );
}
