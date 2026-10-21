import Link from "next/link";
import { StatusBadge } from "@/components/ui";
import { formatDateTime, formatMoney, ORDER_STATUS } from "@/lib/format";
import type { OrderSummary } from "@/lib/types";

/** Orders as an admin table, each linking to its admin detail page. */
export function OrderTable({ orders }: { orders: OrderSummary[] }) {
  if (orders.length === 0) {
    return <p className="p-4 text-sm text-slate-500">Không có đơn hàng.</p>;
  }
  return (
    <table className="table">
      <thead>
        <tr>
          <th>Mã đơn</th>
          <th>Khách hàng</th>
          <th>Trạng thái</th>
          <th className="text-right">Tổng tiền</th>
          <th>Thời gian</th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.id} className="hover:bg-slate-50">
            <td>
              <Link href={`/admin/orders/${order.id}`} className="link font-medium">
                {order.orderNumber}
              </Link>
            </td>
            <td className="max-w-56 truncate">{order.userEmail}</td>
            <td>
              <StatusBadge value={order.status} labels={ORDER_STATUS} />
            </td>
            <td className="text-right whitespace-nowrap">{formatMoney(order.totalAmount, order.currency)}</td>
            <td className="whitespace-nowrap text-slate-500">{formatDateTime(order.createdAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
