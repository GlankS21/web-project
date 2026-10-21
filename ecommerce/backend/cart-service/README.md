# cart-service

Giỏ hàng của khách đã đăng nhập. Port mặc định: **8085**. Database: `cart_db`.

## Hoạt động

- Mỗi khách một giỏ; mỗi dòng là một sản phẩm (unique giỏ + sản phẩm). Thêm sản phẩm đã có thì cộng dồn số lượng.
- Khi thêm/sửa dòng: sản phẩm phải đang bán (product-service) và số lượng không vượt tồn kho còn bán được
  (inventory-service). Nếu inventory-service không phản hồi, số lượng vẫn được nhận — Saga đặt hàng là
  bước kiểm tra cuối cùng.
- Dòng giỏ lưu ảnh chụp (tên, SKU, ảnh, giá) tại lần thay đổi gần nhất. Mỗi lần đọc giỏ, giá và tồn kho
  được lấy **lại** từ product-service/inventory-service:

| Trường dòng | Ý nghĩa |
|---|---|
| `unitPrice` | Giá hiện tại (giá đã lưu nếu product-service không phản hồi) |
| `priceChanged` | Giá khác với lúc khách thêm/sửa dòng |
| `availableQuantity` | Còn bán được, `null` nếu không rõ |
| `status` | `AVAILABLE`, `INSUFFICIENT_STOCK`, `OUT_OF_STOCK`, `UNAVAILABLE` (ngừng bán), `UNKNOWN` |

Cả giỏ có `subtotal`, `totalQuantity`, `checkoutReady` (không dòng nào bị chặn) và `pricesUpToDate`.

- Khi nhận `order.created` (queue `cart.order-created`), các sản phẩm vừa đặt bị xoá khỏi giỏ.
  Xoá nguyên dòng nên xử lý lặp lại vẫn an toàn.
- Feign gọi ngoài transaction; thay đổi giỏ khoá dòng giỏ (`SELECT … FOR UPDATE`) để hai request song song
  không ghi đè nhau.

## API

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/cart` | Giỏ hiện tại (đã định giá lại) |
| POST | `/api/cart/items` | `{"productId":7,"quantity":1}` — thêm/cộng dồn |
| PUT | `/api/cart/items/{productId}` | `{"quantity":3}` — đặt lại số lượng |
| DELETE | `/api/cart/items/{productId}` | Xoá một dòng |
| DELETE | `/api/cart` | Xoá cả giỏ |

Tất cả cần đăng nhập. Lỗi: sản phẩm ngừng bán / vượt tồn kho / giỏ đầy → `422`; vượt số lượng tối đa mỗi
dòng → `400`; product-service không phản hồi khi thêm/sửa → `503`.

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `CART_DB_PASSWORD`; tuỳ chọn: `CART_SERVICE_PORT`.
Giới hạn `app.cart.max-items` (50), `app.cart.max-quantity-per-item` (99), timeout Feign và circuit breaker
nằm trong `config-repo/cart-service.yml`.

## Test

```bash
cd backend
./mvnw -pl cart-service test
```

Testcontainers: `CartIntegrationTest` — API giỏ hàng với giới hạn tồn kho, định giá lại khi giá đổi,
hiển thị giỏ khi product/inventory-service ngừng, xoá sản phẩm khi nhận `order.created`.
