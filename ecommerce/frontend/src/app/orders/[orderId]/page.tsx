import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { CancelOrder, OrderAutoRefresh } from "@/components/order/OrderActions";
import { OrderDetail } from "@/components/order/OrderDetail";
import { Alert, ButtonLink, StatusBadge } from "@/components/ui";
import { formatDateTime, labelled, ORDER_STATUS_TONE } from "@/lib/format";
import { awaitsOnlinePayment, isCancellable, isInProgress, isSettling } from "@/lib/orders";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { isUuid, param, type SearchParams } from "@/lib/search-params";
import type { Order, Payment } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("order");
  return { title: t("metaTitle") };
}

interface Props {
  params: Promise<{ orderId: string }>;
  searchParams: Promise<SearchParams>;
}

export default async function OrderPage({ params, searchParams }: Props) {
  const { orderId } = await params;
  if (!isUuid(orderId)) {
    notFound();
  }
  const justPlaced = param(await searchParams, "placed") === "1";
  const [order, payment] = await Promise.all([
    apiGetOrNotFound<Order>(`/api/orders/${orderId}`, { auth: true }),
    // No payment yet while stock is being reserved
    apiGet<Payment>(`/api/payments/orders/${orderId}`, { auth: true }).catch(() => null),
  ]);
  const t = await getTranslations("order");
  const tOrderStatus = await getTranslations("orderStatus");
  const toPay = awaitsOnlinePayment(order);

  return (
    <div className="container-page py-8">
      <OrderAutoRefresh active={isSettling(order, payment)} />
      <Link href="/orders" className="link text-sm">
        {t("backToOrders")}
      </Link>
      <div className="mt-3 mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">{t("title", { number: order.orderNumber })}</h1>
            <StatusBadge value={order.status} labels={labelled(ORDER_STATUS_TONE, (value) => tOrderStatus(value))} />
          </div>
          <p className="mt-1 text-sm text-slate-500">{t("placedAt", { date: formatDateTime(order.createdAt) })}</p>
        </div>
        <div className="flex flex-wrap items-start gap-2">
          {toPay && <ButtonLink href={`/orders/${order.id}/payment`}>{t("pay")}</ButtonLink>}
          {isCancellable(order.status) && <CancelOrder orderId={order.id} paid={order.status === "PAID"} />}
        </div>
      </div>

      <div className="mb-6 flex flex-col gap-3">
        {justPlaced && isInProgress(order.status) && !toPay && <Alert tone="info">{t("placedNotice")}</Alert>}
        {toPay && <Alert tone="warning">{t("awaitingOnlinePayment")}</Alert>}
        {order.status === "PAID" && justPlaced && <Alert tone="success">{t("paidNotice")}</Alert>}
        {order.status === "CANCELLED" && (
          <Alert tone="danger">
            {order.cancellationReason
              ? t("cancelledWithReason", { reason: order.cancellationReason })
              : t("cancelledNotice")}
            {payment?.status === "REFUNDED" && ` ${t("refundedNotice")}`}
            {payment?.status === "REFUND_PENDING" && ` ${t("refundPendingNotice")}`}
            {payment?.status === "REFUND_FAILED" && ` ${t("refundFailedNotice")}`}
          </Alert>
        )}
      </div>

      <OrderDetail order={order} payment={payment} />
    </div>
  );
}
