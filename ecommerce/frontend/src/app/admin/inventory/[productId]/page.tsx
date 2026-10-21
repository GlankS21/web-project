import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { StockForms } from "@/components/admin/StockForms";
import { Pagination, pageIndex } from "@/components/Pagination";
import { Badge, PageTitle } from "@/components/ui";
import { formatDateTime, MOVEMENT_TYPE } from "@/lib/format";
import { apiGet, apiGetOrNotFound, query } from "@/lib/server/data";
import { param, positiveInt, type SearchParams } from "@/lib/search-params";
import type { InventoryItem, PageResponse, Product, StockMovement } from "@/lib/types";

export const metadata: Metadata = { title: "Tồn kho sản phẩm" };

interface Props {
  params: Promise<{ productId: string }>;
  searchParams: Promise<SearchParams>;
}

export default async function InventoryItemPage({ params, searchParams }: Props) {
  const productId = positiveInt((await params).productId);
  if (!productId) {
    notFound();
  }
  const page = pageIndex(param(await searchParams, "page"));
  const [item, product, movements] = await Promise.all([
    apiGetOrNotFound<InventoryItem>(`/api/inventory/${productId}`, { auth: true }),
    apiGet<Product>(`/api/products/${productId}`, { auth: true }).catch(() => null),
    apiGet<PageResponse<StockMovement>>(`/api/inventory/${productId}/movements${query({ page, size: 20 })}`, { auth: true }),
  ]);

  const signed = (value: number) => (value > 0 ? `+${value}` : value === 0 ? "0" : String(value));

  return (
    <>
      <Link href="/admin/inventory" className="link text-sm">
        ← Tồn kho
      </Link>
      <div className="mt-3">
        <PageTitle
          actions={
            product && (
              <Link href={`/admin/products/${productId}`} className="link text-sm">
                Sửa sản phẩm
              </Link>
            )
          }
        >
          {product?.name ?? `Sản phẩm #${productId}`}
        </PageTitle>
      </div>

      <dl className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
        {[
          ["Tồn thực tế", item.quantityOnHand],
          ["Đang giữ cho đơn", item.quantityReserved],
          ["Có thể bán", item.availableQuantity],
          ["Ngưỡng cảnh báo", item.lowStockThreshold],
        ].map(([label, value]) => (
          <div key={label} className="card p-4">
            <dt className="text-sm text-slate-500">{label}</dt>
            <dd className="mt-1 text-2xl font-semibold text-slate-900">{value}</dd>
          </div>
        ))}
      </dl>

      <StockForms item={item} />

      <section className="card mt-8 overflow-x-auto" aria-labelledby="movements-title">
        <h2 id="movements-title" className="p-4 font-semibold text-slate-900">
          Lịch sử biến động
        </h2>
        <table className="table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Loại</th>
              <th className="text-right">Tồn thực tế</th>
              <th className="text-right">Đang giữ</th>
              <th>Tham chiếu / lý do</th>
            </tr>
          </thead>
          <tbody>
            {movements.content.map((movement) => (
              <tr key={movement.id}>
                <td className="whitespace-nowrap text-slate-500">{formatDateTime(movement.createdAt)}</td>
                <td>
                  <Badge>{MOVEMENT_TYPE[movement.type]}</Badge>
                </td>
                <td className="text-right whitespace-nowrap">
                  {signed(movement.onHandChange)} → {movement.onHandAfter}
                </td>
                <td className="text-right whitespace-nowrap">
                  {signed(movement.reservedChange)} → {movement.reservedAfter}
                </td>
                <td className="max-w-72 truncate text-slate-600">
                  {movement.reason ?? movement.reference ?? "—"}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {movements.content.length === 0 && <p className="p-4 text-sm text-slate-500">Chưa có biến động.</p>}
      </section>
      <Pagination page={movements.page} totalPages={movements.totalPages} basePath={`/admin/inventory/${productId}`} params={{}} />
    </>
  );
}
