# order-service

Đặt hàng, điều phối Saga đặt hàng (choreography qua RabbitMQ), lịch sử trạng thái đơn.
Port mặc định: **8086**. Database: `order_db`.

## Vòng đời đơn hàng

```
PENDING ──stock.reserved──► AWAITING_PAYMENT ──payment.succeeded──► PAID ──admin──► SHIPPED ──admin──► DELIVERED
   │                              │
   └──────────────┬───────────────┘
                  ▼
              CANCELLED   (hết hàng, thanh toán thất bại, khách/admin huỷ, quá hạn thanh toán)
```

- Chỉ huỷ được đơn **trước khi thanh toán** (`PENDING`, `AWAITING_PAYMENT`). Đơn đã `PAID` thì hàng đã rời kho.
- Mọi lần đổi trạng thái được ghi vào `order_status_history` (từ → đến, lý do, người thực hiện, thời điểm).
- Chuyển trạng thái sai thứ tự bị từ chối (`422`).

## Đặt hàng

`POST /api/orders` với `Idempotency-Key` (tuỳ chọn, 8–100 ký tự `[A-Za-z0-9_-]`):

1. Gộp các dòng trùng sản phẩm, kiểm tra giới hạn (`app.order.max-items`, `max-quantity-per-item`).
2. Lấy giá **hiện tại** từ product-service (`GET /api/products/by-ids`, Feign + circuit breaker). Giá client
   gửi lên không được dùng. Sản phẩm không còn bán → `422`; product-service không phản hồi → `503`.
3. Tính phí ship: `shipping.flat-fee` (30.000), miễn phí từ `shipping.free-threshold` (500.000).
4. Lưu đơn `PENDING` và ghi `order.created` vào outbox **trong cùng transaction**.

Gửi lại cùng `Idempotency-Key` trả về đơn đã tạo (`200` thay vì `201`), không tạo đơn thứ hai.
Feign được gọi ngoài transaction để không giữ kết nối DB khi chờ mạng.

## Saga (phía order)

| Nhận (queue) | Điều kiện | Kết quả |
|---|---|---|
| `stock.reserved` (`order.stock-reserved`) | `PENDING` | → `AWAITING_PAYMENT`, phát `order.payment-requested` |
| `stock.reservation-failed` (`order.stock-reservation-failed`) | `PENDING` | → `CANCELLED`, phát `order.cancelled` |
| `payment.succeeded` (`order.payment-succeeded`) | `AWAITING_PAYMENT` | → `PAID`, lưu mã giao dịch, phát `order.confirmed` |
| `payment.failed` (`order.payment-failed`) | đang xử lý | → `CANCELLED`, phát `order.cancelled` (inventory trả hàng) |

- **Idempotent**: mỗi handler khoá dòng đơn (`SELECT … FOR UPDATE`) và chỉ hành động khi trạng thái phù hợp;
  sự kiện gửi lại hoặc tới muộn bị bỏ qua (ghi log).
- **Thanh toán tới sau khi đơn đã huỷ**: đơn giữ `CANCELLED`; payment-service nhận `order.cancelled` và hoàn tiền.
- **Hết hạn**: `OrderExpiryJob` (mỗi `expiry-check-interval`, mặc định 1 phút) huỷ đơn `PENDING`/`AWAITING_PAYMENT`
  quá `payment-timeout` (mặc định 15 phút) để trả hàng đang giữ.
- **Transactional outbox** như inventory-service: publisher confirm, `FOR UPDATE SKIP LOCKED`, xoá sau 7 ngày.
- Lỗi xử lý: thử lại 3 lần rồi chuyển vào `<queue>.dlq`.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/orders` | đăng nhập | Đặt hàng (`201`; `200` khi lặp lại `Idempotency-Key`) |
| GET | `/api/orders?status=&page=&size=&sort=` | đăng nhập | Đơn của tôi (dạng tóm tắt) |
| GET | `/api/orders/{orderId}` | chủ đơn / ADMIN | Chi tiết + lịch sử trạng thái (đơn của người khác → `404`) |
| POST | `/api/orders/{orderId}/cancel` | chủ đơn / ADMIN | Huỷ, body tuỳ chọn `{"reason":"..."}` |
| GET | `/api/orders/admin?status=&userId=&keyword=` | ADMIN | Tìm đơn (keyword: mã đơn hoặc e-mail khách) |
| PATCH | `/api/orders/admin/{orderId}/status` | ADMIN | `{"status":"SHIPPED"\|"DELIVERED","note":"..."}` |

Ví dụ body đặt hàng:

```json
{
  "items": [{"productId": 7, "quantity": 1}, {"productId": 19, "quantity": 2}],
  "shippingAddress": {
    "recipientName": "Nguyễn Văn A", "phoneNumber": "+84901234567",
    "addressLine1": "12 Nguyễn Huệ", "ward": "Bến Nghé", "district": "Quận 1",
    "city": "TP. Hồ Chí Minh", "countryCode": "VN"
  },
  "paymentMethod": "CARD",
  "note": "Giao giờ hành chính"
}
```

`paymentMethod`: `COD`, `CARD`, `E_WALLET`. Mã đơn có dạng `ORD-20261021-7KQ2XM`.

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `ORDER_DB_PASSWORD`; tuỳ chọn: `ORDER_SERVICE_PORT`.
Giới hạn, phí ship, thời hạn thanh toán, outbox (`app.order.*`), timeout Feign và circuit breaker nằm trong
`config-repo/order-service.yml`.

## Test

```bash
cd backend
./mvnw -pl order-service test
```

- Unit test: `OrderTest`, `OrderServiceImplTest`, `OrderSagaServiceImplTest`
- Testcontainers: `OrderSagaIntegrationTest` — đặt đơn qua API, giả lập phản hồi của inventory/payment
  trên RabbitMQ thật, kiểm tra sự kiện đi ra qua outbox (thành công, thanh toán thất bại, idempotency, phân quyền)
