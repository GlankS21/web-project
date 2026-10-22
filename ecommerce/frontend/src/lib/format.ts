import type {
  CartItemStatus,
  MovementType,
  NotificationStatus,
  NotificationType,
  OrderStatus,
  PaymentMethod,
  PaymentStatus,
  ProductStatus,
  TransactionStatus,
} from "./types";
import { INTL_LOCALE, TIME_ZONE } from "@/i18n/config";

const ZERO_DECIMAL_CURRENCIES = new Set(["JPY", "KRW"]);

/** 1234.5 RUB → "1 234,50 ₽"; null → "—". */
export function formatMoney(amount: number | null | undefined, currency: string | null | undefined = "RUB"): string {
  if (amount === null || amount === undefined) {
    return "—";
  }
  const code = currency ?? "RUB";
  const digits = ZERO_DECIMAL_CURRENCIES.has(code) ? 0 : 2;
  return new Intl.NumberFormat(INTL_LOCALE, {
    style: "currency",
    currency: code,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(amount);
}

// Explicit fields give dd.MM.yyyy whatever the default style of the locale
const DATE_FIELDS = { day: "2-digit", month: "2-digit", year: "numeric" } as const;

/** Date and time in the shop's time zone (the server may run in UTC). */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  // "21.10.2026, 13:00"
  return new Intl.DateTimeFormat(INTL_LOCALE, {
    ...DATE_FIELDS,
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: TIME_ZONE,
  }).format(new Date(value));
}

/** "21.10.2026" */
export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return new Intl.DateTimeFormat(INTL_LOCALE, { ...DATE_FIELDS, timeZone: TIME_ZONE }).format(new Date(value));
}

/** Percentage saved against the crossed-out price, or null when there is no discount. */
export function discountPercent(price: number, compareAtPrice: number | null | undefined): number | null {
  if (!compareAtPrice || compareAtPrice <= price) {
    return null;
  }
  return Math.round(((compareAtPrice - price) / compareAtPrice) * 100);
}

export type Tone = "neutral" | "info" | "success" | "warning" | "danger";

interface Label {
  label: string;
  tone: Tone;
}

/** Gives each value of a status its translated label, e.g. `labelled(PAYMENT_STATUS_TONE, t)`. */
export function labelled<K extends string>(tones: Record<K, Tone>, label: (value: K) => string): Record<K, Label> {
  return Object.fromEntries(
    (Object.keys(tones) as K[]).map((value) => [value, { label: label(value), tone: tones[value] }]),
  ) as Record<K, Label>;
}

export const ORDER_STATUS_TONE: Record<OrderStatus, Tone> = {
  PENDING: "info",
  AWAITING_PAYMENT: "warning",
  PAID: "success",
  SHIPPED: "info",
  DELIVERED: "success",
  CANCELLED: "danger",
};

export const PAYMENT_METHODS: PaymentMethod[] = ["ONLINE", "COD"];

export const PAYMENT_STATUS_TONE: Record<PaymentStatus, Tone> = {
  PENDING: "warning",
  SUCCEEDED: "success",
  FAILED: "danger",
  REFUND_PENDING: "warning",
  REFUNDED: "info",
  REFUND_FAILED: "danger",
  CANCELLED: "neutral",
};

export const TRANSACTION_STATUS_TONE: Record<TransactionStatus, Tone> = {
  PENDING: "warning",
  SUCCEEDED: "success",
  FAILED: "danger",
};

export const PRODUCT_STATUS_TONE: Record<ProductStatus, Tone> = {
  DRAFT: "neutral",
  ACTIVE: "success",
  ARCHIVED: "danger",
};

export const CART_ITEM_STATUS_TONE: Record<CartItemStatus, Tone> = {
  AVAILABLE: "success",
  INSUFFICIENT_STOCK: "warning",
  OUT_OF_STOCK: "danger",
  UNAVAILABLE: "danger",
  UNKNOWN: "neutral",
};

export const NOTIFICATION_TYPES: NotificationType[] = [
  "WELCOME",
  "ORDER_PLACED",
  "ORDER_CONFIRMED",
  "ORDER_CANCELLED",
  "PASSWORD_RESET",
];

export const NOTIFICATION_STATUS_TONE: Record<NotificationStatus, Tone> = {
  PENDING: "neutral",
  SENT: "success",
  FAILED: "danger",
};

export const MOVEMENT_TYPES: MovementType[] = ["ADJUSTMENT", "RESERVATION", "RELEASE", "SALE", "RETURN"];
