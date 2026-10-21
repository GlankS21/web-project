import type {
  CartItemStatus,
  MovementType,
  NotificationStatus,
  NotificationType,
  OrderStatus,
  PaymentMethod,
  PaymentStatus,
  ProductStatus,
} from "./types";

const LOCALE = "vi-VN";
const ZERO_DECIMAL_CURRENCIES = new Set(["VND", "JPY", "KRW"]);

/** 25140000 VND → "25.140.000 ₫"; null → "—". */
export function formatMoney(amount: number | null | undefined, currency: string | null | undefined = "VND"): string {
  if (amount === null || amount === undefined) {
    return "—";
  }
  const code = currency ?? "VND";
  const digits = ZERO_DECIMAL_CURRENCIES.has(code) ? 0 : 2;
  return new Intl.NumberFormat(LOCALE, {
    style: "currency",
    currency: code,
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(amount);
}

/** Date and time in the shop's time zone (the server may run in UTC). */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  // Explicit fields: the "short" date style of vi-VN has a two-digit year
  return new Intl.DateTimeFormat(LOCALE, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: "Asia/Ho_Chi_Minh",
  }).format(new Date(value));
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium", timeZone: "Asia/Ho_Chi_Minh" }).format(
    new Date(value),
  );
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

export const ORDER_STATUS: Record<OrderStatus, Label> = {
  PENDING: { label: "Đang giữ hàng", tone: "info" },
  AWAITING_PAYMENT: { label: "Chờ thanh toán", tone: "warning" },
  PAID: { label: "Đã thanh toán", tone: "success" },
  SHIPPED: { label: "Đang giao", tone: "info" },
  DELIVERED: { label: "Đã giao", tone: "success" },
  CANCELLED: { label: "Đã huỷ", tone: "danger" },
};

export const PAYMENT_METHOD: Record<PaymentMethod, string> = {
  COD: "Thanh toán khi nhận hàng (COD)",
  CARD: "Thẻ ngân hàng",
  E_WALLET: "Ví điện tử",
};

export const PAYMENT_STATUS: Record<PaymentStatus, Label> = {
  SUCCEEDED: { label: "Thành công", tone: "success" },
  FAILED: { label: "Thất bại", tone: "danger" },
  REFUNDED: { label: "Đã hoàn tiền", tone: "info" },
  CANCELLED: { label: "Không thu tiền", tone: "neutral" },
};

export const PRODUCT_STATUS: Record<ProductStatus, Label> = {
  DRAFT: { label: "Nháp", tone: "neutral" },
  ACTIVE: { label: "Đang bán", tone: "success" },
  ARCHIVED: { label: "Ngừng bán", tone: "danger" },
};

export const CART_ITEM_STATUS: Record<CartItemStatus, Label> = {
  AVAILABLE: { label: "Còn hàng", tone: "success" },
  INSUFFICIENT_STOCK: { label: "Không đủ hàng", tone: "warning" },
  OUT_OF_STOCK: { label: "Hết hàng", tone: "danger" },
  UNAVAILABLE: { label: "Ngừng bán", tone: "danger" },
  UNKNOWN: { label: "Chưa rõ tồn kho", tone: "neutral" },
};

export const NOTIFICATION_TYPE: Record<NotificationType, string> = {
  WELCOME: "Chào mừng",
  ORDER_PLACED: "Nhận đơn",
  ORDER_CONFIRMED: "Xác nhận đơn",
  ORDER_CANCELLED: "Huỷ đơn",
};

export const NOTIFICATION_STATUS: Record<NotificationStatus, Label> = {
  PENDING: { label: "Đang chờ", tone: "neutral" },
  SENT: { label: "Đã gửi", tone: "success" },
  FAILED: { label: "Lỗi gửi", tone: "danger" },
};

export const MOVEMENT_TYPE: Record<MovementType, string> = {
  ADJUSTMENT: "Điều chỉnh",
  RESERVATION: "Giữ hàng",
  RELEASE: "Trả hàng giữ",
  SALE: "Xuất bán",
};
