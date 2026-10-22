// Shapes of the backend DTOs, as returned by the API gateway. Amounts are JSON numbers (BigDecimal),
// instants are ISO-8601 strings.

export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface FieldViolation {
  field: string;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path?: string;
  correlationId?: string;
  violations?: FieldViolation[];
}

// ---------------------------------------------------------------- auth-service

export type RoleName = "ROLE_CUSTOMER" | "ROLE_ADMIN";

export interface Account {
  id: string;
  email: string;
  roles: RoleName[];
  enabled: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
  account: Account;
}

// ---------------------------------------------------------------- user-service

export interface UserProfile {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phoneNumber: string | null;
  dateOfBirth: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AddressFields {
  recipientName: string;
  phoneNumber: string;
  addressLine1: string;
  addressLine2?: string | null;
  ward?: string | null;
  district?: string | null;
  city: string;
  postalCode?: string | null;
  countryCode: string;
}

export interface Address extends AddressFields {
  id: string;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------- product-service

export type ProductStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";

export interface Category {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  parentId: number | null;
  sortOrder: number;
  children: Category[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductSummary {
  id: number;
  sku: string;
  name: string;
  slug: string;
  shortDescription: string | null;
  brand: string | null;
  price: number;
  compareAtPrice: number | null;
  currency: string;
  thumbnailUrl: string | null;
  categoryId: number;
  categoryName: string;
  status: ProductStatus;
  /** 0 when the product has no review yet. */
  ratingAverage: number;
  ratingCount: number;
}

export interface ProductImage {
  id: number;
  url: string;
  altText: string | null;
  sortOrder: number;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  slug: string;
  shortDescription: string | null;
  description: string | null;
  brand: string | null;
  price: number;
  compareAtPrice: number | null;
  currency: string;
  status: ProductStatus;
  category: { id: number; name: string; slug: string };
  images: ProductImage[];
  ratingAverage: number;
  ratingCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  sku: string;
  name: string;
  slug?: string | null;
  shortDescription?: string | null;
  description?: string | null;
  brand?: string | null;
  price: number;
  compareAtPrice?: number | null;
  categoryId: number;
  status?: ProductStatus;
  images?: { url: string; altText?: string | null }[];
}

/** Answer of POST /api/products/images. */
export interface UploadedImage {
  /** Same-origin path, e.g. /api/products/images/<uuid>.webp */
  url: string;
  contentType: string;
  size: number;
}

export interface Review {
  id: number;
  productId: number;
  authorName: string;
  rating: number;
  comment: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface MyReview {
  /** The caller received a delivered order containing the product. */
  purchased: boolean;
  canReview: boolean;
  review: Review | null;
}

export interface AdminReview extends Review {
  productName: string;
  productSlug: string;
  userId: string;
}

// ---------------------------------------------------------------- inventory-service

export interface StockAvailability {
  productId: number;
  availableQuantity: number;
  inStock: boolean;
}

export interface InventoryItem {
  productId: number;
  sku: string | null;
  quantityOnHand: number;
  quantityReserved: number;
  availableQuantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  updatedAt: string;
}

export type MovementType = "ADJUSTMENT" | "RESERVATION" | "RELEASE" | "SALE" | "RETURN";

export interface StockMovement {
  id: number;
  productId: number;
  type: MovementType;
  onHandChange: number;
  reservedChange: number;
  onHandAfter: number;
  reservedAfter: number;
  reference: string | null;
  reason: string | null;
  actorId: string | null;
  createdAt: string;
}

// ---------------------------------------------------------------- cart-service

export type CartItemStatus = "AVAILABLE" | "INSUFFICIENT_STOCK" | "OUT_OF_STOCK" | "UNAVAILABLE" | "UNKNOWN";

export interface CartItem {
  productId: number;
  sku: string;
  productName: string;
  thumbnailUrl: string | null;
  unitPrice: number;
  currency: string;
  quantity: number;
  lineTotal: number;
  priceChanged: boolean;
  availableQuantity: number | null;
  status: CartItemStatus;
  addedAt: string;
}

export interface Cart {
  items: CartItem[];
  totalQuantity: number;
  subtotal: number;
  currency: string | null;
  checkoutReady: boolean;
  pricesUpToDate: boolean;
}

// ---------------------------------------------------------------- order-service

export type OrderStatus = "PENDING" | "AWAITING_PAYMENT" | "PAID" | "SHIPPED" | "DELIVERED" | "CANCELLED";
export type PaymentMethod = "COD" | "ONLINE";

export interface OrderItem {
  productId: number;
  sku: string;
  productName: string;
  thumbnailUrl: string | null;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderStatusHistory {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  reason: string | null;
  changedBy: string | null;
  createdAt: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId: string;
  userEmail: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  subtotal: number;
  shippingFee: number;
  /** Discount of the coupon, 0 without one. */
  discountAmount: number;
  couponCode: string | null;
  totalAmount: number;
  currency: string;
  shippingAddress: AddressFields;
  note: string | null;
  cancellationReason: string | null;
  paymentId: string | null;
  paymentTransactionId: string | null;
  items: OrderItem[];
  statusHistory: OrderStatusHistory[];
  createdAt: string;
  updatedAt: string;
  paidAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  refundTransactionId: string | null;
  refundedAt: string | null;
}

export interface OrderSummary {
  id: string;
  orderNumber: string;
  userId: string;
  userEmail: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  discountAmount: number;
  couponCode: string | null;
  totalAmount: number;
  currency: string;
  itemCount: number;
  firstItemName: string | null;
  firstItemThumbnailUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export type CouponDiscountType = "PERCENTAGE" | "FIXED_AMOUNT";

export interface Coupon {
  id: number;
  code: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: number;
  minOrderAmount: number | null;
  maxDiscountAmount: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  usageLimit: number | null;
  usedCount: number;
  perUserLimit: number | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CouponRequest {
  code: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: number;
  minOrderAmount: number | null;
  maxDiscountAmount: number | null;
  startsAt: string | null;
  expiresAt: string | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  active: boolean;
}

/** Totals of the cart with a coupon, computed by order-service with the current prices. */
export interface CouponPreview {
  code: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: number;
  subtotal: number;
  discountAmount: number;
  shippingFee: number;
  totalAmount: number;
  currency: string;
}

export interface DailyStats {
  /** yyyy-MM-dd in the shop's time zone */
  date: string;
  orders: number;
  paidOrders: number;
  revenue: number;
}

export interface TopProduct {
  productId: number;
  productName: string;
  sku: string;
  thumbnailUrl: string | null;
  quantity: number;
  revenue: number;
}

export interface OrderStats {
  from: string;
  to: string;
  currency: string;
  revenue: number;
  orderCount: number;
  paidOrderCount: number;
  cancelledOrderCount: number;
  averageOrderValue: number;
  ordersByStatus: Partial<Record<OrderStatus, number>>;
  daily: DailyStats[];
  topProducts: TopProduct[];
}

// ---------------------------------------------------------------- payment-service

export type PaymentStatus =
  | "PENDING"
  | "SUCCEEDED"
  | "FAILED"
  | "REFUND_PENDING"
  | "REFUNDED"
  | "REFUND_FAILED"
  | "CANCELLED";
export type PaymentProvider = "COD" | "MOCK" | "YOOKASSA" | "LEGACY";
export type TransactionType = "CHARGE" | "REFUND";
export type TransactionStatus = "PENDING" | "SUCCEEDED" | "FAILED";

export interface PaymentTransaction {
  id: string;
  type: TransactionType;
  provider: PaymentProvider;
  /** Id of the operation at the provider, e.g. the YooKassa payment id. */
  providerId: string | null;
  /** For a refund: the charge it gives back. */
  parentId: string | null;
  status: TransactionStatus;
  amount: number;
  currency: string;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Answer of the mock card form (PAYMENT_PROVIDER=mock). */
export interface MockCardPaymentResult {
  threeDSecureRequired: boolean;
  payment: Payment;
}

export interface Payment {
  id: string;
  orderId: string;
  orderNumber: string;
  userId: string;
  amount: number | null;
  currency: string | null;
  method: PaymentMethod | null;
  provider: PaymentProvider | null;
  status: PaymentStatus;
  /** Initialises the YooKassa widget (or the mock card form) while the payment is PENDING. */
  confirmationToken: string | null;
  attempts: number;
  transactionId: string | null;
  /** Code of the last decline, e.g. "insufficient_funds". */
  failureReason: string | null;
  refundTransactionId: string | null;
  paidAt: string | null;
  refundedAt: string | null;
  createdAt: string;
  updatedAt: string;
  /** Charge attempts and refunds, oldest first. */
  transactions: PaymentTransaction[];
}

// ---------------------------------------------------------------- notification-service

export type NotificationType = "WELCOME" | "ORDER_PLACED" | "ORDER_CONFIRMED" | "ORDER_CANCELLED" | "PASSWORD_RESET";
export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export interface Notification {
  id: string;
  type: NotificationType;
  userId: string | null;
  recipient: string;
  reference: string | null;
  subject: string;
  /** Null for e-mails whose content is secret (password reset link). */
  body: string | null;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  sentAt: string | null;
  createdAt: string;
}
