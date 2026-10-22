import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { CategoryManager } from "@/components/admin/CategoryManager";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Category } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminCategories"))("title") };
}

export default async function AdminCategoriesPage() {
  const [categories, t] = await Promise.all([apiGet<Category[]>("/api/categories"), getTranslations("adminCategories")]);
  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <CategoryManager categories={categories} />
    </>
  );
}
