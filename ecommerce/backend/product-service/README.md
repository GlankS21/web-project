# product-service

Каталог товаров: дерево категорий, товары, изображения, поиск/фильтрация. Порт по умолчанию: **8083**. База данных: `product_db`.

Чтение данных публичное (gateway открывает `GET /api/products/**`, `GET /api/categories/**`); любые операции записи
категорий/товаров требуют `ROLE_ADMIN`. Собственные отзывы (`/api/reviews/**`) требуют входа. Покупатели видят только товары `ACTIVE`; администратор (с access token) видит также `DRAFT`
и `ARCHIVED` — для остальных скрытый товар возвращает 404.

## Модель

| Понятие | Правило |
|---|---|
| Категория | Дерево **из 2 уровней**: корневые категории и их непосредственные дочерние. Фильтр по корневой категории включает дочерние |
| Slug | Генерируется из названия, если не передан (кириллица транслитерируется латиницей: «Ноутбуки и ультрабуки» → `noutbuki-i-ultrabuki`); при совпадении slug товара добавляется SKU. При обновлении slug меняется, только если передан явно |
| SKU | `^[A-Z0-9][A-Z0-9-]{2,63}$`, уникален, **не изменяется** после создания (на него ссылаются склад и заказы) |
| Цена | `price > 0`, не более 2 знаков после запятой; `compareAtPrice` (зачёркнутая цена) должна быть больше `price`. Одна валюта для всего магазина: `CATALOG_CURRENCY` (по умолчанию `RUB`) |
| Статус | `DRAFT` (по умолчанию при создании) → `ACTIVE` → `ARCHIVED`. `DELETE` = перевод в `ARCHIVED` (мягкое удаление) |
| Изображения | Не более 10 URL, первое изображение — миниатюра. URL — абсолютный http(s) **или** относительный URL загруженного изображения (`/api/products/images/<uuid>.<ext>`). Если при обновлении передан `images`, список заменяется целиком, если не передан — остаётся без изменений |
| Отзывы | Оставить отзыв может только тот, кто **получил товар** (есть заказ `DELIVERED` с этим товаром); один отзыв на товар от каждого пользователя (повторная отправка = изменение). `rating` 1–5, `comment` ≤ 2000 символов (trim, пусто → `null`), `authorName` ≤ 60 символов (trim) |
| Средняя оценка | `ratingAverage` (округление HALF_UP до 1 знака, 0 при отсутствии отзывов) и `ratingCount` хранятся прямо в `products` и пересчитываются в той же транзакции после каждого создания/изменения/удаления отзыва. Строка товара блокируется (`SELECT ... FOR UPDATE`), поэтому значения остаются верными при одновременной записи; обновление выполняется native query, поэтому `version`/`updatedAt` товара не меняются |
| Удаление категории | Отклоняется (422), если есть дочерние категории или товары (включая архивные) |

Создание товара публикует событие `product.created` (exchange `shop.product.events`) после commit;
inventory-service создаёт для этого товара пустую запись об остатке.

## Потребляемые события

| Очередь | Exchange / routing key | Обработка |
|---|---|---|
| `product.order-delivered` (DLQ `product.order-delivered.dlq` через `shop.dlx`) | `shop.order.events` / `order.delivered` | Записывает `product_purchases(user_id, product_id, order_id, purchased_at)` для каждого товара заказа. Идемпотентно: unique key `(user_id, product_id, order_id)` + `INSERT ... ON CONFLICT DO NOTHING`, повторное событие ничего не добавляет. Несуществующие id товаров пропускаются (с предупреждением в логе) |

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/products?keyword=&categoryId=&brand=&minPrice=&maxPrice=&page=&size=&sort=` | публично | Поиск; `keyword` ищет по названию/SKU/бренду; сортировка по `price`, `name`, `createdAt`, `updatedAt`, `sku`, `ratingAverage` (например, `sort=ratingAverage,desc`) |
| GET | `/api/products?status=DRAFT` | ADMIN | Фильтр по статусу (игнорируется для не-администраторов) |
| GET | `/api/products/{id}` | публично | Подробности (с изображениями, категорией) |
| GET | `/api/products/slug/{slug}` | публично | Подробности по slug (для URL фронтенда) |
| GET | `/api/products/by-ids?ids=1,2,3` | публично | Не более 100 id, порядок сохраняется, несуществующие/скрытые id пропускаются — cart/order используют для получения текущих цен |
| POST | `/api/products` | ADMIN | Создание |
| PUT | `/api/products/{id}` | ADMIN | Обновление (нужно передать текущий SKU) |
| PATCH | `/api/products/{id}/status` | ADMIN | `{"status":"ACTIVE"}` |
| DELETE | `/api/products/{id}` | ADMIN | Архивирование |
| GET | `/api/categories` | публично | Дерево категорий |
| GET | `/api/categories/{id}`, `/api/categories/slug/{slug}` | публично | Подробности + дочерние категории |
| POST / PUT / DELETE | `/api/categories[/{id}]` | ADMIN | Управление категориями (PUT без `parentId` ⇒ категория становится корневой) |
| POST | `/api/products/images` | ADMIN | Загрузка изображения, `multipart/form-data` part `file` → 201 `{url, contentType, size}` |
| GET | `/api/products/images/{filename}` | публично | Скачивание загруженного изображения (правильный Content-Type даже при `Accept: application/json`, `Cache-Control: public, max-age=31536000, immutable`, `X-Content-Type-Options: nosniff`); 404, если нет |
| GET | `/api/products/{id}/reviews?page=0&size=10` | публично | Отзывы о товаре, сначала новые (`size` не более 50; сортировка `createdAt`, `updatedAt`, `rating`); 404, если товар не существует или не `ACTIVE` (администратор видит и скрытые товары) |
| GET | `/api/reviews/products/{id}/mine` | вход | `{purchased, canReview, review}`; `review` равен `null`, если отзыв ещё не написан |
| PUT | `/api/reviews/products/{id}` | вход | Создание/изменение своего отзыва `{rating, comment, authorName}`; товар не получен → 422 `review.error.not-purchased` |
| DELETE | `/api/reviews/products/{id}` | вход | Удаление своего отзыва; 404, если его нет |
| GET | `/api/reviews/admin?productId=&rating=&page=&size=` | ADMIN | Все отзывы (с `productName`, `productSlug`, `userId`), сначала новые |
| DELETE | `/api/reviews/admin/{reviewId}` | ADMIN | Удаление любого отзыва (с пересчётом оценки) |

Примеры:

```bash
curl -s "http://localhost:8080/api/products?categoryId=4&sort=price,asc&size=5"
curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <adminAccessToken>" -H "Content-Type: application/json" \
  -d '{"sku":"ACC-USB-HUB","name":"USB-C хаб 7 в 1","brand":"UGREEN","price":2490,
       "categoryId":11,"status":"ACTIVE","images":[{"url":"https://picsum.photos/seed/hub/800/800"}]}'
```

Загрузите изображение и используйте возвращённый `url` в `images` товара:

```bash
curl -s -X POST http://localhost:8080/api/products/images \
  -H "Authorization: Bearer <adminAccessToken>" -F "file=@phone.png"
# {"success":true,"message":"Изображение загружено","data":{"url":"/api/products/images/<uuid>.png","contentType":"image/png","size":48213}}
```

## Загрузка изображений

- Принимаются только JPEG, PNG, WebP, GIF, тип определяется по **magic bytes** (`Content-Type` и расширению файла не доверяем):
  текстовый файл, переименованный в `.png` → 400 `image.error.unsupported-type`; пустой файл или отсутствие part `file` → 400 `image.error.empty`.
- Лимит `app.catalog.image-max-size` (по умолчанию 5 МБ) → 400 `image.error.too-large`. Лимит multipart в Spring
  (`spring.servlet.multipart.max-file-size`/`max-request-size`, по умолчанию 6 МБ) немного выше; его превышение
  (`MaxUploadSizeExceededException`) тоже возвращает `ErrorResponse` 400 с тем же сообщением.
- Сохраняется как `<случайный UUID>.<расширение по фактическому типу>` в `app.catalog.image-storage-dir`
  (`PRODUCT_IMAGE_DIR`, по умолчанию `./data/product-images`; каталог создаётся при запуске, если его нет).
  В Docker это named volume, подключённый в `/data/product-images` (Dockerfile заранее создаёт каталог с владельцем `app:app`).
- При скачивании имя файла строго проверяется регулярным выражением (UUID + допустимое расширение), поэтому path traversal невозможен.
- **Файлы не удаляются**: изображения, которые больше не использует ни один товар (замена изображения, пробная загрузка),
  остаются в volume (файлы-сироты). Это допустимо; при необходимости очищайте вручную.

## Сообщения

`src/main/resources/messages_ru.properties` (на русском, UTF-8) содержит, в частности, ключи: `review.saved`, `review.deleted`,
`review.error.not-purchased`, `review.error.not-found`, `review.error.product-not-found`,
`review.error.unsupported-sort`, `image.uploaded`, `image.error.empty`, `image.error.unsupported-type`,
`image.error.too-large`, `image.error.not-found`. Сообщения переводятся через `MessageSource` с локалью по умолчанию
ru-RU; все сообщения API, включая общие из `common-messages_ru.properties` (common-lib) и сообщения Bean Validation, — на русском.

## Начальные данные (Flyway)

- `V1__init_schema.sql`: `categories`, `products`, `product_images` (CHECK для цены, статуса; unique SKU/slug)
- `V2__seed_catalog.sql`: 4 корневые категории, 8 дочерних, **24 товара** (22 `ACTIVE`, 1 `DRAFT` id 23,
  1 `ARCHIVED` id 24), у каждого товара 2 изображения-заглушки с picsum.photos. Id фиксированы, потому что inventory-service
  заполняет остатки для тех же id.
- `V3__prices_in_rub.sql`: перевод демонстрационных цен в RUB
- `V4__reviews.sql`: `product_reviews` (unique `(product_id, user_id)`, CHECK rating 1–5, каскадное удаление по товару),
  `product_purchases` (unique `(user_id, product_id, order_id)`), столбцы `rating_average NUMERIC(2,1)` и
  `rating_count INT` в `products`
- Следующая миграция переводит демонстрационный каталог на русский: названия и описания категорий и товаров на русском,
  slug — латинская транслитерация кириллических названий (id и SKU не меняются)

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `PRODUCT_DB_PASSWORD`;
необязательные: `CATALOG_CURRENCY` (по умолчанию `RUB`), `PRODUCT_SERVICE_PORT`, `PRODUCT_IMAGE_DIR`
(по умолчанию `./data/product-images`, Docker: `/data/product-images`), `PRODUCT_IMAGE_MAX_SIZE` (по умолчанию `5MB`),
`PRODUCT_IMAGE_MULTIPART_MAX_SIZE` (по умолчанию `6MB`, должен быть немного больше `PRODUCT_IMAGE_MAX_SIZE`).

## Тесты

```bash
cd backend
./mvnw -pl product-service test
```

- Unit-тесты: `ProductServiceImplTest`, `CategoryServiceImplTest`, `SlugUtilsTest`, `ReviewServiceImplTest`
  (проверка покупки, создание/изменение, пересчёт оценки, удаление, удаление администратором), `PurchaseServiceImplTest` (идемпотентность, пропуск неизвестных id),
  `LocalImageStorageServiceTest` (magic bytes, размер, имя файла, path traversal), `GlobalExceptionHandlerTest`
- Testcontainers: `ProductCatalogIntegrationTest` (настоящие PostgreSQL + RabbitMQ, демонстрационные данные, событие `product.created`),
  `ReviewsAndImagesIntegrationTest` (`order.delivered` через RabbitMQ → отзыв → средняя оценка, сортировка по оценке,
  не купивший пользователь получает 422, модерация; загрузка PNG и повторное скачивание, отказ для текстового файла, переименованного в `.png`)
