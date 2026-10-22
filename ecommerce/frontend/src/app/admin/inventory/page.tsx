import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { Pagination, pageIndex } from "@/components/Pagination";
import { Badge, PageTitle } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { InventoryItem, PageResponse, ProductSummary } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminInventory"))("title") };
}

export default async function AdminInventoryPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const lowStock = param(params, "lowStock") === "true";
  const page = pageIndex(param(params, "page"));
  const [items, t] = await Promise.all([
    apiGet<PageResponse<InventoryItem>>(`/api/inventory${query({ lowStock: lowStock || undefined, page, size: 20 })}`, {
      auth: true,
    }),
    getTranslations("adminInventory"),
  ]);
  // Names come from product-service; the stock list still renders without them
  const ids = items.content.map((item) => item.productId);
  const names = new Map<number, string>(
    ids.length
      ? await apiGet<ProductSummary[]>(`/api/products/by-ids?ids=${ids.join(",")}`, { auth: true })
          .then((products) => products.map((product) => [product.id, product.name] as [number, string]))
          .catch(() => [])
      : [],
  );

  const tabClass = (active: boolean) =>
    `rounded-full px-3 py-1.5 text-sm ${active ? "bg-brand-600 text-white" : "bg-white text-slate-600 ring-1 ring-slate-200 hover:bg-slate-50"}`;

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <nav className="mb-4 flex gap-2" aria-label={t("filter")}>
        <Link href="/admin/inventory" className={tabClass(!lowStock)}>
          {t("all")}
        </Link>
        <Link href="/admin/inventory?lowStock=true" className={tabClass(lowStock)}>
          {t("lowStock")}
        </Link>
      </nav>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>{t("product")}</th>
              <th className="text-right">{t("onHand")}</th>
              <th className="text-right">{t("reserved")}</th>
              <th className="text-right">{t("available")}</th>
              <th className="text-right">{t("threshold")}</th>
              <th>{t("updatedAt")}</th>
            </tr>
          </thead>
          <tbody>
            {items.content.map((item) => (
              <tr key={item.productId}>
                <td>
                  <Link href={`/admin/inventory/${item.productId}`} className="link font-medium">
                    {names.get(item.productId) ?? t("productFallback", { id: item.productId })}
                  </Link>
                  <p className="text-xs text-slate-500">{item.sku ?? "—"}</p>
                </td>
                <td className="text-right">{item.quantityOnHand}</td>
                <td className="text-right">{item.quantityReserved}</td>
                <td className="text-right">
                  {item.lowStock ? <Badge tone="danger">{item.availableQuantity}</Badge> : item.availableQuantity}
                </td>
                <td className="text-right">{item.lowStockThreshold}</td>
                <td className="whitespace-nowrap text-slate-500">{formatDateTime(item.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {items.content.length === 0 && <p className="p-4 text-sm text-slate-500">{t("empty")}</p>}
      </div>
      <Pagination
        page={items.page}
        totalPages={items.totalPages}
        basePath="/admin/inventory"
        params={{ lowStock: lowStock ? "true" : undefined }}
      />
    </>
  );
}
