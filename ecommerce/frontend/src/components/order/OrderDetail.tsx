import { formatAddress } from "@/components/address/AddressFields";
import { StatusBadge, Thumb } from "@/components/ui";
import { formatDateTime, formatMoney, ORDER_STATUS, PAYMENT_METHOD, PAYMENT_STATUS } from "@/lib/format";
import type { Order, Payment } from "@/lib/types";

/** Order content shared by the customer and admin pages. */
export function OrderDetail({ order, payment }: { order: Order; payment: Payment | null }) {
  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="flex flex-col gap-6">
        <section className="card p-5" aria-labelledby="items-title">
          <h2 id="items-title" className="mb-4 font-semibold text-slate-900">
            Sản phẩm
          </h2>
          <ul className="flex flex-col divide-y divide-slate-100">
            {order.items.map((item) => (
              <li key={item.productId} className="flex items-center gap-4 py-3">
                <Thumb src={item.thumbnailUrl} alt={item.productName} />
                <div className="min-w-0 flex-1">
                  <p className="font-medium text-slate-900">{item.productName}</p>
                  <p className="text-sm text-slate-500">
                    {formatMoney(item.unitPrice, order.currency)} × {item.quantity}
                  </p>
                </div>
                <p className="font-medium whitespace-nowrap">{formatMoney(item.lineTotal, order.currency)}</p>
              </li>
            ))}
          </ul>
          <dl className="mt-2 flex flex-col gap-2 border-t border-slate-100 pt-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-600">Tạm tính</dt>
              <dd>{formatMoney(order.subtotal, order.currency)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-600">Phí vận chuyển</dt>
              <dd>{order.shippingFee > 0 ? formatMoney(order.shippingFee, order.currency) : "Miễn phí"}</dd>
            </div>
            <div className="flex justify-between text-base font-semibold">
              <dt>Tổng cộng</dt>
              <dd className="text-accent-500">{formatMoney(order.totalAmount, order.currency)}</dd>
            </div>
          </dl>
        </section>

        <section className="card p-5" aria-labelledby="timeline-title">
          <h2 id="timeline-title" className="mb-4 font-semibold text-slate-900">
            Lịch sử trạng thái
          </h2>
          <ol className="relative ml-2 flex flex-col gap-5 border-l border-slate-200 pl-6">
            {order.statusHistory.map((entry, index) => (
              <li key={index} className="relative">
                <span
                  className={`absolute top-1 -left-[31px] h-3 w-3 rounded-full ring-4 ring-white ${
                    index === order.statusHistory.length - 1 ? "bg-brand-500" : "bg-slate-300"
                  }`}
                />
                <div className="flex flex-wrap items-center gap-2">
                  <StatusBadge value={entry.toStatus} labels={ORDER_STATUS} />
                  <time className="text-xs text-slate-500">{formatDateTime(entry.createdAt)}</time>
                </div>
                {entry.reason && <p className="mt-1 text-sm text-slate-600">{entry.reason}</p>}
              </li>
            ))}
          </ol>
        </section>
      </div>

      <aside className="flex flex-col gap-6">
        <section className="card p-5 text-sm" aria-labelledby="shipping-title">
          <h2 id="shipping-title" className="mb-3 font-semibold text-slate-900">
            Giao hàng
          </h2>
          <p className="font-medium text-slate-900">{order.shippingAddress.recipientName}</p>
          <p className="text-slate-600">{order.shippingAddress.phoneNumber}</p>
          <p className="mt-1 text-slate-600">{formatAddress(order.shippingAddress)}</p>
          {order.note && <p className="mt-3 rounded-lg bg-slate-50 p-3 text-slate-600">Ghi chú: {order.note}</p>}
        </section>

        <section className="card p-5 text-sm" aria-labelledby="payment-title">
          <h2 id="payment-title" className="mb-3 font-semibold text-slate-900">
            Thanh toán
          </h2>
          <p className="text-slate-700">{PAYMENT_METHOD[order.paymentMethod]}</p>
          {payment ? (
            <dl className="mt-3 flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">Trạng thái</dt>
                <dd>
                  <StatusBadge value={payment.status} labels={PAYMENT_STATUS} />
                </dd>
              </div>
              {payment.transactionId && (
                <div className="flex justify-between gap-2">
                  <dt className="text-slate-500">Mã giao dịch</dt>
                  <dd className="font-mono text-xs break-all">{payment.transactionId}</dd>
                </div>
              )}
              {payment.refundTransactionId && (
                <div className="flex justify-between gap-2">
                  <dt className="text-slate-500">Mã hoàn tiền</dt>
                  <dd className="font-mono text-xs break-all">{payment.refundTransactionId}</dd>
                </div>
              )}
              {payment.failureReason && payment.status === "FAILED" && (
                <p className="mt-1 text-rose-700">{payment.failureReason}</p>
              )}
            </dl>
          ) : (
            <p className="mt-2 text-slate-500">
              {order.status === "PENDING" || order.status === "AWAITING_PAYMENT" ? "Đang chờ xử lý thanh toán…" : "Chưa có giao dịch."}
            </p>
          )}
        </section>
      </aside>
    </div>
  );
}
