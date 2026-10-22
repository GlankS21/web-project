# API

Все API доступны через gateway: `http://localhost:8080`. Интерактивная документация (OpenAPI 3, с кнопкой
*Authorize* для вставки access token): **http://localhost:8080/swagger-ui.html**, сервис выбирается в правом верхнем углу.
OpenAPI JSON каждого сервиса: `http://localhost:8080/docs/<service>/v3/api-docs`.

## Соглашения

| | |
|---|---|
| Аутентификация | `Authorization: Bearer <accessToken>` (получается из `/api/auth/login` или `/api/auth/register`) |
| Успех | `{ "success": true, "message": "...", "data": ..., "timestamp": "..." }` |
| Ошибка | `{ "timestamp", "status", "error", "code", "message", "path", "correlationId", "violations": [{ "field", "message" }] }` |
| Пагинация | `?page=0&size=20&sort=createdAt,desc` (не более 100 на страницу) → `data = { content, page, size, totalElements, totalPages, first, last }` |
| Деньги | Десятичное число JSON (`89990.00`), валюта ISO 4217 в поле `currency` (RUB) |
| Время | ISO-8601 UTC (`2026-10-21T10:00:00Z`); группировка по дням и отображение — в часовом поясе Europe/Moscow |
| Трассировка | Отправьте или получите `X-Correlation-Id`; присутствует в каждом ответе с ошибкой |
| Язык | `message` и тексты `violations` — на русском (локаль по умолчанию ru-RU) |

| Код | Когда |
|---|---|
| 400 | Некорректные данные (`violations` перечисляет ошибочные поля), повреждённый JSON |
| 401 | Нет токена, токен истёк или неверен; неверный пароль |
| 403 | Недостаточно прав; учётная запись заблокирована |
| 404 | Не найдено или не принадлежит вызывающему |
| 409 | Дубликат (email, SKU, slug) |
| 422 | Нарушение бизнес-правила (нет в наличии, недопустимый переход статуса…) |
| 429 | Превышен rate limit, см. заголовок `Retry-After` |
| 503 | Зависимый сервис не отвечает (circuit breaker) |

Права: **публично**, **вход** (любая учётная запись), **admin** (`ROLE_ADMIN`).

## auth-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| POST | `/api/auth/register` | публично | Регистрация покупателя с немедленным входом (201) |
| POST | `/api/auth/login` | публично | Вход, возвращает access token + refresh token |
| POST | `/api/auth/refresh` | публично | Обмен refresh token на новую пару токенов (старый токен перестаёт действовать) |
| POST | `/api/auth/logout` | публично | Отзыв переданного refresh token |
| POST | `/api/auth/logout-all` | вход | Отзыв всех сеансов учётной записи |
| POST | `/api/auth/password/forgot` | публично | Восстановление пароля `{ "email" }`: всегда возвращает 200 с одним и тем же сообщением (не раскрывает, для каких e-mail есть учётные записи); ссылка отправляется через notification-service, не чаще 1 раза в минуту на учётную запись |
| POST | `/api/auth/password/reset` | публично | Установка нового пароля `{ "token", "newPassword" }` по одноразовому токену (30 минут); отзывает все сеансы. Неверный/истёкший/использованный токен → `400` |
| GET | `/api/auth/me` | вход | Текущая учётная запись |
| PUT | `/api/auth/me/password` | вход | Смена пароля (отзывает все сеансы) |
| GET | `/api/auth/admin/accounts?email=` | admin | Поиск учётных записей |
| GET | `/api/auth/admin/accounts/{id}` | admin | Подробности учётной записи |
| PUT | `/api/auth/admin/accounts/{id}/roles` | admin | Переназначение ролей `{ "roles": ["ROLE_CUSTOMER", "ROLE_ADMIN"] }` |
| PATCH | `/api/auth/admin/accounts/{id}/status` | admin | Блокировка / разблокировка `{ "enabled": false }` (отзывает сеансы; нельзя заблокировать себя) |

Пароль: 8–72 символа, содержит буквы и цифры. Access token живёт 15 минут, refresh token — 7 дней.
На `/api/auth/password/*` действует тот же лимит частоты, что и на вход (10 раз в минуту на клиента).

## user-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET / PUT | `/api/users/me` | вход | Просмотр / изменение профиля (имя и фамилия, телефон в формате `+7XXXXXXXXXX`, дата рождения) |
| GET / POST | `/api/users/me/addresses` | вход | Список (адрес по умолчанию первым) / добавление адреса (201, не более 10) |
| GET / PUT / DELETE | `/api/users/me/addresses/{id}` | вход | Один адрес |
| PATCH | `/api/users/me/addresses/{id}/default` | вход | Сделать адресом по умолчанию |
| GET | `/api/users?keyword=` | admin | Поиск профилей по email или имени |
| GET | `/api/users/{id}` | admin | Профиль пользователя |
| GET | `/api/users/{id}/addresses` | admin | Адресная книга пользователя |

## product-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/products` | публично | Поиск: `keyword`, `categoryId` (включая дочерние категории), `brand`, `minPrice`, `maxPrice`, `sort` (`price`, `name`, `createdAt`, `updatedAt`, `sku`, `ratingAverage`); результат содержит `ratingAverage`, `ratingCount`; администратор может дополнительно фильтровать по `status` |
| GET | `/api/products/{id}`, `/api/products/slug/{slug}` | публично | Подробности (покупатель видит только товары в продаже) |
| GET | `/api/products/by-ids?ids=1,2` | публично | Несколько товаров (не более 100) |
| POST | `/api/products` | admin | Создание (по умолчанию `DRAFT`) |
| PUT | `/api/products/{id}` | admin | Изменение (SKU не меняется) |
| PATCH | `/api/products/{id}/status` | admin | `DRAFT` / `ACTIVE` / `ARCHIVED` |
| DELETE | `/api/products/{id}` | admin | Снятие с продажи (мягкое удаление) |
| POST | `/api/products/images` | admin | Загрузка изображения multipart (part `file`, JPEG/PNG/WebP/GIF с проверкой по magic bytes, ≤ 5 МБ) → `201 { url, contentType, size }` |
| GET | `/api/products/images/{file}` | публично | Загруженное изображение (кеш на 1 год) |
| GET | `/api/products/{id}/reviews?page=&size=` | публично | Отзывы о товаре (сначала новые, не более 50 на страницу) |
| GET | `/api/reviews/products/{id}/mine` | вход | `{ purchased, canReview, review }` вызывающего |
| PUT | `/api/reviews/products/{id}` | вход | Написать / изменить отзыв `{ "rating": 1–5, "comment", "authorName" }`; товар ещё не получен → `422` |
| DELETE | `/api/reviews/products/{id}` | вход | Удалить свой отзыв |
| GET | `/api/reviews/admin?productId=&rating=` | admin | Все отзывы |
| DELETE | `/api/reviews/admin/{reviewId}` | admin | Удалить отзыв (средняя оценка пересчитывается) |
| GET | `/api/categories` | публично | Двухуровневое дерево категорий |
| GET | `/api/categories/{id}`, `/api/categories/slug/{slug}` | публично | Одна категория |
| POST / PUT / DELETE | `/api/categories[/{id}]` | admin | Управление категориями (удалить можно только пустую категорию) |

Названия и описания демонстрационного каталога — на русском; slug — латинская транслитерация кириллических
названий (например, `/api/products/slug/<slug-латиницей>`).

## inventory-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/inventory/availability?productIds=1,2` | публично | Доступное для продажи количество (не более 100 товаров) |
| GET | `/api/inventory?lowStock=true` | admin | Список остатков |
| GET | `/api/inventory/{productId}` | admin | Остаток одного товара |
| PUT | `/api/inventory/{productId}` | admin | Инвентаризация: установить фактический остаток `{ "quantityOnHand", "lowStockThreshold", "reason" }` |
| POST | `/api/inventory/{productId}/adjustments` | admin | Приход / расход `{ "quantityChange": 20, "reason": "..." }` |
| GET | `/api/inventory/{productId}/movements` | admin | Журнал движения склада |
| GET | `/api/inventory/reservations/{orderId}` | admin | Товар, зарезервированный под заказ |

## cart-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/cart` | вход | Корзина с текущими ценами и остатками, статусом каждой строки, `checkoutReady` |
| POST | `/api/cart/items` | вход | Добавить `{ "productId", "quantity" }` (количество суммируется) |
| PUT | `/api/cart/items/{productId}` | вход | Задать количество `{ "quantity" }` |
| DELETE | `/api/cart/items/{productId}` | вход | Удалить строку |
| DELETE | `/api/cart` | вход | Очистить корзину |

## order-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| POST | `/api/orders` | вход | Оформление заказа; рекомендуется передавать `Idempotency-Key`. Возвращает `201` (новый) или `200` (заказ, уже созданный с тем же ключом) |
| GET | `/api/orders?status=` | вход | Мои заказы |
| GET | `/api/orders/{id}` | вход | Подробности и история статусов (администратор видит все заказы) |
| POST | `/api/orders/{id}/cancel` | вход | Отмена недоставленного заказа `{ "reason" }` (владелец заказа или администратор); за оплаченный заказ деньги возвращаются |
| GET | `/api/orders/admin?status=&userId=&keyword=` | admin | Поиск заказов (keyword: номер заказа, email) |
| PATCH | `/api/orders/admin/{id}/status` | admin | `PAID → SHIPPED`, `SHIPPED → DELIVERED` `{ "status", "note" }`; `DELIVERED` публикует `order.delivered` (открывает возможность оставить отзыв) |
| POST | `/api/orders/coupons/preview` | вход | Предварительный расчёт промокода `{ "code", "items" }` → `subtotal`, `discountAmount`, `shippingFee`, `totalAmount`; промокод неприменим → `422` |
| GET / POST | `/api/orders/admin/coupons` | admin | Список (`keyword`, `active`) / создание промокода (201) |
| GET / PUT / DELETE | `/api/orders/admin/coupons/{id}` | admin | Один промокод; удалить можно только ни разу не использованный (`422 coupon.error.in-use`) |
| GET | `/api/orders/admin/stats?from=&to=&top=` | admin | Статистика (по умолчанию 30 дней, не более 366): выручка, число заказов, средний чек, заказы по статусам, по дням (Europe/Moscow), лидеры продаж |

Пример тела запроса на оформление заказа:

```json
{
  "items": [{ "productId": 7, "quantity": 1 }],
  "paymentMethod": "ONLINE",
  "shippingAddress": {
    "recipientName": "Иван Петров", "phoneNumber": "+79161234567",
    "addressLine1": "ул. Тверская, д. 1, кв. 10", "city": "Москва", "postalCode": "125009", "countryCode": "RU"
  },
  "note": "Доставить в рабочее время",
  "couponCode": "WELCOME10"
}
```

Цена заново берётся из каталога при оформлении; цена в корзине служит только для отображения. `paymentMethod`: `COD`
(оплата при получении), `ONLINE` (карта через виджет ЮKassa). Валюта: RUB. Телефон получателя проверяется по шаблону
`^\+7\d{10}$`, страна по умолчанию — RU. У заказа с возвратом есть дополнительные поля `refundTransactionId`, `refundedAt`.
`couponCode` (необязательно): `totalAmount = subtotal − discountAmount + shippingFee`, стоимость доставки рассчитывается
от суммы до скидки, к оплате всегда остаётся не менее 1 ₽; отмена заказа возвращает использование промокода.
Демонстрационные промокоды: `WELCOME10` (−10 %, не более 5 000 ₽), `SALE500` (−500 ₽ для заказов от 5 000 ₽).

## payment-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/payments/orders/{orderId}` | вход | Платёж по заказу и история транзакций (владелец заказа или администратор); `404`, если платежа ещё нет |
| POST | `/api/payments/orders/{orderId}/retry` | вход | Новая попытка (другая карта) после отказа; возвращает новый `confirmationToken` |
| POST | `/api/payments/orders/{orderId}/refresh` | вход | Запрос статуса ожидающих транзакций из ЮKassa |
| GET | `/api/payments` | admin | Все транзакции |
| POST | `/api/payments/webhooks/yookassa` | публично | Webhook ЮKassa (`PAYMENT_PROVIDER=yookassa`) |
| POST | `/api/payments/mock/{paymentId}/pay` | вход | Эмулированная форма карты (`PAYMENT_PROVIDER=mock`) `{ "cardNumber", "expiry": "MM/YY", "cvc", "threeDSecureCode" }` |

Платёж создаётся автоматически в ходе Saga (`confirmationToken` для инициализации виджета ЮKassa), API для прямого
создания нет.

## notification-service

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/notifications` | вход | Письма, отправленные мне |
| GET | `/api/notifications/admin?status=&type=` | admin | Поиск писем |
| POST | `/api/notifications/admin/{id}/resend` | admin | Отправить повторно |
