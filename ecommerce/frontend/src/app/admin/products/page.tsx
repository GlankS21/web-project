import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { ProductStatusSelect } from "@/components/admin/ProductStatusSelect";
import { Pagination, pageIndex } from "@/components/Pagination";
import { ButtonLink, PageTitle, Thumb } from "@/components/ui";
import { formatMoney, PRODUCT_STATUS_TONE } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { oneOf, param, positiveInt, type SearchParams } from "@/lib/search-params";
import type { Category, PageResponse, ProductStatus, ProductSummary } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminProducts"))("title") };
}

const STATUSES = Object.keys(PRODUCT_STATUS_TONE) as ProductStatus[];

export default async function AdminProductsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const keyword = param(params, "keyword")?.slice(0, 100);
  const status = oneOf(param(params, "status"), STATUSES);
  const categoryId = positiveInt(param(params, "categoryId"));
  const page = pageIndex(param(params, "page"));

  const [products, categories, t, tStatus] = await Promise.all([
    apiGet<PageResponse<ProductSummary>>(
      `/api/products${query({ keyword, status, categoryId, page, size: 20, sort: "createdAt,desc" })}`,
      { auth: true },
    ),
    apiGet<Category[]>("/api/categories").catch(() => [] as Category[]),
    getTranslations("adminProducts"),
    getTranslations("productStatus"),
  ]);

  return (
    <>
      <PageTitle actions={<ButtonLink href="/admin/products/new">{t("new")}</ButtonLink>}>{t("title")}</PageTitle>
      <form method="get" className="mb-4 flex flex-wrap gap-2">
        <input
          name="keyword"
          defaultValue={keyword}
          maxLength={100}
          placeholder={t("searchPlaceholder")}
          className="input max-w-xs"
          aria-label={t("searchLabel")}
        />
        <select name="categoryId" defaultValue={categoryId ?? ""} className="input w-auto" aria-label={t("category")}>
          <option value="">{t("allCategories")}</option>
          {categories.map((root) => (
            <optgroup key={root.id} label={root.name}>
              <option value={root.id}>{t("wholeCategory", { name: root.name })}</option>
              {root.children.map((child) => (
                <option key={child.id} value={child.id}>
                  {child.name}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
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
      <p className="mb-2 text-sm text-slate-500">{t("count", { count: products.totalElements })}</p>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>{t("product")}</th>
              <th>{t("category")}</th>
              <th className="text-right">{t("price")}</th>
              <th>{t("status")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {products.content.map((product) => (
              <tr key={product.id}>
                <td>
                  <div className="flex items-center gap-3">
                    <Thumb src={product.thumbnailUrl} alt={product.name} className="h-10 w-10" />
                    <div className="min-w-0">
                      <Link href={`/admin/products/${product.id}`} className="link font-medium">
                        {product.name}
                      </Link>
                      <p className="text-xs text-slate-500">{product.sku}</p>
                    </div>
                  </div>
                </td>
                <td className="whitespace-nowrap">{product.categoryName}</td>
                <td className="text-right whitespace-nowrap">{formatMoney(product.price, product.currency)}</td>
                <td>
                  <ProductStatusSelect productId={product.id} status={product.status} />
                </td>
                <td className="text-right whitespace-nowrap">
                  <Link href={`/admin/inventory/${product.id}`} className="link text-sm">
                    {t("inventory")}
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {products.content.length === 0 && <p className="p-4 text-sm text-slate-500">{t("empty")}</p>}
      </div>
      <Pagination
        page={products.page}
        totalPages={products.totalPages}
        basePath="/admin/products"
        params={{ keyword, status, categoryId }}
      />
    </>
  );
}
