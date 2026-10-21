import Link from "next/link";
import { ProductCard } from "@/components/ProductCard";
import { Alert, ButtonLink } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Category, PageResponse, ProductSummary } from "@/lib/types";

export default async function HomePage() {
  const [categories, latest] = await Promise.all([
    apiGet<Category[]>("/api/categories").catch(() => null),
    apiGet<PageResponse<ProductSummary>>("/api/products?size=8&sort=createdAt,desc").catch(() => null),
  ]);

  return (
    <>
      <section className="bg-gradient-to-br from-brand-700 via-brand-600 to-sky-500 text-white">
        <div className="container-page flex flex-col gap-6 py-14 sm:py-20">
          <p className="text-sm font-medium tracking-widest text-sky-100 uppercase">Công nghệ chính hãng</p>
          <h1 className="max-w-2xl text-3xl leading-tight font-bold sm:text-5xl">
            Laptop, điện thoại và phụ kiện cho công việc lẫn giải trí
          </h1>
          <p className="max-w-xl text-sky-50">
            Giá cập nhật theo thời gian thực, giữ hàng ngay khi đặt, thanh toán khi nhận hàng hoặc qua thẻ và ví điện tử.
          </p>
          <div className="flex flex-wrap gap-3">
            <ButtonLink href="/products" size="lg" variant="secondary" className="border-transparent">
              Mua sắm ngay
            </ButtonLink>
            <ButtonLink href="/products?sort=price,asc" size="lg" variant="ghost" className="text-white hover:bg-white/10">
              Giá tốt nhất
            </ButtonLink>
          </div>
        </div>
      </section>

      <div className="container-page flex flex-col gap-12 py-10">
        {categories && categories.length > 0 && (
          <section aria-labelledby="categories-title">
            <h2 id="categories-title" className="mb-4 text-xl font-semibold text-slate-900">
              Danh mục
            </h2>
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
              {categories.map((category) => (
                <div key={category.id} className="card p-5">
                  <Link href={`/products?categoryId=${category.id}`} className="font-semibold text-slate-900 hover:text-brand-600">
                    {category.name}
                  </Link>
                  {category.children.length > 0 && (
                    <ul className="mt-3 flex flex-col gap-1.5 text-sm">
                      {category.children.map((child) => (
                        <li key={child.id}>
                          <Link href={`/products?categoryId=${child.id}`} className="text-slate-500 hover:text-brand-600">
                            {child.name}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

        <section aria-labelledby="latest-title">
          <div className="mb-4 flex items-center justify-between">
            <h2 id="latest-title" className="text-xl font-semibold text-slate-900">
              Sản phẩm mới
            </h2>
            <Link href="/products?sort=createdAt,desc" className="link text-sm">
              Xem tất cả →
            </Link>
          </div>
          {latest ? (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {latest.content.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          ) : (
            <Alert tone="warning">Không tải được danh sách sản phẩm. Vui lòng thử lại sau.</Alert>
          )}
        </section>
      </div>
    </>
  );
}
