# payment-service

Оплата заказов через **ЮKassa (API v3)** или её эмулятор, возврат денег при отмене оплаченного заказа,
хранение истории всех транзакций и webhook. Валюта: **RUB**. Порт по умолчанию: **8087**. База данных: `payment_db`.

## Провайдер: `PAYMENT_PROVIDER=yookassa|mock`

| | `yookassa` | `mock` (по умолчанию) |
|---|---|---|
| Создание payment | `POST /v3/payments`, `capture: true`, `confirmation: {type: "embedded", locale: "ru_RU"}`, заголовок `Idempotence-Key` | в памяти, id `mock-…` |
| Где покупатель вводит карту | **Виджет ЮKassa**, встроенный прямо в страницу оформления заказа (инициализируется `confirmation_token`), с 3-D Secure | форма карты на сайте (вместо виджета), результат по **таблице тестовых карт ЮKassa** |
| Как результат попадает в магазин | webhook (с повторной проверкой через API) + сайт вызывает `refresh`, когда виджет сообщает о завершении, + задача сверки | сразу при отправке формы |
| Возврат | `POST /v3/refunds` (`Idempotence-Key`) + webhook `refund.succeeded` | успешен сразу |

В режиме `yookassa` номер карты **никогда не проходит через бэкенд магазина**; форма карты режима `mock` принимает только
тестовые карты, не сохраняет и не логирует номер карты.

`YOOKASSA_SHOP_ID` / `YOOKASSA_SECRET_KEY` читаются только из переменных окружения (значений по умолчанию нет); если они
не заданы при `PAYMENT_PROVIDER=yookassa`, сервис не запускается.

## Модель данных

- `payments` — одна строка на заказ (unique `order_id` + блокировка строки ⇒ все обработчики идемпотентны): текущий статус,
  `confirmation_token` ожидающей попытки, число попыток, `failure_reason` (код `cancellation_details.reason`).
- `payment_transactions` — все операции у провайдера: `CHARGE` (каждая попытка, включая отклонённые карты) и `REFUND`;
  id строки — это `Idempotence-Key`, отправленный в ЮKassa, `provider_id` — id payment/refund в ЮKassa, плюс сумма,
  статус, причина отмены.
- `payment_notifications` — все webhook с **исходным телом**, статусом обработки (`PROCESSED`, `DUPLICATE`, `IGNORED`,
  `FAILED`) и связанной транзакцией.

Статусы payment: `PENDING` → `SUCCEEDED` | `FAILED` (можно повторить с другой картой) → `REFUND_PENDING` → `REFUNDED` |
`REFUND_FAILED`; `CANCELLED` (заказ отменён до списания денег или отменён заказ с COD).

## Поток

1. `order.payment-requested` → создание payment в ЮKassa (вне транзакции БД, идемпотентный ключ сохранён заранее) →
   сохранение `confirmation_token`. COD принимается сразу (`payment.succeeded`).
2. Фронтенд показывает виджет с токеном; виджет сообщает `success`/`fail` → фронтенд вызывает `POST /orders/{id}/refresh`.
3. Webhook `payment.succeeded` / `payment.canceled` / `refund.succeeded`: тело сохраняется, **объект перечитывается через API
   ЮKassa**, доверие только статусу из API. Повторно пришедшее уже обработанное финальное уведомление → помечается `DUPLICATE`, пропускается.
   Объект не существует в ЮKassa → `404`; ЮKassa не отвечает → `503` (ЮKassa отправит повторно).
4. `SUCCEEDED` → `payment.succeeded` (order → `PAID`, inventory списывает со склада).
   `CANCELED` → попытка `FAILED` с причиной; заказ остаётся `AWAITING_PAYMENT`, покупатель нажимает «Оплатить другой картой»
   (`POST /orders/{id}/retry`) — **новый заказ не создаётся**. Не более `max-attempts` (по умолчанию 5) попыток.
5. `order.cancelled` (отмена покупателем/администратором до доставки или истечение срока оплаты): деньги списаны → создаётся refund
   → `payment.refunded` (order сохраняет данные о возврате; inventory возвращает проданный товар на склад).
   Деньги списаны *после* отмены заказа → автоматический возврат.
6. `PaymentReconciliationJob` (каждую минуту) перечитывает транзакции, находящиеся в `PENDING` дольше 1 минуты, — это нужно,
   когда webhook не может дойти до локальной машины; спустя 24 часа транзакция отбрасывается (`expired`).

Причины отказа (`insufficient_funds`, `general_decline`, `card_expired`, `invalid_csc`, `3d_secure_failed`,
`fraud_suspected`, `call_issuer`, …) фронтенд показывает на русском (`messages/ru.json`, группа `paymentFailure`).

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/payments/orders/{orderId}` | владелец заказа / ADMIN | Платёж + история транзакций (другой пользователь → `404`) |
| POST | `/api/payments/orders/{orderId}/retry` | владелец заказа | Новая попытка (другая карта) после отказа; возвращает новый `confirmationToken` |
| POST | `/api/payments/orders/{orderId}/refresh` | владелец заказа / ADMIN | Запрос статуса ожидающих транзакций из ЮKassa |
| GET | `/api/payments?status=&page=&size=&sort=` | ADMIN | Список платежей |
| POST | `/api/payments/webhooks/yookassa` | публично | Webhook ЮKassa (только при `yookassa`) |
| POST | `/api/payments/mock/{paymentId}/pay` | владелец заказа | Эмулированная форма карты `{cardNumber, expiry: "MM/YY", cvc, threeDSecureCode?}` (только при `mock`) |

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `PAYMENT_DB_PASSWORD`, `PAYMENT_PROVIDER`;
для ЮKassa: `YOOKASSA_SHOP_ID`, `YOOKASSA_SECRET_KEY`. Инструкция по созданию тестового магазина, webhook через ngrok и таблица
тестовых карт: см. [корневой README](../../README.md#оплата-через-юkassa-тестовый-режим).

## Тесты

```bash
cd backend
./mvnw -pl payment-service verify
```

- Unit: `PaymentServiceImplTest` (списание, проверка webhook, защита от дублей, повторные попытки, возврат, сверка),
  `YooKassaPaymentGatewayTest` (реальные request/response API v3 через `MockRestServiceServer`),
  `MockPaymentGatewayTest` (таблица тестовых карт, 3-D Secure)
- Testcontainers: `PaymentSagaIntegrationTest` (отклонённая карта → другая карта → 3-D Secure → отмена → возврат),
  `YooKassaWebhookIntegrationTest` (webhook без токена, доверие только API, исходное тело сохраняется, дубликаты уведомлений пропускаются)
