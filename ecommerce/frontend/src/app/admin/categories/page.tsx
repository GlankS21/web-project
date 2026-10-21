import type { Metadata } from "next";
import { CategoryManager } from "@/components/admin/CategoryManager";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Category } from "@/lib/types";

export const metadata: Metadata = { title: "Danh mục" };

export default async function AdminCategoriesPage() {
  const categories = await apiGet<Category[]>("/api/categories");
  return (
    <>
      <PageTitle>Danh mục</PageTitle>
      <CategoryManager categories={categories} />
    </>
  );
}
