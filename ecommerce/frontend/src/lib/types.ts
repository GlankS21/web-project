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

export type MovementType = "ADJUSTMENT" | "RESERVATION" | "RELEASE" | "SALE";

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
export type PaymentMethod = "COD" | "CARD" | "E_WALLET";

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
}

export interface OrderSummary {
  id: string;
  orderNumber: string;
  userId: string;
  userEmail: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  totalAmount: number;
  currency: string;
  itemCount: number;
  firstItemName: string | null;
  firstItemThumbnailUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------- payment-service

export type PaymentStatus = "SUCCEEDED" | "FAILED" | "REFUNDED" | "CANCELLED";

export interface Payment {
  id: string;
  orderId: string;
  orderNumber: string;
  userId: string;
  amount: number | null;
  currency: string | null;
  method: PaymentMethod | null;
  status: PaymentStatus;
  transactionId: string | null;
  failureReason: string | null;
  refundTransactionId: string | null;
  refundedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------- notification-service

export type NotificationType = "WELCOME" | "ORDER_PLACED" | "ORDER_CONFIRMED" | "ORDER_CANCELLED";
export type NotificationStatus = "PENDING" | "SENT" | "FAILED";

export interface Notification {
  id: string;
  type: NotificationType;
  userId: string | null;
  recipient: string;
  reference: string | null;
  subject: string;
  body: string;
  status: NotificationStatus;
  attempts: number;
  lastError: string | null;
  sentAt: string | null;
  createdAt: string;
}
