# common-lib

Общая библиотека (обычный jar, не приложение Spring Boot) для всех сервисов.

## Содержимое

| Package | Описание |
|---|---|
| `constant` | `HeaderConstants` (X-Correlation-Id, X-User-Id...), `RoleConstants`, `MessagingConstants` (exchange, routing key, очереди RabbitMQ), `LoggingConstants` |
| `dto` | `ApiResponse<T>`, `ErrorResponse`, `FieldViolation`, `PageResponse<T>` |
| `event` | Контракты событий RabbitMQ (record): `OrderCreatedEvent`, `StockReservedEvent`, `StockReservationFailedEvent`, `StockReleasedEvent`, `PaymentRequestedEvent`, `PaymentSucceededEvent`, `PaymentFailedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent`, `UserRegisteredEvent`, `ProductCreatedEvent` |
| `exception` | `ErrorCode` + бизнес-исключения: `ResourceNotFoundException`, `BadRequestException`, `ConflictException`, `BusinessException`, `UnauthorizedException`, `ForbiddenException`, `ServiceUnavailableException` |
| `web` | `AbstractGlobalExceptionHandler` — обработка ошибок с приведением к `ErrorResponse` |
| `logging` | Filter/interceptor/post-processor для передачи correlation id через HTTP, Feign, RabbitMQ |
| `autoconfigure` | Auto-configuration Spring Boot, включается автоматически по classpath |

Общие тексты сообщений об ошибках находятся в `src/main/resources/common-messages_ru.properties` (на русском);
каждый сервис дополняет их своим `messages_ru.properties`, локаль по умолчанию — ru-RU.

## Auto-configuration

Достаточно добавить зависимость `common-lib`, объявлять bean не нужно:

| Условие | Регистрируемый bean |
|---|---|
| Servlet-приложение | `CorrelationIdFilter` (выполняется до Spring Security) |
| Есть OpenFeign | `CorrelationIdFeignInterceptor` |
| Есть Spring AMQP | `Jackson2JsonMessageConverter` (trusted package `com.shop.common.event`), добавляет correlation id при публикации, восстанавливает его в MDC при потреблении |

Зависимости web/validation/amqp/feign/spring-data объявлены как `optional`; сервис, которому они нужны,
добавляет их в свой pom сам.

## Использование GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
    // добавьте собственные обработчики сервиса (AccessDeniedException, DataIntegrityViolationException...)
}
```

## Поток Saga (контракт событий)

```
order.created ──► inventory ──► stock.reserved ──► order ──► order.payment-requested ──► payment
              │          └────► stock.reservation-failed ──► order (CANCELLED)
              └──► cart (удаление заказанных товаров), notification
payment.succeeded ──► order (PAID) ──► order.confirmed ──► inventory (commit), notification
payment.failed    ──► order (CANCELLED) ──► order.cancelled ──► inventory (release), payment (возврат денег /
                                                              блокировка поздней оплаты), notification
```

Заказ, отменённый покупателем, администратором или задачей по истечении срока оплаты, тоже публикует `order.cancelled`, как показано выше.

Вне Saga: `user.registered` (auth → user, notification), `product.created` (product → inventory).

| Exchange | Producer | Routing key |
|---|---|---|
| `shop.order.events` | order-service | `order.created`, `order.payment-requested`, `order.confirmed`, `order.cancelled` |
| `shop.inventory.events` | inventory-service | `stock.reserved`, `stock.reservation-failed`, `stock.released` |
| `shop.payment.events` | payment-service | `payment.succeeded`, `payment.failed` |
| `shop.user.events` | auth-service | `user.registered` |
| `shop.product.events` | product-service | `product.created` |

Очереди (у каждой есть `<queue>.dlq`, имена находятся в `MessagingConstants`):

| Сервис | Очередь ← routing key |
|---|---|
| inventory-service | `inventory.order-created`, `inventory.order-confirmed`, `inventory.order-cancelled`, `inventory.product-created` |
| order-service | `order.stock-reserved`, `order.stock-reservation-failed`, `order.payment-succeeded`, `order.payment-failed` |
| payment-service | `payment.payment-requested` ← `order.payment-requested`; `payment.order-cancelled` ← `order.cancelled` |
| cart-service | `cart.order-created` ← `order.created` |
| user-service | `user.user-registered` ← `user.registered` |
| notification-service | `notification.order-events` ← `order.created`, `order.confirmed`, `order.cancelled`; `notification.user-events` ← `user.registered` |

notification-service использует по одной очереди на каждый exchange-источник; listener выбирает обработчик по типу события из заголовка `__TypeId__`.

## Сборка и тесты

```bash
cd backend
./mvnw -pl common-lib clean install
```
