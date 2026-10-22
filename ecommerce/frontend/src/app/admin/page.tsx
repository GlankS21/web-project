import type { Metadata } from "next";
import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { SalesStats, STATS_PERIODS, type StatsPeriod } from "@/components/admin/SalesStats";
import { OrderTable } from "@/components/order/OrderTable";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { OrderSummary, PageResponse } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminDashboard"))("title") };
}

/** Number of elements matching a list query, or null when the service did not answer. */
async function count(path: string): Promise<number | null> {
  return apiGet<PageResponse<unknown>>(path, { auth: true })
    .then((page) => page.totalElements)
    .catch(() => null);
}

export default async function AdminDashboard({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const requested = Number(param(await searchParams, "days"));
  const days: StatsPeriod = STATS_PERIODS.find((period) => period === requested) ?? 30;
  const [toShip, awaitingPayment, lowStock, failedMails, recent, t] = await Promise.all([
    count("/api/orders/admin?status=PAID&size=1"),
    count("/api/orders/admin?status=AWAITING_PAYMENT&size=1"),
    count("/api/inventory?lowStock=true&size=1"),
    count("/api/notifications/admin?status=FAILED&size=1"),
    apiGet<PageResponse<OrderSummary>>("/api/orders/admin?size=8&sort=createdAt,desc", { auth: true }).catch(() => null),
    getTranslations("adminDashboard"),
  ]);

  const tiles = [
    { label: t("toShip"), value: toShip, href: "/admin/orders?status=PAID", tone: "text-emerald-700" },
    { label: t("awaitingPayment"), value: awaitingPayment, href: "/admin/orders?status=AWAITING_PAYMENT", tone: "text-amber-700" },
    { label: t("lowStock"), value: lowStock, href: "/admin/inventory?lowStock=true", tone: "text-rose-700" },
    { label: t("failedMails"), value: failedMails, href: "/admin/notifications?status=FAILED", tone: "text-rose-700" },
  ];

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        {tiles.map((tile) => (
          <Link key={tile.label} href={tile.href} className="card p-5 transition-shadow hover:shadow-md">
            <p className="text-sm text-slate-500">{tile.label}</p>
            <p className={`mt-2 text-3xl font-semibold ${tile.tone}`}>{tile.value ?? "—"}</p>
          </Link>
        ))}
      </div>

      <SalesStats days={days} />

      <section className="card mt-8 overflow-x-auto" aria-labelledby="recent-title">
        <div className="flex items-center justify-between p-4">
          <h2 id="recent-title" className="font-semibold text-slate-900">
            {t("recentOrders")}
          </h2>
          <Link href="/admin/orders" className="link text-sm">
            {t("allOrders")}
          </Link>
        </div>
        {recent ? <OrderTable orders={recent.content} /> : <p className="p-4 text-sm text-slate-500">{t("ordersUnavailable")}</p>}
      </section>
    </>
  );
}
