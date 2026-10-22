"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { useCart } from "@/components/providers";
import { Alert, Button, ButtonLink, EmptyState, StatusBadge, Thumb } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { CART_ITEM_STATUS_TONE, formatMoney, labelled } from "@/lib/format";
import type { Cart, CartItem } from "@/lib/types";

/**
 * Lines are re-priced by cart-service on every read; each change is sent at once and the page is
 * re-rendered from the server so prices and stock stay current.
 */
export function CartView({ cart }: { cart: Cart }) {
  const t = useTranslations("cart");
  const tCommon = useTranslations("common");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const { refresh } = useCart();
  const [busyProduct, setBusyProduct] = useState<number | "all" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isRefreshing, startTransition] = useTransition();

  const change = async (productId: number | "all", action: () => Promise<unknown>) => {
    setBusyProduct(productId);
    setError(null);
    try {
      await action();
      await refresh();
      startTransition(() => router.refresh());
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusyProduct(null);
    }
  };

  if (cart.items.length === 0) {
    return (
      <EmptyState title={t("empty")}>
        <ButtonLink href="/products">{t("continueShopping")}</ButtonLink>
      </EmptyState>
    );
  }

  const blocked = cart.items.filter(
    (item) => item.status === "INSUFFICIENT_STOCK" || item.status === "OUT_OF_STOCK" || item.status === "UNAVAILABLE",
  );
  const busy = busyProduct !== null || isRefreshing;

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_340px]">
      <section className="flex flex-col gap-3" aria-label={t("items")}>
        {error && <Alert tone="danger">{error}</Alert>}
        {!cart.pricesUpToDate && (
          <Alert tone="warning">{t("pricesOutdated")}</Alert>
        )}
        {cart.items.map((item) => (
          <CartLine
            key={item.productId}
            item={item}
            busy={busy}
            onQuantity={(quantity) => change(item.productId, () => api.put(`/cart/items/${item.productId}`, { quantity }))}
            onRemove={() => change(item.productId, () => api.delete(`/cart/items/${item.productId}`))}
          />
        ))}
        <div>
          <Button variant="ghost" size="sm" disabled={busy} onClick={() => change("all", () => api.delete("/cart"))}>
            {t("clear")}
          </Button>
        </div>
      </section>

      <aside className="card h-fit p-5 lg:sticky lg:top-32">
        <h2 className="mb-4 text-lg font-semibold text-slate-900">{t("summary")}</h2>
        <dl className="flex flex-col gap-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-slate-600">{t("quantity")}</dt>
            <dd>{tCommon("cartItems", { count: cart.totalQuantity })}</dd>
          </div>
          <div className="flex justify-between text-base font-semibold">
            <dt>{tCommon("subtotal")}</dt>
            <dd className="text-accent-500">{formatMoney(cart.subtotal, cart.currency)}</dd>
          </div>
        </dl>
        <p className="mt-2 text-xs text-slate-500">{t("shippingLater")}</p>
        {blocked.length > 0 && (
          <div className="mt-4">
            <Alert tone="warning">{t("blocked", { count: blocked.length })}</Alert>
          </div>
        )}
        {cart.checkoutReady ? (
          <ButtonLink href="/checkout" size="lg" className="mt-5 w-full">
            {t("checkout")}
          </ButtonLink>
        ) : (
          <Button size="lg" className="mt-5 w-full" disabled>
            {t("checkout")}
          </Button>
        )}
        <Link href="/products" className="link mt-3 block text-center text-sm">
          {t("continueShopping")}
        </Link>
      </aside>
    </div>
  );
}

interface LineProps {
  item: CartItem;
  busy: boolean;
  onQuantity: (quantity: number) => void;
  onRemove: () => void;
}

function CartLine({ item, busy, onQuantity, onRemove }: LineProps) {
  const t = useTranslations("cart");
  const tProduct = useTranslations("product");
  const tStatus = useTranslations("cartItemStatus");
  const statuses = labelled(CART_ITEM_STATUS_TONE, (value) => tStatus(value));
  const unavailable = item.status === "UNAVAILABLE";
  const maxQuantity = item.availableQuantity !== null ? Math.max(item.availableQuantity, 1) : 99;
  return (
    <article className="card flex gap-4 p-4">
      <Thumb src={item.thumbnailUrl} alt={item.productName} className="h-20 w-20" />
      <div className="flex min-w-0 flex-1 flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h3 className="font-medium text-slate-900">{item.productName}</h3>
          <p className="text-xs text-slate-500">{tProduct("sku", { sku: item.sku })}</p>
          <div className="mt-1 flex flex-wrap items-center gap-2 text-sm">
            <span>{formatMoney(item.unitPrice, item.currency)}</span>
            {item.priceChanged && <span className="text-xs text-amber-700">{t("priceChanged")}</span>}
            {item.status !== "AVAILABLE" && <StatusBadge value={item.status} labels={statuses} />}
            {item.status === "INSUFFICIENT_STOCK" && item.availableQuantity !== null && item.availableQuantity > 0 && (
              <button
                type="button"
                className="text-xs font-medium text-brand-600 hover:underline"
                disabled={busy}
                onClick={() => onQuantity(item.availableQuantity as number)}
              >
                {t("reduceTo", { count: item.availableQuantity })}
              </button>
            )}
          </div>
        </div>
        <div className="flex items-center gap-4 sm:flex-col sm:items-end">
          {!unavailable && (
            <div className="inline-flex items-center rounded-lg border border-slate-300 bg-white text-sm">
              <button
                type="button"
                className="px-2.5 py-1 text-slate-600 hover:bg-slate-50 disabled:text-slate-300"
                disabled={busy || item.quantity <= 1}
                onClick={() => onQuantity(item.quantity - 1)}
                aria-label={t("decrease", { name: item.productName })}
              >
                −
              </button>
              <span className="w-10 text-center" aria-label={t("quantity")}>
                {item.quantity}
              </span>
              <button
                type="button"
                className="px-2.5 py-1 text-slate-600 hover:bg-slate-50 disabled:text-slate-300"
                disabled={busy || item.quantity >= maxQuantity}
                onClick={() => onQuantity(item.quantity + 1)}
                aria-label={t("increase", { name: item.productName })}
              >
                +
              </button>
            </div>
          )}
          <p className="font-semibold text-slate-900">{unavailable ? "—" : formatMoney(item.lineTotal, item.currency)}</p>
          <button
            type="button"
            className="text-xs text-rose-600 hover:underline disabled:text-slate-400"
            disabled={busy}
            onClick={onRemove}
            aria-label={t("remove", { name: item.productName })}
          >
            {t("removeShort")}
          </button>
        </div>
      </div>
    </article>
  );
}
