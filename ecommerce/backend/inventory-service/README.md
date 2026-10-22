# inventory-service

Остатки, резервирование товара (reservation) для Saga оформления заказа и журнал движения склада. Порт по умолчанию: **8084**.
База данных: `inventory_db`.

## Модель остатков

У каждого товара есть запись `inventory_items` (первичный ключ = id товара в product-service):

| Поле | Значение |
|---|---|
| `quantityOnHand` | Фактическое количество на складе |
| `quantityReserved` | Часть, зарезервированная под заказы, ожидающие оплаты |
| `availableQuantity` | `onHand − reserved`, доступно для продажи |
| `lowStockThreshold` | Товар заканчивается, когда `available ≤ threshold` |

БД гарантирует `reserved ≥ 0` и `onHand ≥ reserved`. Каждое изменение записывается в журнал `stock_movements`
(`ADJUSTMENT`, `RESERVATION`, `RELEASE`, `SALE`) с количеством после изменения, номером заказа или администратором,
выполнившим операцию.

Запись об остатке создаётся при получении `product.created`. Если событие ещё не пришло, первая корректировка остатка
администратором создаёт запись (SKU дозаполняется, когда событие приходит с опозданием).

## Saga (сторона inventory)

| Получение (очередь) | Обработка | Ответ (exchange `shop.inventory.events`) |
|---|---|---|
| `order.created` (`inventory.order-created`) | Резервирует **все позиции или ни одной**; строки с одинаковым товаром суммируются | `stock.reserved` или `stock.reservation-failed` (с причиной по каждому недостающему товару) |
| `order.confirmed` (`inventory.order-confirmed`) | Зарезервированный товар покидает склад: `onHand` и `reserved` уменьшаются вместе | — |
| `order.cancelled` (`inventory.order-cancelled`) | Снимает резерв | `stock.released` (только если резерв действительно был снят) |
| `product.created` (`inventory.product-created`) | Создаёт пустую запись об остатке | — |

Гарантии корректности:

- **Идемпотентность**: у каждого заказа не более одной `stock_reservations` (unique `order_id`). Повторно доставленное
  событие не резервирует товар второй раз и не отвечает второй раз. Отмена заказа, пришедшая раньше `order.created`,
  оставляет запись `RELEASED`, поэтому опоздавший `order.created` товар не резервирует.
- **Конкурентность**: блокировка строк `SELECT … FOR UPDATE` в порядке id товаров (без deadlock); confirm/cancel
  одного и того же заказа выполняются последовательно благодаря блокировке строки reservation.
- **Ответы не теряются (transactional outbox)**: ответное событие записывается в таблицу `outbox_events` в той же
  транзакции, что и изменение остатков. Задача (каждую 1 с) отправляет его в RabbitMQ, ждёт **publisher confirm** и
  только затем помечает как отправленное; несколько экземпляров безопасно работают параллельно благодаря
  `FOR UPDATE SKIP LOCKED`. Доставка at-least-once — консьюмер на стороне order-service устраняет дубликаты по `eventId`.
  Отправленные события удаляются через 7 дней.
- Ошибки обработки: 3 повторные попытки, затем перемещение в `<queue>.dlq`.

Резерв под заказ, который так и не был оплачен, не зависает: order-service отменяет заказы с истёкшим сроком оплаты
(по умолчанию 15 минут), и резерв снимается через `order.cancelled`.

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/inventory/availability?productIds=1,2,3` | публично | Доступное для продажи количество (не более 100 id; товар без записи = 0) |
| GET | `/api/inventory?lowStock=true&page=&size=&sort=` | ADMIN | Список остатков, фильтр заканчивающихся товаров |
| GET | `/api/inventory/{productId}` | ADMIN | Остаток одного товара |
| PUT | `/api/inventory/{productId}` | ADMIN | Инвентаризация: `{"quantityOnHand":42,"lowStockThreshold":5,"reason":"..."}` |
| POST | `/api/inventory/{productId}/adjustments` | ADMIN | Приход/расход: `{"quantityChange":20,"reason":"..."}` (≠ 0, нельзя уменьшить ниже зарезервированного) |
| GET | `/api/inventory/{productId}/movements` | ADMIN | Журнал движения, сначала новые |
| GET | `/api/inventory/reservations/{orderId}` | ADMIN | Состояние резерва по заказу |

## Начальные данные (Flyway)

- `V1__init_schema.sql`: `inventory_items`, `stock_reservations`, `stock_reservation_items`,
  `stock_movements`, `outbox_events`
- `V2__seed_stock.sql`: остатки для 24 демонстрационных товаров product-service (те же id, SKU) + проводки начального
  сальдо. Намеренно есть заканчивающиеся товары (id 6, 10) и товар, которого нет в наличии (id 17).

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `INVENTORY_DB_PASSWORD`;
необязательные: `INVENTORY_SERVICE_PORT`. Конфигурация outbox (`app.inventory.outbox.*`) — в
`config-repo/inventory-service.yml`.

## Тесты

```bash
cd backend
./mvnw -pl inventory-service test
```

- Unit-тесты: `InventoryItemTest`, `StockReservationServiceImplTest`, `InventoryServiceImplTest`
- Testcontainers: `InventorySagaIntegrationTest` — публикует настоящие `order.created/confirmed/cancelled`,
  `product.created` в RabbitMQ и ждёт ответов, уходящих через outbox
