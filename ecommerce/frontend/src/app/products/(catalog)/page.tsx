import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { Pagination, pageIndex } from "@/components/Pagination";
import { ProductCard } from "@/components/ProductCard";
import { Alert, EmptyState } from "@/components/ui";
import { apiGet, query } from "@/lib/server/data";
import { nonNegativeNumber, oneOf, param, positiveInt, type SearchParams } from "@/lib/search-params";
import type { Category, PageResponse, ProductSummary } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("catalog"))("metaTitle") };
}

const PAGE_SIZE = 12;
const SORTS = ["createdAt,desc", "price,asc", "price,desc", "name,asc", "ratingAverage,desc"] as const;

/** Message key of each sort option in the "catalog" namespace. */
const SORT_LABELS = {
  "createdAt,desc": "sortNewest",
  "price,asc": "sortPriceAsc",
  "price,desc": "sortPriceDesc",
  "name,asc": "sortName",
  "ratingAverage,desc": "sortRating",
} as const satisfies Record<(typeof SORTS)[number], string>;

export default async function ProductsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const keyword = param(params, "keyword")?.slice(0, 100);
  const categoryId = positiveInt(param(params, "categoryId"));
  const brand = param(params, "brand")?.slice(0, 100);
  const minPrice = nonNegativeNumber(param(params, "minPrice"));
  const maxPrice = nonNegativeNumber(param(params, "maxPrice"));
  const sort = oneOf(param(params, "sort"), SORTS) ?? "createdAt,desc";
  const page = pageIndex(param(params, "page"));
  const [categories, products, t] = await Promise.all([
    apiGet<Category[]>("/api/categories").catch(() => [] as Category[]),
    apiGet<PageResponse<ProductSummary>>(
      `/api/products${query({ keyword, categoryId, brand, minPrice, maxPrice, sort, page, size: PAGE_SIZE })}`,
    ).catch(() => null),
    getTranslations("catalog"),
  ]);

  const category = categories.flatMap((root) => [root, ...root.children]).find((item) => item.id === categoryId);
  const current = {
    keyword,
    categoryId: categoryId?.toString(),
    brand,
    minPrice: minPrice?.toString(),
    maxPrice: maxPrice?.toString(),
    sort,
  };

  return (
    <div className="container-page py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">
          {keyword ? t("searchTitle", { keyword }) : category ? category.name : t("allProducts")}
        </h1>
        {products && <p className="mt-1 text-sm text-slate-500">{t("count", { count: products.totalElements })}</p>}
      </div>

      <div className="grid gap-8 lg:grid-cols-[240px_1fr]">
        <aside className="flex flex-col gap-6">
          <nav aria-label={t("categoryFilter")} className="card p-4">
            <h2 className="mb-2 text-sm font-semibold text-slate-900">{t("categories")}</h2>
            <ul className="flex flex-col gap-1 text-sm">
              <li>
                <Link
                  href={`/products${query({ ...current, categoryId: undefined })}`}
                  className={!categoryId ? "font-medium text-brand-600" : "text-slate-600 hover:text-brand-600"}
                >
                  {t("allCategories")}
                </Link>
              </li>
              {categories.map((root) => (
                <li key={root.id}>
                  <Link
                    href={`/products${query({ ...current, categoryId: root.id })}`}
                    className={root.id === categoryId ? "font-medium text-brand-600" : "text-slate-700 hover:text-brand-600"}
                  >
                    {root.name}
                  </Link>
                  <ul className="mt-1 mb-1 ml-3 flex flex-col gap-1 border-l border-slate-200 pl-3">
                    {root.children.map((child) => (
                      <li key={child.id}>
                        <Link
                          href={`/products${query({ ...current, categoryId: child.id })}`}
                          className={child.id === categoryId ? "font-medium text-brand-600" : "text-slate-500 hover:text-brand-600"}
                        >
                          {child.name}
                        </Link>
                      </li>
                    ))}
                  </ul>
                </li>
              ))}
            </ul>
          </nav>

          <form method="get" action="/products" className="card flex flex-col gap-3 p-4">
            <h2 className="text-sm font-semibold text-slate-900">{t("filters")}</h2>
            {keyword && <input type="hidden" name="keyword" value={keyword} />}
            {categoryId && <input type="hidden" name="categoryId" value={categoryId} />}
            <div>
              <label htmlFor="brand" className="label">
                {t("brand")}
              </label>
              <input
                id="brand"
                name="brand"
                defaultValue={brand}
                maxLength={100}
                className="input"
                placeholder={t("brandPlaceholder")}
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="minPrice" className="label">
                  {t("priceFrom")}
                </label>
                <input id="minPrice" name="minPrice" type="number" min={0} step={1} defaultValue={minPrice} className="input" />
              </div>
              <div>
                <label htmlFor="maxPrice" className="label">
                  {t("priceTo")}
                </label>
                <input id="maxPrice" name="maxPrice" type="number" min={0} step={1} defaultValue={maxPrice} className="input" />
              </div>
            </div>
            <div>
              <label htmlFor="sort" className="label">
                {t("sort")}
              </label>
              <select id="sort" name="sort" defaultValue={sort} className="input">
                {SORTS.map((option) => (
                  <option key={option} value={option}>
                    {t(SORT_LABELS[option])}
                  </option>
                ))}
              </select>
            </div>
            <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
              {t("apply")}
            </button>
            {(brand || minPrice !== undefined || maxPrice !== undefined) && (
              <Link href={`/products${query({ keyword, categoryId, sort })}`} className="link text-center text-sm">
                {t("resetFilters")}
              </Link>
            )}
          </form>
        </aside>

        <section aria-label={t("results")}>
          {!products ? (
            <Alert tone="warning">{t("unavailable")}</Alert>
          ) : products.content.length === 0 ? (
            <EmptyState title={t("empty")}>
              <Link href="/products" className="link text-sm">
                {t("viewAll")}
              </Link>
            </EmptyState>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
                {products.content.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
              <Pagination page={products.page} totalPages={products.totalPages} basePath="/products" params={current} />
            </>
          )}
        </section>
      </div>
    </div>
  );
}
