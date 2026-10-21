# payment-service

Thanh toán đơn hàng (cổng thanh toán giả lập) và hoàn tiền khi đơn bị huỷ. Port mặc định: **8087**.
Database: `payment_db`.

## Cổng thanh toán giả lập

`SimulatedPaymentGateway` thay cho nhà cung cấp thật, đủ xác định để thử cả hai nhánh Saga:

| Phương thức | Kết quả |
|---|---|
| `COD` | Luôn chấp nhận (mã `COD-…`), tiền thu khi giao hàng |
| `CARD`, `E_WALLET` | Từ chối nếu số tiền > `PAYMENT_SIMULATOR_MAX_AMOUNT` (mặc định 50.000.000) |
| | Từ chối ngẫu nhiên với tỉ lệ `PAYMENT_SIMULATOR_FAILURE_RATE` (0.0–1.0, mặc định 0) |
| | Còn lại: chấp nhận (mã `TXN-…`) |

Muốn thấy nhánh thất bại: đặt đơn thẻ trên 50 triệu, hoặc đặt `PAYMENT_SIMULATOR_FAILURE_RATE=1.0`.
Thay bằng cổng thật chỉ cần một implementation khác của `PaymentGateway` (dùng `orderId` làm idempotency key
phía nhà cung cấp).

## Saga (phía payment)

| Nhận (queue) | Xử lý | Trả lời (exchange `shop.payment.events`) |
|---|---|---|
| `order.payment-requested` (`payment.payment-requested`) | Thu tiền, lưu `SUCCEEDED`/`FAILED` | `payment.succeeded` / `payment.failed` |
| `order.cancelled` (`payment.order-cancelled`) | Đã thu → hoàn tiền (`REFUNDED`); chưa có thanh toán → lưu bản ghi chặn `CANCELLED` | — |

Đảm bảo tính đúng:

- **Mỗi đơn tối đa một thanh toán**: unique `order_id` + khoá dòng. Yêu cầu gửi lại không thu tiền lần hai.
- **Huỷ tới trước yêu cầu thanh toán** (đơn hết hạn trong lúc chờ): bản ghi `CANCELLED` khiến yêu cầu tới muộn
  bị bỏ qua — đơn đã huỷ không bao giờ bị thu tiền.
- **Thanh toán xong nhưng đơn đã huỷ**: order-service giữ đơn ở `CANCELLED` và phát `order.cancelled`,
  payment-service hoàn tiền.
- Kết quả gửi qua **transactional outbox** (publisher confirm), như inventory-service và order-service.
- Lỗi xử lý: thử lại 3 lần rồi chuyển vào `<queue>.dlq`.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/payments/orders/{orderId}` | chủ đơn / ADMIN | Thanh toán của một đơn (người khác → `404`) |
| GET | `/api/payments?status=&page=&size=&sort=` | ADMIN | Danh sách thanh toán, lọc theo trạng thái |

Trạng thái: `SUCCEEDED`, `FAILED`, `REFUNDED`, `CANCELLED` (bản ghi chặn, không có số tiền).

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `PAYMENT_DB_PASSWORD`; tuỳ chọn:
`PAYMENT_SERVICE_PORT`, `PAYMENT_SIMULATOR_MAX_AMOUNT`, `PAYMENT_SIMULATOR_FAILURE_RATE`.

## Test

```bash
cd backend
./mvnw -pl payment-service test
```

- Unit test: `SimulatedPaymentGatewayTest`, `PaymentServiceImplTest`
- Testcontainers: `PaymentSagaIntegrationTest` — thu tiền một lần dù yêu cầu bị gửi lại, hoàn tiền khi huỷ,
  từ chối vượt hạn mức, chặn thanh toán của đơn đã huỷ
