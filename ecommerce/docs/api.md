# API

Mọi API đi qua gateway: `http://localhost:8080`. Tài liệu tương tác (OpenAPI 3, có nút
*Authorize* để dán access token): **http://localhost:8080/swagger-ui.html**, chọn service ở góc phải.
OpenAPI JSON của từng service: `http://localhost:8080/docs/<service>/v3/api-docs`.

## Quy ước

| | |
|---|---|
| Xác thực | `Authorization: Bearer <accessToken>` (lấy từ `/api/auth/login` hoặc `/api/auth/register`) |
| Thành công | `{ "success": true, "message": "...", "data": ..., "timestamp": "..." }` |
| Lỗi | `{ "timestamp", "status", "error", "code", "message", "path", "correlationId", "violations": [{ "field", "message" }] }` |
| Phân trang | `?page=0&size=20&sort=createdAt,desc` (tối đa 100/trang) → `data = { content, page, size, totalElements, totalPages, first, last }` |
| Tiền | Số thập phân JSON (`24990000`), tiền tệ ISO 4217 trong trường `currency` |
| Thời gian | ISO-8601 UTC (`2026-10-21T10:00:00Z`) |
| Truy vết | Gửi hoặc nhận `X-Correlation-Id`; có trong mọi phản hồi lỗi |

| Mã | Khi nào |
|---|---|
| 400 | Dữ liệu không hợp lệ (`violations` liệt kê trường sai), JSON hỏng |
| 401 | Thiếu token, token hết hạn hoặc sai; sai mật khẩu |
| 403 | Không đủ quyền; tài khoản bị khoá |
| 404 | Không tìm thấy, hoặc không thuộc về người gọi |
| 409 | Trùng (email, SKU, slug) |
| 422 | Vi phạm quy tắc nghiệp vụ (hết hàng, chuyển trạng thái không hợp lệ…) |
| 429 | Vượt rate limit, xem header `Retry-After` |
| 503 | Service phụ thuộc không phản hồi (circuit breaker) |

Quyền: **công khai**, **đăng nhập** (mọi tài khoản), **admin** (`ROLE_ADMIN`).

## auth-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | công khai | Đăng ký khách hàng và đăng nhập luôn (201) |
| POST | `/api/auth/login` | công khai | Đăng nhập, trả access token + refresh token |
| POST | `/api/auth/refresh` | công khai | Đổi refresh token lấy cặp token mới (token cũ hết hiệu lực) |
| POST | `/api/auth/logout` | công khai | Thu hồi refresh token gửi lên |
| POST | `/api/auth/logout-all` | đăng nhập | Thu hồi mọi phiên của tài khoản |
| GET | `/api/auth/me` | đăng nhập | Tài khoản hiện tại |
| PUT | `/api/auth/me/password` | đăng nhập | Đổi mật khẩu (thu hồi mọi phiên) |
| GET | `/api/auth/admin/accounts?email=` | admin | Tìm tài khoản |
| GET | `/api/auth/admin/accounts/{id}` | admin | Chi tiết tài khoản |
| PUT | `/api/auth/admin/accounts/{id}/roles` | admin | Đặt lại quyền `{ "roles": ["ROLE_CUSTOMER", "ROLE_ADMIN"] }` |
| PATCH | `/api/auth/admin/accounts/{id}/status` | admin | Khoá / mở khoá `{ "enabled": false }` (thu hồi phiên; không tự khoá mình) |

Mật khẩu: 8–72 ký tự, có chữ cái và chữ số. Access token sống 15 phút, refresh token 7 ngày.

## user-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET / PUT | `/api/users/me` | đăng nhập | Xem / sửa hồ sơ (họ tên, số điện thoại, ngày sinh) |
| GET / POST | `/api/users/me/addresses` | đăng nhập | Danh sách (mặc định đứng đầu) / thêm địa chỉ (201, tối đa 10) |
| GET / PUT / DELETE | `/api/users/me/addresses/{id}` | đăng nhập | Một địa chỉ |
| PATCH | `/api/users/me/addresses/{id}/default` | đăng nhập | Đặt làm mặc định |
| GET | `/api/users?keyword=` | admin | Tìm hồ sơ theo email hoặc tên |
| GET | `/api/users/{id}` | admin | Hồ sơ của một người dùng |

## product-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/products` | công khai | Tìm kiếm: `keyword`, `categoryId` (gồm danh mục con), `brand`, `minPrice`, `maxPrice`, `sort` (`price`, `name`, `createdAt`, `updatedAt`, `sku`); admin lọc thêm `status` |
| GET | `/api/products/{id}`, `/api/products/slug/{slug}` | công khai | Chi tiết (khách chỉ thấy sản phẩm đang bán) |
| GET | `/api/products/by-ids?ids=1,2` | công khai | Nhiều sản phẩm (tối đa 100) |
| POST | `/api/products` | admin | Tạo (mặc định `DRAFT`) |
| PUT | `/api/products/{id}` | admin | Sửa (không đổi SKU) |
| PATCH | `/api/products/{id}/status` | admin | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| DELETE | `/api/products/{id}` | admin | Ngừng bán (xoá mềm) |
| GET | `/api/categories` | công khai | Cây danh mục hai cấp |
| GET | `/api/categories/{id}`, `/api/categories/slug/{slug}` | công khai | Một danh mục |
| POST / PUT / DELETE | `/api/categories[/{id}]` | admin | Quản lý danh mục (chỉ xoá được danh mục rỗng) |

## inventory-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/inventory/availability?productIds=1,2` | công khai | Số lượng có thể bán (tối đa 100 sản phẩm) |
| GET | `/api/inventory?lowStock=true` | admin | Danh sách tồn kho |
| GET | `/api/inventory/{productId}` | admin | Tồn kho một sản phẩm |
| PUT | `/api/inventory/{productId}` | admin | Kiểm kê: đặt tồn thực tế `{ "quantityOnHand", "lowStockThreshold", "reason" }` |
| POST | `/api/inventory/{productId}/adjustments` | admin | Nhập / xuất `{ "quantityChange": 20, "reason": "..." }` |
| GET | `/api/inventory/{productId}/movements` | admin | Sổ biến động kho |
| GET | `/api/inventory/reservations/{orderId}` | admin | Hàng đang giữ cho một đơn |

## cart-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/cart` | đăng nhập | Giỏ hàng với giá và tồn kho hiện tại, trạng thái từng dòng, `checkoutReady` |
| POST | `/api/cart/items` | đăng nhập | Thêm `{ "productId", "quantity" }` (cộng dồn) |
| PUT | `/api/cart/items/{productId}` | đăng nhập | Đặt số lượng `{ "quantity" }` |
| DELETE | `/api/cart/items/{productId}` | đăng nhập | Xoá một dòng |
| DELETE | `/api/cart` | đăng nhập | Xoá cả giỏ |

## order-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/orders` | đăng nhập | Đặt hàng; nên gửi `Idempotency-Key`. Trả `201` (mới) hoặc `200` (đơn đã tạo với cùng key) |
| GET | `/api/orders?status=` | đăng nhập | Đơn của tôi |
| GET | `/api/orders/{id}` | đăng nhập | Chi tiết và lịch sử trạng thái (admin xem mọi đơn) |
| POST | `/api/orders/{id}/cancel` | đăng nhập | Huỷ đơn chưa thanh toán `{ "reason" }` (chủ đơn hoặc admin) |
| GET | `/api/orders/admin?status=&userId=&keyword=` | admin | Tìm đơn (keyword: mã đơn, email) |
| PATCH | `/api/orders/admin/{id}/status` | admin | `PAID → SHIPPED`, `SHIPPED → DELIVERED` `{ "status", "note" }` |

Ví dụ thân request đặt hàng:

```json
{
  "items": [{ "productId": 7, "quantity": 1 }],
  "paymentMethod": "CARD",
  "shippingAddress": {
    "recipientName": "Nguyễn An", "phoneNumber": "0912345678",
    "addressLine1": "1 Lê Lợi", "city": "Hồ Chí Minh", "countryCode": "VN"
  },
  "note": "Giao giờ hành chính"
}
```

Giá được lấy lại từ catalogue khi đặt; giá trong giỏ chỉ để hiển thị. `paymentMethod`: `COD`, `CARD`, `E_WALLET`.

## payment-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/payments/orders/{orderId}` | đăng nhập | Thanh toán của một đơn (chủ đơn hoặc admin); `404` khi chưa có |
| GET | `/api/payments` | admin | Mọi giao dịch |

Thanh toán được tạo tự động trong Saga, không có API tạo trực tiếp.

## notification-service

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/notifications` | đăng nhập | E-mail đã gửi cho tôi |
| GET | `/api/notifications/admin?status=&type=` | admin | Tìm e-mail |
| POST | `/api/notifications/admin/{id}/resend` | admin | Gửi lại |
