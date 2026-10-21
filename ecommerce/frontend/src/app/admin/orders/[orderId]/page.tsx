import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { AdvanceOrderStatus } from "@/components/admin/AdvanceOrderStatus";
import { CancelOrder, OrderAutoRefresh } from "@/components/order/OrderActions";
import { OrderDetail } from "@/components/order/OrderDetail";
import { StatusBadge } from "@/components/ui";
import { formatDateTime, ORDER_STATUS } from "@/lib/format";
import { apiGet, apiGetOrNotFound } from "@/lib/server/data";
import { isUuid } from "@/lib/search-params";
import type { Order, Payment } from "@/lib/types";

export const metadata: Metadata = { title: "Chi tiết đơn hàng" };

export default async function AdminOrderPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  if (!isUuid(orderId)) {
    notFound();
  }
  const [order, payment] = await Promise.all([
    apiGetOrNotFound<Order>(`/api/orders/${orderId}`, { auth: true }),
    apiGet<Payment>(`/api/payments/orders/${orderId}`, { auth: true }).catch(() => null),
  ]);
  const cancellable = order.status === "PENDING" || order.status === "AWAITING_PAYMENT";

  return (
    <>
      <OrderAutoRefresh status={order.status} />
      <Link href="/admin/orders" className="link text-sm">
        ← Đơn hàng
      </Link>
      <div className="mt-3 mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-2xl font-semibold text-slate-900">Đơn {order.orderNumber}</h1>
            <StatusBadge value={order.status} labels={ORDER_STATUS} />
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {order.userEmail} · {formatDateTime(order.createdAt)}
          </p>
          {order.cancellationReason && <p className="mt-1 text-sm text-rose-700">Lý do huỷ: {order.cancellationReason}</p>}
        </div>
        <div className="flex flex-col items-end gap-2">
          {order.status === "PAID" && <AdvanceOrderStatus orderId={order.id} target="SHIPPED" />}
          {order.status === "SHIPPED" && <AdvanceOrderStatus orderId={order.id} target="DELIVERED" />}
          {cancellable && <CancelOrder orderId={order.id} adminView />}
        </div>
      </div>
      <OrderDetail order={order} payment={payment} />
    </>
  );
}
