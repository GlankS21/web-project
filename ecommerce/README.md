# E-commerce Microservices Platform

Интернет-магазин на микросервисной архитектуре: Java 21, Spring Boot 3.5, Spring Cloud 2025.0,
PostgreSQL (database-per-service), RabbitMQ, Next.js.

## Возможности

- **Покупатель**: просмотр и поиск товаров (фильтры по категории, бренду, цене), корзина с пересчётом цен
  в реальном времени, оформление заказа с оплатой при получении (COD) / картой / электронным кошельком,
  отслеживание и отмена заказа, адресная книга, письма-подтверждения.
- **Администратор**: товары и категории, складской учёт (приход/расход, инвентаризация, предупреждения
  о заканчивающемся товаре, журнал движения), обработка заказов (доставка, отмена), учётные записи и
  права доступа, контроль отправки писем.
- **Техническая часть**: 8 бизнес-сервисов, у каждого своя база данных; Saga-хореография через RabbitMQ с
  transactional outbox, идемпотентными консьюмерами и DLQ; API gateway с JWT, rate limit, circuit breaker;
  фронтенд по схеме Backend-for-Frontend (токены в cookie HttpOnly); объединённый Swagger на gateway;
  модульные и интеграционные тесты с Testcontainers + smoke-тест всей системы; CI.
- **Локализация**: весь интерфейс и все сообщения API на русском языке (next-intl `messages/ru.json` во
  фронтенде; `messages_ru.properties` в каждом сервисе + `common-messages_ru.properties` в common-lib,
  локаль по умолчанию ru-RU, сообщения Bean Validation на русском), часовой пояс Europe/Moscow, валюта RUB,
  телефоны в формате `+7XXXXXXXXXX`, письма на русском.

## Документация

| Документ | Содержание |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Схема системы, данные, Saga, безопасность, наблюдаемость, ограничения |
| [docs/api.md](docs/api.md) | Список эндпоинтов, права доступа, коды ошибок (Swagger UI: http://localhost:8080/swagger-ui.html) |
| [docs/testing.md](docs/testing.md) | Стратегия тестирования, запуск, покрытие, smoke-тест, CI |
| [frontend/README.md](frontend/README.md) | Сайт, BFF, cookie, обновление токенов |
| `backend/<service>/README.md` | Подробности по каждому сервису: API, события, конфигурация |

## Ход работ

| Этап | Содержание | Статус |
|---|---|---|
| 1 | Parent POM, common-lib, config-server, discovery-server, Docker Compose (PostgreSQL, RabbitMQ) | ✅ Готово |
| 2 | api-gateway, auth-service, user-service | ✅ Готово |
| 3 | product-service, inventory-service + демонстрационные данные | ✅ Готово |
| 4 | cart, order, payment, notification + Saga через RabbitMQ | ✅ Готово |
| 5 | Фронтенд Next.js (витрина + панель администратора) | ✅ Готово |
| 6 | Тесты, Swagger, полная документация | ✅ Готово |

## Структура каталогов

```
ecommerce/
├── docker-compose.yml
├── .env.example                  # шаблон переменных окружения (секретов) — скопировать в .env
├── .github/workflows/ci.yml      # CI: сборка + тесты бэкенда, фронтенда, проверка compose
├── docs/                         # архитектура, API, тестирование
├── scripts/smoke-test.sh         # end-to-end проверка работающей системы
├── infrastructure/
│   └── postgres/init/            # скрипт создания отдельной базы данных + роли для каждого сервиса
├── backend/
│   ├── pom.xml                   # parent POM (multi-module)
│   ├── mvnw, mvnw.cmd            # Maven Wrapper (устанавливать Maven не нужно)
│   ├── common-lib/               # DTO, события, исключения, константы, correlation id
│   ├── config-server/            # Spring Cloud Config Server (+ config-repo)
│   ├── discovery-server/         # Eureka
│   ├── api-gateway/              # единая точка входа: маршруты, JWT, CORS, rate limit
│   ├── auth-service/             # регистрация, вход, токены, права доступа
│   ├── user-service/             # профиль, адресная книга
│   ├── product-service/          # категории, товары, изображения, поиск
│   ├── inventory-service/        # остатки, резервирование для Saga, журнал движения склада
│   ├── cart-service/             # корзина, пересчёт по каталогу и остаткам
│   ├── order-service/            # оформление заказа, координация Saga, жизненный цикл заказа
│   ├── payment-service/          # оплата (эмулятор шлюза), возвраты
│   └── notification-service/     # письма о регистрации и статусе заказа
└── frontend/                     # Next.js: витрина, личный кабинет, администрирование (BFF к gateway)
```

## Требования

- Docker Desktop (Compose v2)
- JDK 21+ (нужен только для запуска/тестирования сервисов вне Docker)
- Node.js 22+ (нужен только для запуска фронтенда вне Docker)

## Запуск через Docker Compose

```bash
cp .env.example .env              # Windows PowerShell: Copy-Item .env.example .env
# заменить все значения change-me в .env; JWT_SECRET: openssl rand -base64 64 | tr -d '\r\n'
docker compose up -d --build --wait
docker compose ps                 # все в состоянии healthy
```

| Компонент | URL | Вход |
|---|---|---|
| **Сайт (frontend)** | http://localhost:3000 | покупатели регистрируются сами; администратор: `ADMIN_EMAIL` / `ADMIN_PASSWORD` → меню учётной записи → *Администрирование* |
| **API Gateway** | http://localhost:8080 | JWT (вход через `/api/auth/login`) |
| Swagger UI (объединённый) | http://localhost:8080/swagger-ui.html | кнопка *Authorize* → вставить access token |
| Config Server | http://localhost:8888/application/default | `CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD` |
| Eureka Dashboard | http://localhost:8761 | `EUREKA_USERNAME` / `EUREKA_PASSWORD` |
| RabbitMQ Management | http://localhost:15672 | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` |
| Mailpit (тестовый почтовый ящик) | http://localhost:8025 | — (все письма notification-service) |
| PostgreSQL | localhost:5432 | у каждого сервиса своя роль, см. ниже |

Бизнес-сервисы не публикуют порты наружу; доступ к ним только через gateway. Сайт обращается к gateway
на стороне сервера (модель Backend-for-Frontend, токены лежат в cookie HttpOnly); подробности в
[frontend/README](frontend/README.md).

| Сервис | Внутренний порт | Путь через gateway | Документация |
|---|---|---|---|
| auth-service | 8081 | `/api/auth/**` | [README](backend/auth-service/README.md) |
| user-service | 8082 | `/api/users/**` | [README](backend/user-service/README.md) |
| product-service | 8083 | `/api/products/**`, `/api/categories/**` | [README](backend/product-service/README.md) |
| inventory-service | 8084 | `/api/inventory/**` | [README](backend/inventory-service/README.md) |
| cart-service | 8085 | `/api/cart/**` | [README](backend/cart-service/README.md) |
| order-service | 8086 | `/api/orders/**` | [README](backend/order-service/README.md) |
| payment-service | 8087 | `/api/payments/**` | [README](backend/payment-service/README.md) |
| notification-service | 8088 | `/api/notifications/**` | [README](backend/notification-service/README.md) |

### Быстрая проверка

```bash
# вход администратора (ADMIN_EMAIL / ADMIN_PASSWORD из .env)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@shop.local","password":"<ADMIN_PASSWORD>"}'

# регистрация покупателя
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"ivan@example.com","password":"Secret123","firstName":"Иван","lastName":"Петров"}'

# вызов API, требующего входа
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"

# просмотр каталога (публично): сначала самые дешёвые ноутбуки, и доступные остатки
curl -s "http://localhost:8080/api/products?categoryId=4&sort=price,asc&size=5"
curl -s "http://localhost:8080/api/inventory/availability?productIds=7,10,17"

# администратор: оприходовать ещё 20 единиц товара id 10
curl -s -X POST http://localhost:8080/api/inventory/10/adjustments   -H "Authorization: Bearer <adminAccessToken>" -H "Content-Type: application/json"   -d '{"quantityChange":20,"reason":"Поступление товара"}'
```

### Оформление заказа (Saga)

```bash
TOKEN="<accessToken покупателя>"
curl -s -X POST http://localhost:8080/api/cart/items -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json" -d '{"productId":7,"quantity":1}'
curl -s -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json" -H "Idempotency-Key: checkout-0001"   -d '{"items":[{"productId":7,"quantity":1}],"paymentMethod":"COD",
       "shippingAddress":{"recipientName":"Иван Петров","phoneNumber":"+79161234567",
       "addressLine1":"ул. Тверская, д. 1, кв. 10","city":"Москва","postalCode":"125009","countryCode":"RU"}}'
# через несколько секунд (каждый шаг через outbox ~1 с): PAID (COD принимается сразу); письмо появится в Mailpit
# заказ ONLINE: AWAITING_PAYMENT, пока покупатель не оплатит картой на сайте (см. «Оплата через ЮKassa»)
curl -s http://localhost:8080/api/orders/<orderId> -H "Authorization: Bearer $TOKEN"
```

Поток событий (хореография, без центрального сервиса-координатора):

```
order-service ──order.created──► inventory-service ──stock.reserved──► order-service
     │                                └──stock.reservation-failed──► order-service ──► CANCELLED
     ├──order.created──► cart-service (удаляет заказанные товары), notification-service
order-service ──order.payment-requested──► payment-service ──(покупатель платит картой: виджет ЮKassa)
                                                 ├──payment.succeeded──► order-service ──► PAID
                                                 └──payment.failed────► order-service ──► CANCELLED (некорректный запрос)
PAID      ──order.confirmed──► inventory (списание со склада), notification
CANCELLED ──order.cancelled──► inventory (снятие резерва / возврат проданного товара на склад), payment (возврат денег, если списаны),
                               notification
payment-service ──payment.refunded──► order-service (сохраняет идентификатор и время возврата)
```

- Все события Saga проходят через **transactional outbox** + publisher confirm: события не теряются при сбое RabbitMQ.
- Консьюмеры идемпотентны (доставка at-least-once); при ошибке 3 повторные попытки, затем сообщение уходит в `<queue>.dlq`.
- Заказ, не оплаченный в течение 30 минут, отменяется автоматически, резерв снимается. Отклонённая карта не отменяет
  заказ: покупатель может попробовать другую карту для того же заказа.
- Оплаченный заказ можно отменить до момента доставки (покупателю или администратору): деньги возвращаются, товар
  возвращается на склад.
- Проверка неуспешных веток: отклоняемая тестовая карта (например, `5555 5555 5555 4600`) или товар id 17 (нет в наличии).

### Демонстрационные данные

Flyway загружает их автоматически при первом запуске сервиса:

| Сервис | Данные |
|---|---|
| auth-service | роли `CUSTOMER`, `ADMIN`; учётная запись администратора (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) |
| user-service | профиль администратора |
| product-service | 12 категорий (4 корневые × 2 дочерние), 24 товара электроники с ценами в RUB (22 в продаже, 1 черновик, 1 снят с продажи), изображения-заглушки; названия и описания на русском (отдельная миграция Flyway переводит демонстрационный каталог, slug — латинская транслитерация кириллических названий) |
| inventory-service | остатки для 24 товаров выше (заканчиваются: id 6, 10; нет в наличии: id 17) |

### Database-per-service

Один экземпляр PostgreSQL содержит отдельную базу данных для каждого сервиса; каждая база принадлежит своей
роли, и эта роль не имеет доступа к другим базам:

| Сервис | База данных | Роль | Пароль (.env) |
|---|---|---|---|
| auth-service | `auth_db` | `auth_user` | `AUTH_DB_PASSWORD` |
| user-service | `user_db` | `user_user` | `USER_DB_PASSWORD` |
| product-service | `product_db` | `product_user` | `PRODUCT_DB_PASSWORD` |
| inventory-service | `inventory_db` | `inventory_user` | `INVENTORY_DB_PASSWORD` |
| cart-service | `cart_db` | `cart_user` | `CART_DB_PASSWORD` |
| order-service | `order_db` | `order_user` | `ORDER_DB_PASSWORD` |
| payment-service | `payment_db` | `payment_user` | `PAYMENT_DB_PASSWORD` |
| notification-service | `notification_db` | `notification_user` | `NOTIFICATION_DB_PASSWORD` |

Скрипт инициализации выполняется только **при первом запуске**, когда volume пуст. Если позже изменить пароли
в `.env`, нужно пересоздать volume: `docker compose down -v` (удаляет все данные).

## Оплата через ЮKassa (тестовый режим)

Оплата картой использует **Виджет ЮKassa**: покупатель вводит данные карты прямо на странице оформления заказа,
в форме ЮKassa (включая 3-D Secure); номер карты не проходит через бэкенд магазина. Провайдер выбирается
переменной `PAYMENT_PROVIDER` в `.env`:

| `PAYMENT_PROVIDER` | Когда использовать |
|---|---|
| `mock` (по умолчанию) | Нет учётной записи ЮKassa: страница оформления показывает эмулированную форму карты, результат определяется таблицей тестовых карт ниже |
| `yookassa` | Есть тестовый магазин ЮKassa: настоящий виджет, настоящие webhook |

### 1. Создание тестового магазина

1. Зарегистрируйтесь на <https://yookassa.ru> и войдите в [личный кабинет](https://yookassa.ru/my).
2. Создайте **тестовый магазин** (переключатель магазинов в верхнем углу → «Тестовый магазин»). Для тестового
   магазина не нужен договор, реальные деньги не списываются.
3. В тестовом магазине: **Интеграция → Ключи API** → «Выпустить секретный ключ». Запишите:
   - `shopId` (число, например `123456`) → `YOOKASSA_SHOP_ID`
   - секретный ключ (начинается с `test_`) → `YOOKASSA_SECRET_KEY`
4. В `.env` (этот файл не коммитится):

   ```bash
   PAYMENT_PROVIDER=yookassa
   YOOKASSA_SHOP_ID=123456
   YOOKASSA_SECRET_KEY=test_xxxxxxxxxxxxxxxxxxxxxxxx
   ```

5. `docker compose up -d --build payment-service frontend`. Сервис не запустится, если не задан один из двух ключей.

### 2. Получение webhook при локальном запуске (ngrok)

ЮKassa нужен публичный HTTPS-адрес для отправки HTTP-уведомлений. Webhook приходят в API gateway (порт 8080):

```bash
ngrok http 8080
# → Forwarding https://ab12-34-56.ngrok-free.app -> http://localhost:8080
```

В личном кабинете тестового магазина: **Интеграция → HTTP-уведомления**:

- URL: `https://<адрес-ngrok>/api/payments/webhooks/yookassa`
- События: `payment.succeeded`, `payment.canceled`, `refund.succeeded`

Эндпоинт не требует токена: payment-service сохраняет тело запроса как есть в `payment_notifications`,
**перечитывает payment/refund через API ЮKassa** и доверяет только статусу из API; повторно пришедшее уже
обработанное уведомление помечается `DUPLICATE` и пропускается.
Оплата работает и без ngrok: когда виджет сообщает о завершении, страница оформления вызывает `refresh` (читает
статус из API), а задача сверки каждую минуту проверяет зависшие транзакции.

### 3. Тестовые карты

Срок действия: любая дата в будущем (например, `12/30`); CVC: любые 3 цифры; код 3-D Secure: любой
(например, `123`). Эта же таблица действует для тестового магазина ЮKassa и для режима `mock`.

| Результат | Mastercard | Visa | Мир |
|---|---|---|---|
| Успешно, с 3-D Secure | `5555 5555 5555 4477` | `4793 1281 6164 4804` | `2200 0000 0000 0004` |
| Успешно, без 3-D Secure | `5555 5555 5555 4444` | `4111 1111 1111 1111` | `2202 4743 0132 2987` |
| `insufficient_funds` — недостаточно средств | `5555 5555 5555 4600` | `4562 2655 8771 2390` | `2200 0000 0000 0053` |
| `general_decline` — отказ банка | `5555 5555 5555 4527` | `4889 9717 0658 8753` | `2202 2022 1231 2379` |
| `card_expired` — истёк срок действия карты | `5555 5555 5555 4543` | `4141 4354 1263 0840` | `2200 0000 0000 0038` |
| `invalid_csc` — неверный CVC | `5555 5555 5555 4626` | `4194 1806 6614 6368` | `2200 7702 1272 7079` |
| `3d_secure_failed` — 3-D Secure не пройден | `5555 5555 5555 4592` | `4839 6654 9960 3842` | `2200 0000 0000 0012` |
| `fraud_suspected` — подозрение на мошенничество | `5555 5555 5555 4568` | `4483 2742 8229 9972` | `2200 0000 0000 0046` |
| `call_issuer` — обратитесь в банк | `5555 5555 5555 4535` | `4926 9464 1623 9025` | `2200 0000 0000 0020` |
| `invalid_card_number` | `5555 5555 5555 4618` | `4951 0178 5363 0544` | `2201 3820 0000 0013` |
| `issuer_unavailable` | `5555 5555 5555 4501` | `4654 1308 4835 9150` | `2201 3820 0000 0021` |
| `payment_method_limit_exceeded` | `5555 5555 5555 4576` | `4565 2310 2257 7548` | `2201 3820 0000 0039` |
| `payment_method_restricted` | `5555 5555 5555 4550` | `4233 9611 6907 1671` | `2201 3820 0000 0047` |
| `country_forbidden` | `5555 5555 5555 4584` | `4969 7515 1001 3864` | `2201 3820 0000 0054` |

Источник: [Тестирование — тестовые банковские карты](https://yookassa.ru/developers/payment-acceptance/testing-and-going-live/testing#test-bank-card).
Карта отклонена: страница оформления показывает причину на русском языке и кнопку «Оплатить другой картой» — новая
попытка для **того же заказа**. Отмена оплаченного заказа (покупателем или администратором, до доставки) → автоматический
возврат денег (`POST /v3/refunds`), товар возвращается на склад.

Примечание: в зависимости от настроек магазина виджет может сам предложить попробовать другую карту прямо в форме
при отказе (payment остаётся `pending`); если нужно, чтобы каждый отказ завершал payment (`payment.canceled`),
попросите менеджера ЮKassa отключить у виджета режим обработки неуспешных попыток.

## Локальный запуск (сервисы на машине, инфраструктура в Docker)

```bash
docker compose up -d postgres rabbitmq mailpit
cd backend
./mvnw clean install                        # сборка + все тесты, установка common-lib в ~/.m2
./mvnw -pl config-server spring-boot:run    # терминал 1
./mvnw -pl discovery-server spring-boot:run # терминал 2
./mvnw -pl auth-service spring-boot:run     # терминал 3
./mvnw -pl user-service spring-boot:run     # терминал 4
./mvnw -pl product-service spring-boot:run  # терминал 5
./mvnw -pl inventory-service spring-boot:run # терминал 6
./mvnw -pl cart-service spring-boot:run     # терминал 7
./mvnw -pl order-service spring-boot:run    # терминал 8
./mvnw -pl payment-service spring-boot:run  # терминал 9
./mvnw -pl notification-service spring-boot:run # терминал 10
./mvnw -pl api-gateway spring-boot:run      # терминал 11

cd ../frontend && npm ci && npm run dev     # терминал 12 → http://localhost:3000
```

Тесты используют Testcontainers (настоящие PostgreSQL, RabbitMQ) и автоматически пропускаются, если Docker не запущен.

Профиль по умолчанию — `dev`; в этом профиле сервисы сами читают файл `.env` в корне проекта,
поэтому экспортировать переменные окружения вручную не нужно.

## Тестирование

```bash
cd backend && ./mvnw clean verify           # модульные + интеграционные (Testcontainers), отчёт о покрытии JaCoCo
cd frontend && npm run typecheck && npm run lint && npm test && npm run build

# вся система, после "docker compose up -d --build --wait" (создаёт тестовые данные: только для локальной/тестовой среды)
ADMIN_EMAIL=admin@shop.local ADMIN_PASSWORD='<ADMIN_PASSWORD>' scripts/smoke-test.sh
```

Подробнее: [docs/testing.md](docs/testing.md).

## Устранение неполадок

| Симптом | Частая причина | Решение |
|---|---|---|
| `required variable ... is missing a value` при `docker compose up` | В `.env` не хватает переменной | Скопируйте недостающие переменные из `.env.example` |
| Сервис падает при запуске с `password authentication failed` | Пароль базы данных изменён после создания volume | `docker compose down -v` и повторный запуск (данные удаляются) |
| Все запросы после входа получают 401 / токен недействителен | `JWT_SECRET` содержит перевод строки (`\r` в Windows) или слишком короткий | Сгенерируйте заново: `openssl rand -base64 64 \| tr -d '\r\n'` |
| Gateway возвращает `503` сразу после запуска | Eureka ещё не обновила список сервисов (около 30 секунд) | Подождите и повторите |
| Сайт сообщает «Сервис временно недоступен» | Gateway или связанный сервис ещё не в состоянии healthy | `docker compose ps`, смотрите `docker compose logs api-gateway` |
| Заказ застрял в `PENDING` / `AWAITING_PAYMENT` | inventory или payment-service не запущены, либо событие ушло в DLQ | `docker compose ps`; проверьте очереди `*.dlq` в RabbitMQ Management |
| Письма не приходят | notification-service или Mailpit не запущены | http://localhost:8025, администратор → *Письма* для повторной отправки |
| `429` при многократных попытках | Rate limit (вход/регистрация — 10 раз в минуту) | Подождите время из заголовка `Retry-After`; при разработке можно задать `RATE_LIMIT_ENABLED=false` |

Каждый ответ с ошибкой содержит `correlationId`; найдите этот идентификатор в логах (`docker compose logs | grep <id>`),
чтобы увидеть весь путь запроса через сервисы.

## Общие соглашения

- **Успешный ответ**: `ApiResponse { success, message, data, timestamp }`
- **Ответ с ошибкой**: `ErrorResponse { timestamp, status, error, code, message, path, correlationId, violations[] }`;
  `message` и тексты `violations` — на русском (локаль по умолчанию ru-RU).
- **Correlation id**: заголовок `X-Correlation-Id`, генерируется при отсутствии, записывается в каждую строку лога
  (`[service-name,correlationId]`), передаётся дальше через Feign и RabbitMQ.
- **Секреты**: отсутствуют в исходном коде и config-repo; всегда берутся из переменных окружения.
- **Аутентификация**: access token JWT HS256 (15 минут) + ротируемый непрозрачный refresh token (7 дней).
  Gateway проверяет JWT, каждый сервис проверяет его повторно (defense in depth); детальные права — через `@PreAuthorize`.
- **Пагинация**: `?page=0&size=20&sort=createdAt,desc` (не более 100 элементов на страницу), возвращается `PageResponse`.
- **События**: RabbitMQ, у каждого домена свой topic exchange, у каждой очереди есть DLQ (список в
  [common-lib](backend/common-lib/README.md)). События Saga для order, inventory, payment проходят через
  transactional outbox (не теряются при сбое брокера); консьюмеры идемпотентны, так как доставка at-least-once.
- **Синхронные вызовы между сервисами**: OpenFeign через Eureka + circuit breaker Resilience4j (cart, order → product,
  inventory); сбой зависимости возвращает `503`, а не подвешивает запрос.
- **Время и деньги**: часовой пояс Europe/Moscow, суммы в RUB (на сайте отображаются как «1 234,50 ₽»).
