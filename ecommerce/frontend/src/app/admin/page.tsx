import Link from "next/link";
import { OrderTable } from "@/components/order/OrderTable";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { OrderSummary, PageResponse } from "@/lib/types";

/** Number of elements matching a list query, or null when the service did not answer. */
async function count(path: string): Promise<number | null> {
  return apiGet<PageResponse<unknown>>(path, { auth: true })
    .then((page) => page.totalElements)
    .catch(() => null);
}

export default async function AdminDashboard() {
  const [toShip, awaitingPayment, lowStock, failedMails, recent] = await Promise.all([
    count("/api/orders/admin?status=PAID&size=1"),
    count("/api/orders/admin?status=AWAITING_PAYMENT&size=1"),
    count("/api/inventory?lowStock=true&size=1"),
    count("/api/notifications/admin?status=FAILED&size=1"),
    apiGet<PageResponse<OrderSummary>>("/api/orders/admin?size=8&sort=createdAt,desc", { auth: true }).catch(() => null),
  ]);

  const tiles = [
    { label: "Đơn chờ giao", value: toShip, href: "/admin/orders?status=PAID", tone: "text-emerald-700" },
    { label: "Đơn chờ thanh toán", value: awaitingPayment, href: "/admin/orders?status=AWAITING_PAYMENT", tone: "text-amber-700" },
    { label: "Sản phẩm sắp hết hàng", value: lowStock, href: "/admin/inventory?lowStock=true", tone: "text-rose-700" },
    { label: "E-mail gửi lỗi", value: failedMails, href: "/admin/notifications?status=FAILED", tone: "text-rose-700" },
  ];

  return (
    <>
      <PageTitle>Tổng quan</PageTitle>
      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">
        {tiles.map((tile) => (
          <Link key={tile.label} href={tile.href} className="card p-5 transition-shadow hover:shadow-md">
            <p className="text-sm text-slate-500">{tile.label}</p>
            <p className={`mt-2 text-3xl font-semibold ${tile.tone}`}>{tile.value ?? "—"}</p>
          </Link>
        ))}
      </div>

      <section className="card mt-8 overflow-x-auto" aria-labelledby="recent-title">
        <div className="flex items-center justify-between p-4">
          <h2 id="recent-title" className="font-semibold text-slate-900">
            Đơn hàng gần đây
          </h2>
          <Link href="/admin/orders" className="link text-sm">
            Tất cả →
          </Link>
        </div>
        {recent ? <OrderTable orders={recent.content} /> : <p className="p-4 text-sm text-slate-500">Không tải được đơn hàng.</p>}
      </section>
    </>
  );
}
