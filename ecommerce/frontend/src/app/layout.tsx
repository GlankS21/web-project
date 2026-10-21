import type { Metadata } from "next";
import type { ReactNode } from "react";
import { SiteHeader } from "@/components/header/SiteHeader";
import { Providers } from "@/components/providers";
import { apiGet, getSession } from "@/lib/server/data";
import type { Category } from "@/lib/types";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "TechShop — Laptop, điện thoại, phụ kiện", template: "%s | TechShop" },
  description: "Cửa hàng thiết bị công nghệ: laptop, điện thoại, máy tính bảng và phụ kiện chính hãng.",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  const [session, categories] = await Promise.all([
    getSession(),
    // The shop stays usable without the category bar if product-service is down
    apiGet<Category[]>("/api/categories").catch(() => [] as Category[]),
  ]);

  return (
    <html lang="vi">
      <body className="flex min-h-screen flex-col">
        <Providers session={session}>
          <SiteHeader categories={categories} />
          <main className="flex-1">{children}</main>
          <footer className="mt-16 border-t border-slate-200 bg-white">
            <div className="container-page flex flex-col gap-2 py-8 text-sm text-slate-500 sm:flex-row sm:justify-between">
              <p>© TechShop — đồ án kiến trúc microservice.</p>
              <p>Giao hàng toàn quốc · Thanh toán khi nhận hàng, thẻ hoặc ví điện tử</p>
            </div>
          </footer>
        </Providers>
      </body>
    </html>
  );
}
