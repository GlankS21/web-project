# order-service

Оформление заказов, координация Saga оформления (хореография через RabbitMQ), история статусов заказа.
Порт по умолчанию: **8086**. База данных: `order_db`.

## Жизненный цикл заказа

```
PENDING ──stock.reserved──► AWAITING_PAYMENT ──payment.succeeded──► PAID ──admin──► SHIPPED ──admin──► DELIVERED
   │                              │
   └──────────────┬───────────────┘
                  ▼
              CANCELLED   (нет в наличии, оплата не прошла, отмена покупателем/администратором, истёк срок оплаты)
```

- Отменить можно только заказ **до оплаты** (`PENDING`, `AWAITING_PAYMENT`). У заказа в `PAID` товар уже покинул склад.
- Каждая смена статуса записывается в `order_status_history` (из → в, причина, исполнитель, время).
- Переход статуса в неверном порядке отклоняется (`422`).

## Оформление заказа

`POST /api/orders` с `Idempotency-Key` (необязательно, 8–100 символов `[A-Za-z0-9_-]`):

1. Строки с одинаковым товаром объединяются, проверяются лимиты (`app.order.max-items`, `max-quantity-per-item`).
2. **Текущая** цена берётся из product-service (`GET /api/products/by-ids`, Feign + circuit breaker). Цена, присланная
   клиентом, не используется. Товар больше не продаётся → `422`; product-service не отвечает → `503`.
3. Расчёт стоимости доставки: `shipping.flat-fee` (390 ₽), бесплатно от `shipping.free-threshold` (5 000 ₽) — считается от
   суммы **до** скидки.
4. Если передан `couponCode` (без учёта регистра, пробелы обрезаются, пустая строка = не используется): строка промокода
   блокируется (`PESSIMISTIC_WRITE`), проверяется, рассчитывается скидка, увеличивается `used_count`, записывается
   `coupon_redemptions`. Недействительный промокод → `422`, ничего не сохраняется.
5. Заказ сохраняется в `PENDING`, и `order.created` (сумма с учётом скидки) записывается в outbox **в той же транзакции**.

`totalAmount = subtotal − discountAmount + shippingFee`.

Повторная отправка с тем же `Idempotency-Key` возвращает уже созданный заказ (`200` вместо `201`), второй заказ не создаётся.
Feign вызывается вне транзакции, чтобы не удерживать соединение с БД во время ожидания сети.

Телефон получателя проверяется по шаблону `^\+7\d{10}$` (например, `+79161234567`), страна по умолчанию — `RU`.

## Saga (сторона order)

| Получение (очередь) | Условие | Результат |
|---|---|---|
| `stock.reserved` (`order.stock-reserved`) | `PENDING` | → `AWAITING_PAYMENT`, публикуется `order.payment-requested` |
| `stock.reservation-failed` (`order.stock-reservation-failed`) | `PENDING` | → `CANCELLED`, публикуется `order.cancelled` |
| `payment.succeeded` (`order.payment-succeeded`) | `AWAITING_PAYMENT` | → `PAID`, сохраняется идентификатор транзакции, публикуется `order.confirmed` |
| `payment.failed` (`order.payment-failed`) | в обработке | → `CANCELLED`, публикуется `order.cancelled` (inventory снимает резерв) |

Администратор переводит заказ в `DELIVERED` → публикуется `order.delivered` (`OrderDeliveredEvent`: заказ, покупатель,
строки заказа) через outbox в той же транзакции; product-service использует это событие, чтобы знать, кто что купил
(отзыв может оставить только покупатель).

Все пути отмены заказа (покупатель/администратор, неудача saga, истечение срока) проходят через `OrderLifecycle.cancel`,
где промокод заказа **возвращается** (`released_at`, уменьшение `used_count`). Доставленные/оплаченные заказы сохраняют промокод.

- **Идемпотентность**: каждый обработчик блокирует строку заказа (`SELECT … FOR UPDATE`) и действует только при
  подходящем статусе; повторные или опоздавшие события пропускаются (с записью в лог).
- **Оплата пришла после отмены заказа**: заказ остаётся `CANCELLED`; payment-service получает `order.cancelled` и возвращает деньги.
- **Истечение срока**: `OrderExpiryJob` (каждые `expiry-check-interval`, по умолчанию 1 минута) отменяет заказы
  `PENDING`/`AWAITING_PAYMENT` старше `payment-timeout` (по умолчанию 30 минут), чтобы снять резерв.
- **Transactional outbox**, как в inventory-service: publisher confirm, `FOR UPDATE SKIP LOCKED`, удаление через 7 дней.
- Ошибки обработки: 3 повторные попытки, затем перемещение в `<queue>.dlq`.

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| POST | `/api/orders` | вход | Оформление заказа (`201`; `200` при повторе `Idempotency-Key`) |
| GET | `/api/orders?status=&page=&size=&sort=` | вход | Мои заказы (в кратком виде) |
| GET | `/api/orders/{orderId}` | владелец / ADMIN | Подробности + история статусов (чужой заказ → `404`) |
| POST | `/api/orders/{orderId}/cancel` | владелец / ADMIN | Отмена, необязательное тело `{"reason":"..."}` |
| GET | `/api/orders/admin?status=&userId=&keyword=` | ADMIN | Поиск заказов (keyword: номер заказа или e-mail покупателя) |
| PATCH | `/api/orders/admin/{orderId}/status` | ADMIN | `{"status":"SHIPPED"\|"DELIVERED","note":"..."}` |
| POST | `/api/orders/coupons/preview` | вход | Проверка промокода для корзины `{"code":"WELCOME10","items":[...]}` (без резервирования) |
| GET | `/api/orders/admin/coupons?keyword=&active=&page=&size=&sort=` | ADMIN | Список промокодов (по умолчанию сначала новые) |
| GET | `/api/orders/admin/coupons/{id}` | ADMIN | Подробности промокода |
| POST | `/api/orders/admin/coupons` | ADMIN | Создание промокода (`201`; дубликат кода → `409`) |
| PUT | `/api/orders/admin/coupons/{id}` | ADMIN | Изменение промокода (`usedCount` сохраняется) |
| DELETE | `/api/orders/admin/coupons/{id}` | ADMIN | Удаление ни разу не использованного промокода (использованный → `422`, отключите `active`) |
| GET | `/api/orders/admin/stats?from=2026-01-01&to=2026-01-31&top=5` | ADMIN | Статистика для dashboard |

Пример тела запроса на оформление:

```json
{
  "items": [{"productId": 7, "quantity": 1}, {"productId": 19, "quantity": 2}],
  "shippingAddress": {
    "recipientName": "Иван Петров", "phoneNumber": "+79161234567",
    "addressLine1": "ул. Тверская, д. 1, кв. 10", "ward": "Тверской", "district": "ЦАО",
    "city": "Москва", "postalCode": "125009", "countryCode": "RU"
  },
  "paymentMethod": "ONLINE",
  "note": "Доставить в рабочее время"
}
```

`paymentMethod`: `COD` (оплата при получении), `ONLINE` (карта через виджет ЮKassa). Номер заказа имеет вид `ORD-20261021-7KQ2XM`.
Добавьте `"couponCode": "WELCOME10"` (необязательно), чтобы применить промокод; `OrderResponse` и `OrderSummaryResponse`
содержат `couponCode` и `discountAmount`.

## Промокоды

Таблица `coupons` (код хранится в ВЕРХНЕМ регистре, `^[A-Z0-9_-]{3,30}$`), `coupon_redemptions` (для каждого заказа
не более одного использования, `released_at` при отмене заказа); столбцы `orders.coupon_code`, `orders.discount_amount`
(миграция `V3__coupons.sql`).

- `PERCENTAGE`: `subtotal × value / 100` с округлением HALF_UP до 2 знаков, не более `maxDiscountAmount`.
- `FIXED_AMOUNT`: `value` (`maxDiscountAmount` игнорируется и очищается при сохранении).
- Скидка никогда не превышает сумму заказа минус 1 ₽: у заказа всегда остаётся сумма к оплате (платёжный шлюз отклоняет нулевую сумму).
- Порядок проверок (`422`): не существует / отключён → `coupon.error.not-found`; ещё не начал действовать →
  `coupon.error.not-started`; истёк → `coupon.error.expired`; исчерпан лимит (`usage_limit`, отменённые заказы не считаются) →
  `coupon.error.exhausted`; покупатель уже использовал `per_user_limit` → `coupon.error.user-limit`; сумма меньше
  `min_order_amount` → `coupon.error.min-amount`.
- Администратор: процент > 100 → `400 coupon.error.percentage-range`; `expiresAt` не позже `startsAt` →
  `400 coupon.error.period`; дубликат кода → `409 coupon.error.duplicate-code`.
- Демонстрационные данные: `WELCOME10` (10 %, не более 5 000 ₽), `SALE500` (500 ₽ для заказов от 5 000 ₽).

Тексты сообщений на русском находятся в `src/main/resources/messages_ru.properties` (общие — в
`common-messages_ru.properties` из common-lib).

## Статистика

`GET /api/orders/admin/stats` возвращает `OrderStatsResponse`: `from`, `to`, `currency`, `revenue`, `orderCount`,
`paidOrderCount`, `cancelledOrderCount`, `averageOrderValue`, `ordersByStatus` (все статусы), `daily`
(по одному элементу на каждый день периода, включая дни с нулём: `date`, `orders`, `paidOrders`, `revenue`), `topProducts`
(`productId`, `productName`, `sku`, `thumbnailUrl`, `quantity`, `revenue`).

- `from`/`to` — даты ISO, обе границы включительно; по умолчанию 30 дней до сегодняшнего дня; не более 366 дней
  (`400 stats.error.range`); `top` 1–20 (по умолчанию 5).
- Дни считаются по календарю часового пояса `app.order.time-zone` (по умолчанию `Europe/Moscow`).
- Число заказов = заказы, **созданные** в периоде. Выручка, оплаченные заказы, лидеры продаж = заказы с `paid_at` в периоде
  и статусом `PAID`/`SHIPPED`/`DELIVERED` (отменённые/возвращённые исключаются). `averageOrderValue = revenue / paidOrderCount`.
- Лидеры продаж сортируются по количеству, затем по выручке (сумма `line_total`); название/SKU/изображение берутся из самого нового заказа.
- Вычисляется агрегирующими запросами в PostgreSQL, без загрузки заказов в память.

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `ORDER_DB_PASSWORD`; необязательные: `ORDER_SERVICE_PORT`.
Лимиты, стоимость доставки, срок оплаты, outbox, валюта каталога (`app.order.currency`, по умолчанию `RUB`,
используется для статистики), часовой пояс статистики (`app.order.time-zone`, по умолчанию `Europe/Moscow`) (`app.order.*`),
timeout Feign и circuit breaker находятся в `config-repo/order-service.yml`.

## Тесты

```bash
cd backend
./mvnw -pl order-service test
```

- Unit-тесты: `OrderTest`, `CouponTest` (правила скидок, порядок проверок), `CouponLedgerTest` (использование/возврат промокода),
  `OrderServiceImplTest`, `OrderSagaServiceImplTest`, `OrderStatsServiceImplTest` (заполнение дней, среднее, период)
- Testcontainers: `OrderSagaIntegrationTest` — оформление заказа через API, эмуляция ответов inventory/payment
  в настоящем RabbitMQ, проверка событий, уходящих через outbox (успех, неудачная оплата, идемпотентность, права доступа,
  промокоды: preview, оформление, исчерпание лимита, возврат при отмене, CRUD администратора; `order.delivered`; статистика)
