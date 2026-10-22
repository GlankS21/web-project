import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ProductForm } from "@/components/admin/ProductForm";
import { PageTitle } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { positiveInt } from "@/lib/search-params";
import type { Category, Product } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminProducts"))("editMetaTitle") };
}

export default async function EditProductPage({ params }: { params: Promise<{ productId: string }> }) {
  const productId = positiveInt((await params).productId);
  if (!productId) {
    notFound();
  }
  const [product, categories, t] = await Promise.all([
    apiGetOrNotFound<Product>(`/api/products/${productId}`, { auth: true }),
    apiGet<Category[]>("/api/categories"),
    getTranslations("adminProducts"),
  ]);

  return (
    <>
      <Link href="/admin/products" className="link text-sm">
        {t("back")}
      </Link>
      <div className="mt-3">
        <PageTitle
          actions={
            <p className="text-sm text-slate-500">
              {t("updatedAt", { date: formatDateTime(product.updatedAt) })} ·{" "}
              {product.status === "ACTIVE" ? (
                <Link href={`/products/${product.slug}`} className="link">
                  {t("viewInShop")}
                </Link>
              ) : (
                t("notInShop")
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
