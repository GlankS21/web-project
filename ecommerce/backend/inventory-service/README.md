# inventory-service

Tồn kho, giữ hàng (reservation) cho Saga đặt hàng và sổ biến động kho. Port mặc định: **8084**.
Database: `inventory_db`.

## Mô hình tồn kho

Mỗi sản phẩm có một bản ghi `inventory_items` (khoá chính = id sản phẩm bên product-service):

| Trường | Ý nghĩa |
|---|---|
| `quantityOnHand` | Hàng thực có trong kho |
| `quantityReserved` | Phần đang giữ cho đơn chờ thanh toán |
| `availableQuantity` | `onHand − reserved`, phần còn bán được |
| `lowStockThreshold` | Sắp hết hàng khi `available ≤ threshold` |

DB đảm bảo `reserved ≥ 0` và `onHand ≥ reserved`. Mọi thay đổi ghi vào sổ `stock_movements`
(`ADJUSTMENT`, `RESERVATION`, `RELEASE`, `SALE`) kèm số lượng sau thay đổi, mã đơn hoặc admin thực hiện.

Bản ghi tồn kho được tạo khi nhận `product.created`. Nếu sự kiện chưa tới, lần điều chỉnh kho đầu tiên
của admin sẽ tạo bản ghi (SKU được điền bổ sung khi sự kiện tới muộn).

## Saga (phía inventory)

| Nhận (queue) | Xử lý | Trả lời (exchange `shop.inventory.events`) |
|---|---|---|
| `order.created` (`inventory.order-created`) | Giữ **tất cả hoặc không** mặt hàng nào; dòng trùng sản phẩm được cộng dồn | `stock.reserved` hoặc `stock.reservation-failed` (kèm lý do từng sản phẩm thiếu) |
| `order.confirmed` (`inventory.order-confirmed`) | Hàng đã giữ rời kho: `onHand` và `reserved` cùng giảm | — |
| `order.cancelled` (`inventory.order-cancelled`) | Trả lại hàng đã giữ | `stock.released` (chỉ khi thực sự có hàng được trả) |
| `product.created` (`inventory.product-created`) | Mở bản ghi tồn kho rỗng | — |

Đảm bảo tính đúng:

- **Idempotent**: mỗi đơn có tối đa một `stock_reservations` (unique `order_id`). Sự kiện gửi lại không
  giữ hàng lần hai và không trả lời lần hai. Hủy đơn tới trước `order.created` để lại bản ghi `RELEASED`,
  nên `order.created` tới muộn không giữ hàng.
- **Đồng thời**: khoá dòng `SELECT … FOR UPDATE` theo thứ tự id sản phẩm (không deadlock); confirm/cancel
  của cùng một đơn được tuần tự hoá bằng khoá dòng reservation.
- **Không mất phản hồi (transactional outbox)**: sự kiện trả lời được ghi vào bảng `outbox_events` trong
  cùng transaction với thay đổi tồn kho. Một job (mỗi 1s) gửi lên RabbitMQ, chờ **publisher confirm** rồi
  mới đánh dấu đã gửi; nhiều instance chạy song song an toàn nhờ `FOR UPDATE SKIP LOCKED`. Giao nhận
  at-least-once — consumer phía order-service khử trùng theo `eventId`. Sự kiện đã gửi bị xoá sau 7 ngày.
- Lỗi xử lý: thử lại 3 lần rồi chuyển vào `<queue>.dlq`.

Hàng giữ cho đơn không bao giờ được thanh toán không bị kẹt: order-service huỷ đơn quá hạn thanh toán
(mặc định 15 phút) và hàng được trả lại qua `order.cancelled`.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/inventory/availability?productIds=1,2,3` | công khai | Số lượng còn bán được (tối đa 100 id; sản phẩm không có bản ghi = 0) |
| GET | `/api/inventory?lowStock=true&page=&size=&sort=` | ADMIN | Danh sách tồn kho, lọc sắp hết hàng |
| GET | `/api/inventory/{productId}` | ADMIN | Tồn kho của một sản phẩm |
| PUT | `/api/inventory/{productId}` | ADMIN | Kiểm kê: `{"quantityOnHand":42,"lowStockThreshold":5,"reason":"..."}` |
| POST | `/api/inventory/{productId}/adjustments` | ADMIN | Nhập/xuất: `{"quantityChange":20,"reason":"..."}` (≠ 0, không được bớt vào phần đang giữ) |
| GET | `/api/inventory/{productId}/movements` | ADMIN | Sổ biến động, mới nhất trước |
| GET | `/api/inventory/reservations/{orderId}` | ADMIN | Trạng thái giữ hàng của một đơn |

## Dữ liệu mẫu (Flyway)

- `V1__init_schema.sql`: `inventory_items`, `stock_reservations`, `stock_reservation_items`,
  `stock_movements`, `outbox_events`
- `V2__seed_stock.sql`: tồn kho cho 24 sản phẩm mẫu của product-service (cùng id, SKU) + bút toán số dư
  đầu kỳ. Cố ý có sản phẩm sắp hết (id 6, 10) và hết hàng (id 17).

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `INVENTORY_DB_PASSWORD`;
tuỳ chọn: `INVENTORY_SERVICE_PORT`. Cấu hình outbox (`app.inventory.outbox.*`) trong
`config-repo/inventory-service.yml`.

## Test

```bash
cd backend
./mvnw -pl inventory-service test
```

- Unit test: `InventoryItemTest`, `StockReservationServiceImplTest`, `InventoryServiceImplTest`
- Testcontainers: `InventorySagaIntegrationTest` — publish `order.created/confirmed/cancelled`,
  `product.created` thật lên RabbitMQ và chờ phản hồi đi ra qua outbox
