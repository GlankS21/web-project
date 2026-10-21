import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ProductForm } from "@/components/admin/ProductForm";
import { PageTitle } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { positiveInt } from "@/lib/search-params";
import type { Category, Product } from "@/lib/types";

export const metadata: Metadata = { title: "Sửa sản phẩm" };

export default async function EditProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const productId = positiveInt((await params).productId);
  if (!productId) {
    notFound();
  }
  const [product, categories] = await Promise.all([
    apiGetOrNotFound<Product>(`/api/products/${productId}`, { auth: true }),
    apiGet<Category[]>("/api/categories"),
  ]);

  return (
    <>
      <Link href="/admin/products" className="link text-sm">
        ← Sản phẩm
      </Link>
      <div className="mt-3">
        <PageTitle
          actions={
            <p className="text-sm text-slate-500">
              Cập nhật {formatDateTime(product.updatedAt)} ·{" "}
              {product.status === "ACTIVE" ? (
                <Link href={`/products/${product.slug}`} className="link">
                  Xem trên cửa hàng
                </Link>
              ) : (
                "chưa hiển thị trên cửa hàng"
              )}
            </p>
          }
        >
          {product.name}
        </PageTitle>
      </div>
      <ProductForm categories={categories} product={product} />
    </>
  );
}
