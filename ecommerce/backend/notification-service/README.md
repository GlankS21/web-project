# notification-service

Отправляет письма при регистрации покупателя и по ходу выполнения заказа. Порт по умолчанию: **8088**. База данных: `notification_db`.

## Письма

| Событие (очередь) | Письмо |
|---|---|
| `user.registered` (`notification.user-events`) | Приветствие |
| `user.password-reset-requested` (`notification.user-events`) | Ссылка для сброса пароля |
| `order.created` (`notification.order-events`) | Заказ принят: список товаров, итоговая сумма |
| `order.confirmed` (`notification.order-events`) | Оплата прошла успешно, заказ подтверждён |
| `order.cancelled` (`notification.order-events`) | Заказ отменён, с указанием причины |

Содержимое — простой текст на русском языке, суммы в формате `25 140,00 ₽`, даты в часовом поясе Europe/Moscow.
Название магазина берётся из `SHOP_NAME`.

**Письмо для сброса пароля** (тип `PASSWORD_RESET`): текст на русском, ссылка
`<FRONTEND_URL>/reset-password?token=<URL-кодированный token>` (`FRONTEND_URL` по умолчанию `http://localhost:3000`,
завершающий `/` отбрасывается), срок действия в минутах, вычисляемый как `expiresAt − occurredAt`, и просьба
проигнорировать письмо, если сброс не запрашивался. Поскольку ссылка секретна, API (история покупателя и список
администратора) возвращает для этого типа `body = null`; содержимое всё равно сохраняется для повторной отправки.
При `MAIL_ENABLED=false` все письма (включая ссылку) записываются в лог, чтобы разработчик мог взять ссылку во время
разработки, — не используйте этот режим в production.

## Надёжность

- Каждое письмо сохраняется в таблицу `notifications` до отправки; `event_id` уникален, поэтому повторно доставленное
  событие не порождает второе письмо.
- Отправка SMTP выполняется **вне транзакции**. Ошибка отправки (SMTP недоступен) не портит сообщение: письмо помечается
  `FAILED` с текстом ошибки, а `NotificationRetryJob` повторяет отправку каждые `app.notification.retry.interval`
  (1 минута) до `max-attempts` (5) раз. После этого администратор может отправить письмо повторно вручную.
- Письма, оставшиеся в `PENDING` (сервис остановился между сохранением и отправкой), задача тоже отправляет повторно.
- Прочие ошибки обработки сообщений: 3 повторные попытки, затем `<queue>.dlq`.

## Среда разработки: Mailpit

Docker Compose запускает [Mailpit](https://mailpit.axllent.org/) — тестовый SMTP, перехватывающий все письма без
отправки наружу. Письма можно посмотреть на **http://localhost:8025**. При запуске сервиса вне Docker:
`docker compose up -d mailpit`, сервис отправляет на SMTP `localhost:1025` (порт `MAILPIT_SMTP_PORT`).

`MAIL_ENABLED=false` заменяет SMTP записью писем в лог.

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/notifications?page=&size=` | вход | Письма, отправленные мне, сначала новые |
| GET | `/api/notifications/admin?status=&type=` | ADMIN | Все письма, фильтр по `PENDING`/`SENT`/`FAILED` и типу |
| POST | `/api/notifications/admin/{id}/resend` | ADMIN | Повторная отправка письма |

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `NOTIFICATION_DB_PASSWORD`; необязательные:
`NOTIFICATION_SERVICE_PORT`, `MAIL_HOST` (по умолчанию `localhost`, Docker: `mailpit`), `MAIL_PORT` (1025),
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_ENABLED`, `MAIL_FROM`, `SHOP_NAME`,
`FRONTEND_URL` (по умолчанию `http://localhost:3000`, используется для ссылок в письмах).

## Тесты

```bash
cd backend
./mvnw -pl notification-service test
```

- Unit-тесты: `NotificationTemplatesTest` (содержимое, формат сумм, ссылка для сброса пароля: URL-кодирование, число минут),
  `NotificationMapperTest` (скрытие содержимого писем `PASSWORD_RESET`)
- Testcontainers: `NotificationIntegrationTest` — настоящие события в RabbitMQ, SMTP эмулируется mock-объектом:
  без дублей, повторная отправка при ошибке, ограничение числа попыток, повторная отправка администратором, письмо
  для сброса пароля (ссылка сохраняется, API не возвращает содержимое)
