import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { CancelOrder, OrderAutoRefresh } from "@/components/order/OrderActions";
import { OrderDetail } from "@/components/order/OrderDetail";
import { Alert, StatusBadge } from "@/components/ui";
import { formatDateTime, ORDER_STATUS } from "@/lib/format";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { isUuid, param, type SearchParams } from "@/lib/search-params";
import type { Order, Payment } from "@/lib/types";

export const metadata: Metadata = { title: "Chi tiết đơn hàng" };

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
  const inProgress = order.status === "PENDING" || order.status === "AWAITING_PAYMENT";

  return (
    <div className="container-page py-8">
      <OrderAutoRefresh status={order.status} />
      <Link href="/orders" className="link text-sm">
        ← Đơn hàng của tôi
      </Link>
      <div className="mt-3 mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">Đơn {order.orderNumber}</h1>
            <StatusBadge value={order.status} labels={ORDER_STATUS} />
          </div>
          <p className="mt-1 text-sm text-slate-500">Đặt lúc {formatDateTime(order.createdAt)}</p>
        </div>
        {inProgress && <CancelOrder orderId={order.id} />}
      </div>

      <div className="mb-6 flex flex-col gap-3">
        {justPlaced && inProgress && (
          <Alert tone="info">Đã nhận đơn hàng. Hệ thống đang giữ hàng và xử lý thanh toán, trang sẽ tự cập nhật.</Alert>
        )}
        {order.status === "PAID" && justPlaced && (
          <Alert tone="success">Thanh toán thành công! Đơn hàng đã được xác nhận và sẽ sớm được giao.</Alert>
        )}
        {order.status === "CANCELLED" && (
          <Alert tone="danger">
            Đơn hàng đã bị huỷ{order.cancellationReason ? `: ${order.cancellationReason}` : "."}
            {payment?.status === "REFUNDED" && " Số tiền đã thanh toán đã được hoàn lại."}
          </Alert>
        )}
      </div>

      <OrderDetail order={order} payment={payment} />
    </div>
  );
}
