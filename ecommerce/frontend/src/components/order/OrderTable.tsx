import Link from "next/link";
import { useTranslations } from "next-intl";
import { StatusBadge } from "@/components/ui";
import { formatDateTime, formatMoney, labelled, ORDER_STATUS_TONE } from "@/lib/format";
import type { OrderSummary } from "@/lib/types";

/** Orders as an admin table, each linking to its admin detail page. */
export function OrderTable({ orders }: { orders: OrderSummary[] }) {
  const t = useTranslations("adminOrders");
  const tStatus = useTranslations("orderStatus");
  const orderStatus = labelled(ORDER_STATUS_TONE, (value) => tStatus(value));
  if (orders.length === 0) {
    return <p className="p-4 text-sm text-slate-500">{t("empty")}</p>;
  }
  return (
    <table className="table">
      <thead>
        <tr>
          <th>{t("number")}</th>
          <th>{t("customer")}</th>
          <th>{t("status")}</th>
          <th className="text-right">{t("total")}</th>
          <th>{t("createdAt")}</th>
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
              <StatusBadge value={order.status} labels={orderStatus} />
            </td>
            <td className="text-right whitespace-nowrap">{formatMoney(order.totalAmount, order.currency)}</td>
            <td className="whitespace-nowrap text-slate-500">{formatDateTime(order.createdAt)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
