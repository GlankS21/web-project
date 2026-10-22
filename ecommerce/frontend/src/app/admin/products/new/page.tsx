import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import Link from "next/link";
import { ProductForm } from "@/components/admin/ProductForm";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Category } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminProducts"))("new") };
}

export default async function NewProductPage() {
  const [categories, t] = await Promise.all([apiGet<Category[]>("/api/categories"), getTranslations("adminProducts")]);
  return (
    <>
      <Link href="/admin/products" className="link text-sm">
        {t("back")}
      </Link>
      <div className="mt-3">
        <PageTitle>{t("new")}</PageTitle>
      </div>
      <ProductForm categories={categories} />
    </>
  );
}
