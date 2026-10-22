import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AdvanceOrderStatus } from "@/components/admin/AdvanceOrderStatus";
import { CancelOrder, OrderAutoRefresh } from "@/components/order/OrderActions";
import { OrderDetail } from "@/components/order/OrderDetail";
import { StatusBadge } from "@/components/ui";
import { getTranslations } from "next-intl/server";
import { formatDateTime, labelled, ORDER_STATUS_TONE } from "@/lib/format";
import { isCancellable, isSettling } from "@/lib/orders";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { isUuid } from "@/lib/search-params";
import type { Order, Payment } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("order");
  return { title: t("metaTitle") };
}

export default async function AdminOrderPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  if (!isUuid(orderId)) {
    notFound();
  }
  const [order, payment] = await Promise.all([
    apiGetOrNotFound<Order>(`/api/orders/${orderId}`, { auth: true }),
    apiGet<Payment>(`/api/payments/orders/${orderId}`, { auth: true }).catch(() => null),
  ]);
  const t = await getTranslations("order");
  const tOrderStatus = await getTranslations("orderStatus");

  return (
    <>
      <OrderAutoRefresh active={isSettling(order, payment)} />
      <Link href="/admin/orders" className="link text-sm">
        {t("backToAdminOrders")}
      </Link>
      <div className="mt-3 mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{t("title", { number: order.orderNumber })}</h1>
            <StatusBadge value={order.status} labels={labelled(ORDER_STATUS_TONE, (value) => tOrderStatus(value))} />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {t("customer", { email: order.userEmail, date: formatDateTime(order.createdAt) })}
          </p>
          {order.cancellationReason && (
            <p className="mt-1 text-sm text-rose-700">{t("cancelReasonFailure", { reason: order.cancellationReason })}</p>
          )}
        </div>
        <div className="flex flex-col items-end gap-2">
          {order.status === "PAID" && <AdvanceOrderStatus orderId={order.id} target="SHIPPED" />}
          {order.status === "SHIPPED" && <AdvanceOrderStatus orderId={order.id} target="DELIVERED" />}
          {isCancellable(order.status) && <CancelOrder orderId={order.id} adminView paid={order.status === "PAID"} />}
        </div>
      </div>
      <OrderDetail order={order} payment={payment} />
    </>
  );
}
