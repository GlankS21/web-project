"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, type FormEvent, type KeyboardEvent } from "react";
import { AddressFields, formatAddress, readAddress } from "@/components/address/AddressFields";
import { useCart } from "@/components/providers";
import { OrderPayment } from "@/components/payment/OrderPayment";
import { Alert, Button, Thumb } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { ApiError } from "@/lib/errors";
import { formatMoney, PAYMENT_METHODS } from "@/lib/format";
import { formatPhone, normalizePhone } from "@/lib/phone";
import type { Address, AddressFields as AddressValues, Cart, CouponPreview, Order, PaymentMethod } from "@/lib/types";

interface Props {
  cart: Cart;
  addresses: Address[];
  newAddressDefaults: Partial<AddressValues>;
}

const NEW_ADDRESS = "new";

export function CheckoutForm({ cart, addresses, newAddressDefaults }: Props) {
  const t = useTranslations("checkout");
  const tCommon = useTranslations("common");
  const tMethod = useTranslations("paymentMethod");
  const tCoupon = useTranslations("coupon");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const { refresh } = useCart();
  // One key per visit of the checkout page: a retried or double-submitted order is not placed twice
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [addressChoice, setAddressChoice] = useState<string>(
    addresses.find((address) => address.defaultAddress)?.id ?? addresses[0]?.id ?? NEW_ADDRESS,
  );
  const [method, setMethod] = useState<PaymentMethod>(PAYMENT_METHODS[0]);
  // An online order is paid right here, in the card form of the payment step
  const [placed, setPlaced] = useState<Order | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | string | null>(null);
  const [couponCode, setCouponCode] = useState("");
  // Totals with the coupon, priced by order-service; the order itself is priced again when placed
  const [coupon, setCoupon] = useState<CouponPreview | null>(null);
  const [couponBusy, setCouponBusy] = useState(false);
  const [couponError, setCouponError] = useState<string | null>(null);
  const items = cart.items.map((item) => ({ productId: item.productId, quantity: item.quantity }));

  const applyCoupon = async () => {
    const code = couponCode.trim().toUpperCase();
    if (!code) {
      setCouponError(tCoupon("required"));
      return;
    }
    setCouponBusy(true);
    setCouponError(null);
    try {
      setCoupon(await api.post<CouponPreview>("/orders/coupons/preview", { code, items }));
      setCouponCode(code);
    } catch (caught) {
      setCoupon(null);
      setCouponError(errorMessage(caught));
    } finally {
      setCouponBusy(false);
    }
  };

  const removeCoupon = () => {
    setCoupon(null);
    setCouponCode("");
    setCouponError(null);
  };

  // Enter in the coupon field applies the code instead of placing the order
  const couponKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter") {
      event.preventDefault();
      void applyCoupon();
    }
  };

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const saved = addresses.find((address) => address.id === addressChoice);
    const shippingAddress: AddressValues = saved ? pick(saved) : readAddress(form);

    setSubmitting(true);
    setError(null);
    try {
      if (!saved && form.get("saveAddress") === "on") {
        // Convenience only: the order does not depend on it
        await api.post("/users/me/addresses", shippingAddress).catch(() => undefined);
      }
      const order = await api.post<Order>(
        "/orders",
        {
          items,
          shippingAddress,
          paymentMethod: method,
          note: String(form.get("note") ?? "").trim() || null,
          couponCode: coupon?.code ?? null,
        },
        { "Idempotency-Key": idempotencyKey },
      );
      // cart-service empties the cart when it receives order.created, shortly after
      setTimeout(() => void refresh(), 2000);
      if (order.paymentMethod === "ONLINE") {
        setPlaced(order);
        // A reload of the page keeps the payment step instead of an empty checkout
        window.history.replaceState(null, "", `/orders/${order.id}/payment`);
      } else {
        router.push(`/orders/${order.id}?placed=1`);
      }
    } catch (caught) {
      setSubmitting(false);
      setError(caught instanceof ApiError ? caught : errorMessage(caught));
    }
  };

  const fieldError = (field: keyof AddressValues) =>
    error instanceof ApiError ? error.fieldError(`shippingAddress.${field}`) : undefined;
  const message = typeof error === "string" ? error : error ? errorMessage(error) : null;

  if (placed) {
    return (
      <section className="card mx-auto flex w-full max-w-lg flex-col gap-5 p-6 sm:p-8" aria-labelledby="payment-step">
        <div>
          <h2 id="payment-step" className="text-xl font-semibold text-slate-900">
            {t("paymentStep", { number: placed.orderNumber })}
          </h2>
          <p className="mt-1 text-slate-600">
            {t("paymentStepText", { amount: formatMoney(placed.totalAmount, placed.currency) })}
          </p>
        </div>
        <OrderPayment orderId={placed.id} />
      </section>
    );
  }

  return (
    <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1fr_380px]">
      <div className="flex flex-col gap-6">
        <fieldset className="card p-5">
          <legend className="px-1 text-lg font-semibold text-slate-900">{t("shippingAddress")}</legend>
          <div className="mt-2 flex flex-col gap-3">
            {addresses.map((address) => (
              <label
                key={address.id}
                className={`flex cursor-pointer gap-3 rounded-lg border p-3 text-sm ${
                  addressChoice === address.id ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
                }`}
              >
                <input
                  type="radio"
                  name="addressChoice"
                  value={address.id}
                  checked={addressChoice === address.id}
                  onChange={() => setAddressChoice(address.id)}
                  className="mt-1"
                />
                <span>
                  <span className="font-medium text-slate-900">{address.recipientName}</span>
                  <span className="text-slate-500"> · {formatPhone(address.phoneNumber)}</span>
                  {address.defaultAddress && <span className="ml-2 text-xs text-brand-700">{t("default")}</span>}
                  <span className="mt-0.5 block text-slate-600">{formatAddress(address)}</span>
                </span>
              </label>
            ))}
            <label
              className={`flex cursor-pointer items-center gap-3 rounded-lg border p-3 text-sm ${
                addressChoice === NEW_ADDRESS ? "border-brand-500 bg-brand-50" : "border-slate-200 hover:border-slate-300"
              }`}
            >
              <input
                type="radio"
                name="addressChoice"
                value={NEW_ADDRESS}
                checked={addressChoice === NEW_ADDRESS}
                onChange={() => setAddressChoice(NEW_ADDRESS)}
              />
              <span className="font-medium text-slate-900">{t("otherAddress")}</span>
            </label>
            {addressChoice === NEW_ADDRESS && (
              <div className="mt-2 flex flex-col gap-4">
                <AddressFields idPrefix="checkout" defaults={newAddressDefaults} error={fieldError} />
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input type="checkbox" name="saveAddress" defaultChecked={addresses.length === 0} />
                  {t("saveAddress")}
                </label>
              </div>
            )}
          </div>
        </fieldset>

        <fieldset className="card p-5">
          <legend className="px-1 text-lg font-semibold text-slate-900">{t("paymentMethod")}</legend>
          <div className="mt-2 flex flex-col gap-2">
            {PAYMENT_METHODS.map((value) => (
              <label
                key={value}
                className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 p-3 text-sm has-checked:border-brand-500 has-checked:bg-brand-50"
              >
                <input
                  type="radio"
                  name="paymentMethod"
                  value={value}
                  checked={method === value}
                  onChange={() => setMethod(value)}
                  required
                />
                {tMethod(value)}
              </label>
            ))}
          </div>
          <p className="mt-3 text-xs text-slate-500">{method === "ONLINE" ? t("onlineHint") : t("codHint")}</p>
        </fieldset>

        <div className="card p-5">
          <label htmlFor="note" className="label">
            {t("noteLabel")}
          </label>
          <textarea id="note" name="note" rows={3} maxLength={500} className="input" placeholder={t("notePlaceholder")} />
        </div>
      </div>

      <aside className="card h-fit p-5 lg:sticky lg:top-32">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">
          {t("summary", { items: tCommon("cartItems", { count: cart.totalQuantity }) })}
        </h2>
        <ul className="flex flex-col gap-3">
          {cart.items.map((item) => (
            <li key={item.productId} className="flex gap-3 text-sm">
              <Thumb src={item.thumbnailUrl} alt={item.productName} className="h-12 w-12" />
              <div className="min-w-0 flex-1">
                <p className="line-clamp-2 text-slate-800">{item.productName}</p>
                <p className="text-slate-500">× {item.quantity}</p>
              </div>
              <p className="font-medium whitespace-nowrap">{formatMoney(item.lineTotal, item.currency)}</p>
            </li>
          ))}
        </ul>
        <div className="mt-4 border-t border-slate-100 pt-4">
          <label htmlFor="couponCode" className="label">
            {tCoupon("title")}
          </label>
          {coupon ? (
            <div className="flex items-center justify-between gap-2 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm">
              <span className="min-w-0 text-emerald-800">
                <span className="font-medium">{tCoupon("applied", { code: coupon.code })}</span>
                {coupon.description && <span className="block truncate text-xs">{coupon.description}</span>}
              </span>
              <button type="button" className="link shrink-0 text-xs" onClick={removeCoupon}>
                {tCoupon("remove")}
              </button>
            </div>
          ) : (
            <div className="flex gap-2">
              <input
                id="couponCode"
                value={couponCode}
                onChange={(event) => setCouponCode(event.target.value)}
                onKeyDown={couponKeyDown}
                maxLength={30}
                autoComplete="off"
                placeholder={tCoupon("placeholder")}
                className="input min-w-0 flex-1 uppercase placeholder:normal-case"
                aria-invalid={couponError ? true : undefined}
                aria-describedby={couponError ? "coupon-error" : undefined}
              />
              <Button type="button" variant="secondary" loading={couponBusy} onClick={() => void applyCoupon()}>
                {tCoupon("apply")}
              </Button>
            </div>
          )}
          {couponError && (
            <p id="coupon-error" className="mt-1 text-xs text-rose-600">
              {couponError}
            </p>
          )}
        </div>
        <dl className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-600">{tCommon("subtotal")}</dt>
            <dd>{formatMoney(coupon?.subtotal ?? cart.subtotal, coupon?.currency ?? cart.currency)}</dd>
          </div>
          {coupon && coupon.discountAmount > 0 && (
            <div className="flex justify-between text-emerald-700">
              <dt>{tCoupon("discountLine", { code: coupon.code })}</dt>
              <dd>−{formatMoney(coupon.discountAmount, coupon.currency)}</dd>
            </div>
          )}
          <div className="flex justify-between">
            <dt className="text-slate-600">{tCommon("shippingFee")}</dt>
            {coupon ? (
              <dd>{coupon.shippingFee > 0 ? formatMoney(coupon.shippingFee, coupon.currency) : tCommon("free")}</dd>
            ) : (
              <dd className="text-slate-500">{t("shippingLater")}</dd>
            )}
          </div>
          {coupon && (
            <div className="flex justify-between text-base font-semibold">
              <dt>{tCommon("total")}</dt>
              <dd className="text-accent-500">{formatMoney(coupon.totalAmount, coupon.currency)}</dd>
            </div>
          )}
        </dl>
        <p className="mt-3 text-xs text-slate-500">{t("pricesRechecked")}</p>
        {message && (
          <div className="mt-4">
            <Alert tone="danger">{message}</Alert>
          </div>
        )}
        <Button type="submit" size="lg" className="mt-5 w-full" loading={submitting}>
          {method === "ONLINE" ? t("placeAndPay") : t("placeOrder")}
        </Button>
      </aside>
    </form>
  );
}

function pick(address: Address): AddressValues {
  const { recipientName, phoneNumber, addressLine1, addressLine2, ward, district, city, postalCode, countryCode } = address;
  // Numbers saved before the +7 format was enforced are normalized like the ones typed in the form
  return {
    recipientName,
    phoneNumber: normalizePhone(phoneNumber),
    addressLine1,
    addressLine2,
    ward,
    district,
    city,
    postalCode,
    countryCode,
  };
}
