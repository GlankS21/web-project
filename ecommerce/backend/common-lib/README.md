# common-lib

Thư viện dùng chung (jar thường, không phải ứng dụng Spring Boot) cho mọi service.

## Nội dung

| Package | Mô tả |
|---|---|
| `constant` | `HeaderConstants` (X-Correlation-Id, X-User-Id...), `RoleConstants`, `MessagingConstants` (exchange, routing key, queue RabbitMQ), `LoggingConstants` |
| `dto` | `ApiResponse<T>`, `ErrorResponse`, `FieldViolation`, `PageResponse<T>` |
| `event` | Hợp đồng sự kiện RabbitMQ (record): `OrderCreatedEvent`, `StockReservedEvent`, `StockReservationFailedEvent`, `StockReleasedEvent`, `PaymentRequestedEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`, `UserRegisteredEvent`, `ProductCreatedEvent` |
| `exception` | `ErrorCode` + các exception nghiệp vụ: `ResourceNotFoundException`, `BadRequestException`, `ConflictException`, `BusinessException`, `UnauthorizedException`, `ForbiddenException`, `ServiceUnavailableException` |
| `web` | `AbstractGlobalExceptionHandler` — xử lý lỗi chuẩn hoá thành `ErrorResponse` |
| `logging` | Filter/interceptor/post-processor truyền correlation id qua HTTP, Feign, RabbitMQ |
| `autoconfigure` | Spring Boot auto-configuration, tự kích hoạt theo classpath |

## Auto-configuration

Chỉ cần thêm dependency `common-lib`, không phải khai báo bean:

| Điều kiện | Bean được đăng ký |
|---|---|
| Ứng dụng servlet | `CorrelationIdFilter` (chạy trước Spring Security) |
| Có OpenFeign | `CorrelationIdFeignInterceptor` |
| Có Spring AMQP | `Jackson2JsonMessageConverter` (trusted package `com.shop.common.event`), gắn correlation id khi publish, khôi phục vào MDC khi consume |

Các dependency web/validation/amqp/feign/spring-data được khai báo `optional`, service nào cần
thì tự thêm vào pom của mình.

## Dùng GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
    // thêm handler riêng của service (AccessDeniedException, DataIntegrityViolationException...)
}
```

## Luồng Saga (hợp đồng sự kiện)

```
order.created ──► inventory ──► stock.reserved ──► order ──► order.payment-requested ──► payment
              │          └────► stock.reservation-failed ──► order (CANCELLED)
              └──► cart (xoá sản phẩm đã đặt), notification
payment.succeeded ──► order (PAID) ──► order.confirmed ──► inventory (commit), notification
payment.failed    ──► order (CANCELLED) ──► order.cancelled ──► inventory (release), payment (hoàn tiền /
                                                              chặn thanh toán muộn), notification
```

Đơn bị huỷ bởi khách, admin hay job hết hạn thanh toán cũng phát `order.cancelled` như trên.

Ngoài Saga: `user.registered` (auth → user, notification), `product.created` (product → inventory).

| Exchange | Producer | Routing key |
|---|---|---|
| `shop.order.events` | order-service | `order.created`, `order.payment-requested`, `order.confirmed`, `order.cancelled` |
| `shop.inventory.events` | inventory-service | `stock.reserved`, `stock.reservation-failed`, `stock.released` |
| `shop.payment.events` | payment-service | `payment.succeeded`, `payment.failed` |
| `shop.user.events` | auth-service | `user.registered` |
| `shop.product.events` | product-service | `product.created` |

Queue (mỗi queue có `<queue>.dlq`, tên nằm trong `MessagingConstants`):

| Service | Queue ← routing key |
|---|---|
| inventory-service | `inventory.order-created`, `inventory.order-confirmed`, `inventory.order-cancelled`, `inventory.product-created` |
| order-service | `order.stock-reserved`, `order.stock-reservation-failed`, `order.payment-succeeded`, `order.payment-failed` |
| payment-service | `payment.payment-requested` ← `order.payment-requested`; `payment.order-cancelled` ← `order.cancelled` |
| cart-service | `cart.order-created` ← `order.created` |
| user-service | `user.user-registered` ← `user.registered` |
| notification-service | `notification.order-events` ← `order.created`, `order.confirmed`, `order.cancelled`; `notification.user-events` ← `user.registered` |

notification-service dùng một queue cho mỗi exchange nguồn; listener chọn handler theo kiểu sự kiện trong header `__TypeId__`.

## Build & test

```bash
cd backend
./mvnw -pl common-lib clean install
```
