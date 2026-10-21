"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AddressFields, formatAddress, readAddress } from "@/components/address/AddressFields";
import { useCart } from "@/components/providers";
import { Alert, Button, Thumb } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";
import { formatMoney, PAYMENT_METHOD } from "@/lib/format";
import type { Address, AddressFields as AddressValues, Cart, Order, PaymentMethod } from "@/lib/types";

interface Props {
  cart: Cart;
  addresses: Address[];
  newAddressDefaults: Partial<AddressValues>;
}

const NEW_ADDRESS = "new";
const METHODS: PaymentMethod[] = ["COD", "CARD", "E_WALLET"];

export function CheckoutForm({ cart, addresses, newAddressDefaults }: Props) {
  const router = useRouter();
  const { refresh } = useCart();
  // One key per visit of the checkout page: a retried or double-submitted order is not placed twice
  const [idempotencyKey] = useState(() => crypto.randomUUID());
  const [addressChoice, setAddressChoice] = useState<string>(
    addresses.find((address) => address.defaultAddress)?.id ?? addresses[0]?.id ?? NEW_ADDRESS,
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | string | null>(null);

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
          items: cart.items.map((item) => ({ productId: item.productId, quantity: item.quantity })),
          shippingAddress,
          paymentMethod: form.get("paymentMethod"),
          note: String(form.get("note") ?? "").trim() || null,
        },
        { "Idempotency-Key": idempotencyKey },
      );
      // cart-service empties the cart when it receives order.created, shortly after
      setTimeout(() => void refresh(), 2000);
      router.push(`/orders/${order.id}?placed=1`);
    } catch (caught) {
      setSubmitting(false);
      setError(caught instanceof ApiError ? caught : errorMessage(caught));
    }
  };

  const fieldError = (field: keyof AddressValues) =>
    error instanceof ApiError ? error.fieldError(`shippingAddress.${field}`) : undefined;
  const message = typeof error === "string" ? error : error ? errorMessage(error) : null;

  return (
    <form onSubmit={submit} className="grid gap-8 lg:grid-cols-[1fr_380px]">
      <div className="flex flex-col gap-6">
        <fieldset className="card p-5">
          <legend className="px-1 text-lg font-semibold text-slate-900">Địa chỉ giao hàng</legend>
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
                  <span className="text-slate-500"> · {address.phoneNumber}</span>
                  {address.defaultAddress && <span className="ml-2 text-xs text-brand-700">Mặc định</span>}
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
              <span className="font-medium text-slate-900">Giao tới địa chỉ khác</span>
            </label>
            {addressChoice === NEW_ADDRESS && (
              <div className="mt-2 flex flex-col gap-4">
                <AddressFields idPrefix="checkout" defaults={newAddressDefaults} error={fieldError} />
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input type="checkbox" name="saveAddress" defaultChecked={addresses.length === 0} />
                  Lưu vào sổ địa chỉ
                </label>
              </div>
            )}
          </div>
        </fieldset>

        <fieldset className="card p-5">
          <legend className="px-1 text-lg font-semibold text-slate-900">Phương thức thanh toán</legend>
          <div className="mt-2 flex flex-col gap-2">
            {METHODS.map((method, index) => (
              <label key={method} className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 p-3 text-sm has-checked:border-brand-500 has-checked:bg-brand-50">
                <input type="radio" name="paymentMethod" value={method} defaultChecked={index === 0} required />
                {PAYMENT_METHOD[method]}
              </label>
            ))}
          </div>
          <p className="mt-3 text-xs text-slate-500">
            Thanh toán thẻ và ví điện tử được xử lý bởi cổng thanh toán mô phỏng ngay sau khi hàng được giữ.
          </p>
        </fieldset>

        <div className="card p-5">
          <label htmlFor="note" className="label">
            Ghi chú cho người giao hàng
          </label>
          <textarea id="note" name="note" rows={3} maxLength={500} className="input" placeholder="Ví dụ: giao giờ hành chính" />
        </div>
      </div>

      <aside className="card h-fit p-5 lg:sticky lg:top-32">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">Đơn hàng ({cart.totalQuantity} sản phẩm)</h2>
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
        <dl className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-600">Tạm tính</dt>
            <dd>{formatMoney(cart.subtotal, cart.currency)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-slate-600">Phí vận chuyển</dt>
            <dd className="text-slate-500">Tính khi đặt hàng</dd>
          </div>
        </dl>
        <p className="mt-3 text-xs text-slate-500">Giá được xác nhận lại theo bảng giá hiện tại khi đặt hàng.</p>
        {message && (
          <div className="mt-4">
            <Alert tone="danger">{message}</Alert>
          </div>
        )}
        <Button type="submit" size="lg" className="mt-5 w-full" loading={submitting}>
          Đặt hàng
        </Button>
      </aside>
    </form>
  );
}

function pick(address: Address): AddressValues {
  const { recipientName, phoneNumber, addressLine1, addressLine2, ward, district, city, postalCode, countryCode } = address;
  return { recipientName, phoneNumber, addressLine1, addressLine2, ward, district, city, postalCode, countryCode };
}
