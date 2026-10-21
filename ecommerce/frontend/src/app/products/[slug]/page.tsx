import type { Metadata } from "next";
import Link from "next/link";
import { cache } from "react";
import { Price } from "@/components/Price";
import { AddToCart } from "@/components/product/AddToCart";
import { ImageGallery } from "@/components/product/ImageGallery";
import { Badge } from "@/components/ui";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import type { Product, StockAvailability } from "@/lib/types";

interface Props {
  params: Promise<{ slug: string }>;
}

/** Shared by generateMetadata and the page within one request. */
const loadProduct = cache((slug: string) => apiGetOrNotFound<Product>(`/api/products/slug/${encodeURIComponent(slug)}`));

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const product = await loadProduct((await params).slug);
  return { title: product.name, description: product.shortDescription ?? undefined };
}

export default async function ProductPage({ params }: Props) {
  const product = await loadProduct((await params).slug);
  // Unknown stock (inventory-service down) still lets the customer try; the cart re-checks
  const availability = await apiGet<StockAvailability[]>(`/api/inventory/availability?productIds=${product.id}`)
    .then((items) => items[0]?.availableQuantity ?? 0)
    .catch(() => null);

  return (
    <div className="container-page py-8">
      <nav aria-label="Breadcrumb" className="mb-6 text-sm text-slate-500">
        <Link href="/products" className="hover:text-brand-600">
          Sản phẩm
        </Link>
        <span className="mx-2">/</span>
        <Link href={`/products?categoryId=${product.category.id}`} className="hover:text-brand-600">
          {product.category.name}
        </Link>
      </nav>

      <div className="grid gap-10 lg:grid-cols-2">
        <ImageGallery images={product.images} name={product.name} />

        <div className="flex flex-col gap-5">
          <div>
            {product.brand && <p className="text-sm font-medium tracking-wide text-slate-500 uppercase">{product.brand}</p>}
            <h1 className="mt-1 text-2xl font-semibold text-slate-900 sm:text-3xl">{product.name}</h1>
            <p className="mt-2 text-sm text-slate-500">SKU: {product.sku}</p>
          </div>

          <Price price={product.price} compareAtPrice={product.compareAtPrice} currency={product.currency} size="lg" />

          <div>
            {availability === null ? (
              <Badge>Chưa rõ tồn kho</Badge>
            ) : availability > 0 ? (
              <Badge tone="success">{availability <= 5 ? `Chỉ còn ${availability} sản phẩm` : "Còn hàng"}</Badge>
            ) : (
              <Badge tone="danger">Hết hàng</Badge>
            )}
          </div>

          {product.shortDescription && <p className="text-slate-700">{product.shortDescription}</p>}

          <AddToCart productId={product.id} available={availability} />

          <ul className="grid gap-2 rounded-xl bg-white p-4 text-sm text-slate-600 ring-1 ring-slate-200">
            <li>✓ Hàng được giữ ngay khi đặt, đơn chưa thanh toán quá hạn sẽ tự huỷ</li>
            <li>✓ Thanh toán khi nhận hàng, thẻ ngân hàng hoặc ví điện tử</li>
            <li>✓ Hoàn tiền tự động khi đơn bị huỷ sau thanh toán</li>
          </ul>
        </div>
      </div>

      {product.description && (
        <section className="card mt-10 p-6" aria-labelledby="description-title">
          <h2 id="description-title" className="mb-3 text-lg font-semibold text-slate-900">
            Mô tả sản phẩm
          </h2>
          <p className="leading-relaxed whitespace-pre-line text-slate-700">{product.description}</p>
        </section>
      )}
    </div>
  );
}
