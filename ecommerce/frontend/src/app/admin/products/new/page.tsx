import type { Metadata } from "next";
import Link from "next/link";
import { ProductForm } from "@/components/admin/ProductForm";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Category } from "@/lib/types";

export const metadata: Metadata = { title: "Thêm sản phẩm" };

export default async function NewProductPage() {
  const categories = await apiGet<Category[]>("/api/categories");
  return (
    <>
      <Link href="/admin/products" className="link text-sm">
        ← Sản phẩm
      </Link>
      <div className="mt-3">
        <PageTitle>Thêm sản phẩm</PageTitle>
      </div>
      <ProductForm categories={categories} />
    </>
  );
}
