import type { Metadata } from "next";
import Link from "next/link";
import { Pagination, pageIndex } from "@/components/Pagination";
import { ProductCard } from "@/components/ProductCard";
import { Alert, EmptyState } from "@/components/ui";
import { apiGet, query } from "@/lib/server/data";
import { nonNegativeNumber, oneOf, param, positiveInt, type SearchParams } from "@/lib/search-params";
import type { Category, PageResponse, ProductSummary } from "@/lib/types";

export const metadata: Metadata = { title: "Sản phẩm" };

const PAGE_SIZE = 12;
const SORTS = [
  { value: "createdAt,desc", label: "Mới nhất" },
  { value: "price,asc", label: "Giá tăng dần" },
  { value: "price,desc", label: "Giá giảm dần" },
  { value: "name,asc", label: "Tên A → Z" },
] as const;

export default async function ProductsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const keyword = param(params, "keyword")?.slice(0, 100);
  const categoryId = positiveInt(param(params, "categoryId"));
  const brand = param(params, "brand")?.slice(0, 100);
  const minPrice = nonNegativeNumber(param(params, "minPrice"));
  const maxPrice = nonNegativeNumber(param(params, "maxPrice"));
  const sort = oneOf(param(params, "sort"), SORTS.map((option) => option.value)) ?? "createdAt,desc";
  const page = pageIndex(param(params, "page"));

  const [categories, products] = await Promise.all([
    apiGet<Category[]>("/api/categories").catch(() => [] as Category[]),
    apiGet<PageResponse<ProductSummary>>(
      `/api/products${query({ keyword, categoryId, brand, minPrice, maxPrice, sort, page, size: PAGE_SIZE })}`,
    ).catch(() => null),
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
          {keyword ? `Kết quả cho “${keyword}”` : category ? category.name : "Tất cả sản phẩm"}
        </h1>
        {products && <p className="mt-1 text-sm text-slate-500">{products.totalElements} sản phẩm</p>}
      </div>

      <div className="grid gap-8 lg:grid-cols-[240px_1fr]">
        <aside className="flex flex-col gap-6">
          <nav aria-label="Lọc theo danh mục" className="card p-4">
            <h2 className="mb-2 text-sm font-semibold text-slate-900">Danh mục</h2>
            <ul className="flex flex-col gap-1 text-sm">
              <li>
                <Link
                  href={`/products${query({ ...current, categoryId: undefined })}`}
                  className={!categoryId ? "font-medium text-brand-600" : "text-slate-600 hover:text-brand-600"}
                >
                  Tất cả
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
            <h2 className="text-sm font-semibold text-slate-900">Bộ lọc</h2>
            {keyword && <input type="hidden" name="keyword" value={keyword} />}
            {categoryId && <input type="hidden" name="categoryId" value={categoryId} />}
            <div>
              <label htmlFor="brand" className="label">
                Thương hiệu
              </label>
              <input id="brand" name="brand" defaultValue={brand} maxLength={100} className="input" placeholder="Apple, Samsung..." />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div>
                <label htmlFor="minPrice" className="label">
                  Giá từ
                </label>
                <input id="minPrice" name="minPrice" type="number" min={0} step={1000} defaultValue={minPrice} className="input" />
              </div>
              <div>
                <label htmlFor="maxPrice" className="label">
                  đến
                </label>
                <input id="maxPrice" name="maxPrice" type="number" min={0} step={1000} defaultValue={maxPrice} className="input" />
              </div>
            </div>
            <div>
              <label htmlFor="sort" className="label">
                Sắp xếp
              </label>
              <select id="sort" name="sort" defaultValue={sort} className="input">
                {SORTS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </div>
            <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
              Áp dụng
            </button>
            {(brand || minPrice !== undefined || maxPrice !== undefined) && (
              <Link href={`/products${query({ keyword, categoryId, sort })}`} className="link text-center text-sm">
                Xoá bộ lọc
              </Link>
            )}
          </form>
        </aside>

        <section aria-label="Danh sách sản phẩm">
          {!products ? (
            <Alert tone="warning">Không tải được danh sách sản phẩm. Vui lòng thử lại sau.</Alert>
          ) : products.content.length === 0 ? (
            <EmptyState title="Không có sản phẩm phù hợp">
              <Link href="/products" className="link text-sm">
                Xem tất cả sản phẩm
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
