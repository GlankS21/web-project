import type { Metadata } from "next";
import type { ReactNode } from "react";
import { NextIntlClientProvider } from "next-intl";
import { getTranslations } from "next-intl/server";
import { SiteHeader } from "@/components/header/SiteHeader";
import { Providers } from "@/components/providers";
import { apiGet, getSession } from "@/lib/server/data";
import type { Category } from "@/lib/types";
import { DEFAULT_LOCALE } from "@/i18n/config";
import "./globals.css";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("layout");
  return {
    title: { default: t("metaTitle"), template: "%s | TechShop" },
    description: t("metaDescription"),
    openGraph: {
      type: "website",
      siteName: "TechShop",
      locale: "ru_RU",
      title: t("metaTitle"),
      description: t("metaDescription"),
    },
  };
}

export default async function RootLayout({ children }: { children: ReactNode }) {
  const [session, categories, t] = await Promise.all([
    getSession(),
    // The shop stays usable without the category bar if product-service is down
    apiGet<Category[]>("/api/categories").catch(() => [] as Category[]),
    getTranslations("layout"),
  ]);

  return (
    <html lang={DEFAULT_LOCALE}>
      <body className="flex min-h-screen flex-col">
        <NextIntlClientProvider>
          <Providers session={session}>
            <SiteHeader categories={categories} />
            <main className="flex-1">{children}</main>
            <footer className="mt-16 border-t border-slate-200 bg-white">
              <div className="container-page flex flex-col gap-2 py-8 text-sm text-slate-500 sm:flex-row sm:justify-between">
                <p>{t("copyright")}</p>
                <p>{t("footerInfo")}</p>
              </div>
            </footer>
          </Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
