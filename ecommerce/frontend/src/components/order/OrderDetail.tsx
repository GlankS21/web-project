import { useTranslations } from "next-intl";
import { formatAddress } from "@/components/address/AddressFields";
import { StatusBadge, Thumb } from "@/components/ui";
import {
  formatDateTime,
  formatMoney,
  labelled,
  ORDER_STATUS_TONE,
  PAYMENT_STATUS_TONE,
  TRANSACTION_STATUS_TONE,
} from "@/lib/format";
import { isInProgress } from "@/lib/orders";
import { formatPhone } from "@/lib/phone";
import { isKnownFailure } from "@/lib/payment-failure";
import type { Order, Payment } from "@/lib/types";

/** Order content shared by the customer and admin pages. */
export function OrderDetail({ order, payment }: { order: Order; payment: Payment | null }) {
  const t = useTranslations("order");
  const tCommon = useTranslations("common");
  const tOrderStatus = useTranslations("orderStatus");
  const orderStatus = labelled(ORDER_STATUS_TONE, (value) => tOrderStatus(value));

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
      <div className="flex min-w-0 flex-col gap-6">
        <section className="card p-5" aria-labelledby="items-title">
          <h2 id="items-title" className="mb-4 font-semibold text-slate-900">
            {t("items")}
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
              <dt className="text-slate-600">{tCommon("subtotal")}</dt>
              <dd>{formatMoney(order.subtotal, order.currency)}</dd>
            </div>
            {order.discountAmount > 0 && (
              <div className="flex justify-between text-emerald-700">
                <dt>{t("discount", { code: order.couponCode ?? "" })}</dt>
                <dd>−{formatMoney(order.discountAmount, order.currency)}</dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-slate-600">{tCommon("shippingFee")}</dt>
              <dd>{order.shippingFee > 0 ? formatMoney(order.shippingFee, order.currency) : tCommon("free")}</dd>
            </div>
            <div className="flex justify-between text-base font-semibold">
              <dt>{tCommon("total")}</dt>
              <dd className="text-accent-500">{formatMoney(order.totalAmount, order.currency)}</dd>
            </div>
          </dl>
        </section>

        <section className="card p-5" aria-labelledby="timeline-title">
          <h2 id="timeline-title" className="mb-4 font-semibold text-slate-900">
            {t("history")}
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
                  <StatusBadge value={entry.toStatus} labels={orderStatus} />
                  <time className="text-xs text-slate-500">{formatDateTime(entry.createdAt)}</time>
                </div>
                {entry.reason && <p className="mt-1 text-sm break-words text-slate-600">{entry.reason}</p>}
              </li>
            ))}
          </ol>
        </section>
      </div>

      <aside className="flex min-w-0 flex-col gap-6">
        <section className="card p-5 text-sm" aria-labelledby="shipping-title">
          <h2 id="shipping-title" className="mb-3 font-semibold text-slate-900">
            {t("delivery")}
          </h2>
          <p className="font-medium text-slate-900">{order.shippingAddress.recipientName}</p>
          <p className="text-slate-600">{formatPhone(order.shippingAddress.phoneNumber)}</p>
          <p className="mt-1 text-slate-600">{formatAddress(order.shippingAddress)}</p>
          {order.note && <p className="mt-3 rounded-lg bg-slate-50 p-3 text-slate-600">{t("note", { note: order.note })}</p>}
        </section>

        <PaymentSummary order={order} payment={payment} />
      </aside>
    </div>
  );
}

function PaymentSummary({ order, payment }: { order: Order; payment: Payment | null }) {
  const t = useTranslations("order");
  const tMethod = useTranslations("paymentMethod");
  const tPaymentStatus = useTranslations("paymentStatus");
  const tTransactionStatus = useTranslations("transactionStatus");
  const tTransactionType = useTranslations("transactionType");
  const tFailure = useTranslations("paymentFailure");
  const paymentStatus = labelled(PAYMENT_STATUS_TONE, (value) => tPaymentStatus(value));
  const transactionStatus = labelled(TRANSACTION_STATUS_TONE, (value) => tTransactionStatus(value));
  const failure = (code: string | null) => (isKnownFailure(code) ? tFailure(code) : code);
  // Charges are numbered as attempts: "Оплата · Попытка 2"
  const attempts = new Map(
    (payment?.transactions ?? [])
      .filter((transaction) => transaction.type === "CHARGE")
      .map((transaction, index) => [transaction.id, index + 1]),
  );

  return (
    <section className="card p-5 text-sm" aria-labelledby="payment-title">
      <h2 id="payment-title" className="mb-3 font-semibold text-slate-900">
        {t("payment")}
      </h2>
      <p className="text-slate-700">{tMethod(order.paymentMethod)}</p>
      {payment ? (
        <>
          <dl className="mt-3 flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <dt className="text-slate-500">{t("paymentStatus")}</dt>
              <dd>
                <StatusBadge value={payment.status} labels={paymentStatus} />
              </dd>
            </div>
            {payment.transactionId && (
              <div className="flex justify-between gap-2">
                <dt className="text-slate-500">{t("transactionId")}</dt>
                <dd className="font-mono text-xs break-all">{payment.transactionId}</dd>
              </div>
            )}
            {payment.paidAt && (
              <div className="flex justify-between gap-2">
                <dt className="text-slate-500">{t("paidAt")}</dt>
                <dd>{formatDateTime(payment.paidAt)}</dd>
              </div>
            )}
            {payment.refundTransactionId && (
              <div className="flex justify-between gap-2">
                <dt className="text-slate-500">{t("refundId")}</dt>
                <dd className="font-mono text-xs break-all">{payment.refundTransactionId}</dd>
              </div>
            )}
            {payment.refundedAt && (
              <div className="flex justify-between gap-2">
                <dt className="text-slate-500">{t("refundedAt")}</dt>
                <dd>{formatDateTime(payment.refundedAt)}</dd>
              </div>
            )}
          </dl>
          {payment.failureReason && (payment.status === "FAILED" || payment.status === "REFUND_FAILED") && (
            <p className="mt-2 text-rose-700">{failure(payment.failureReason)}</p>
          )}
          {payment.transactions.length > 0 && payment.provider !== "COD" && (
            <div className="mt-4 border-t border-slate-100 pt-3">
              <h3 className="mb-2 text-xs font-semibold tracking-wide text-slate-500 uppercase">{t("transactions")}</h3>
              <ul className="flex flex-col gap-2">
                {payment.transactions.map((transaction) => (
                  <li key={transaction.id} className="rounded-lg bg-slate-50 p-2.5">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-medium text-slate-800">
                        {transaction.type === "CHARGE"
                          ? `${tTransactionType("CHARGE")} · ${t("attempt", { number: attempts.get(transaction.id) ?? 1 })}`
                          : tTransactionType("REFUND")}
                      </span>
                      <StatusBadge value={transaction.status} labels={transactionStatus} />
                    </div>
                    <div className="mt-1 flex flex-wrap justify-between gap-2 text-xs text-slate-500">
                      <span>{formatDateTime(transaction.createdAt)}</span>
                      <span>
                        {transaction.type === "REFUND" ? "−" : ""}
                        {formatMoney(transaction.amount, transaction.currency)}
                      </span>
                    </div>
                    {transaction.status === "FAILED" && transaction.failureReason && (
                      <p className="mt-1 text-xs text-rose-700">{failure(transaction.failureReason)}</p>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      ) : (
        <p className="mt-2 text-slate-500">{isInProgress(order.status) ? t("waitingForPayment") : t("noPayment")}</p>
      )}
    </section>
  );
}
