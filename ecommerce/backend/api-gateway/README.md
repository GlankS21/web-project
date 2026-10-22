# api-gateway

Единая точка входа в систему (Spring Cloud Gateway, WebFlux). Порт по умолчанию: **8080**.
Фронтенд и внешние клиенты обращаются к API только через gateway; бизнес-сервисы не публикуют порты.

## Функции

| Функция | Реализация |
|---|---|
| Маршрутизация | Маршруты `lb://<service>` через Eureka, объявлены в `config-repo/api-gateway.yml` |
| Аутентификация | Проверка JWT HS256 (подпись, срок, `iss`, `token_type=access`). Сервисы за gateway проверяют токен повторно |
| Идентичность для сервисов | Удаляет все заголовки `X-User-*`, присланные клиентом, затем добавляет `X-User-Id`, `X-User-Email`, `X-User-Roles` из JWT |
| CORS | Настраивается только в gateway (`CORS_ALLOWED_ORIGINS`), на preflight отвечает до этапа аутентификации |
| Rate limiting | Token bucket по адресу клиента: по умолчанию burst 40 запросов, 20/с; `/api/auth/login|register|refresh` — 10/мин. При превышении → 429 + `Retry-After` |
| Доверенный прокси | Фронтенд (BFF) передаёт `X-Client-Ip` + `X-Proxy-Secret`; адрес используется для rate limit только если секрет совпадает с `TRUSTED_PROXY_SECRET`. Оба заголовка всегда удаляются перед передачей в сервис |
| Отказоустойчивость | Circuit breaker Resilience4j для каждого маршрута, fallback 503 на `/fallback/<service>` |
| Correlation id | Принимает или генерирует `X-Correlation-Id`, передаёт в сервис, возвращает клиенту, пишет в access log |
| Ошибки | Все ошибки (401, 403, 404, 429, 503, 504) возвращаются в общем формате `ErrorResponse`, сообщения на русском |
| Swagger | `http://localhost:8080/swagger-ui.html` объединяет документацию сервисов |

## Текущие маршруты

| Путь | Сервис |
|---|---|
| `/api/auth/**` | auth-service |
| `/api/users/**` | user-service |
| `/api/products/**`, `/api/categories/**` | product-service |
| `/api/inventory/**` | inventory-service |
| `/api/cart/**` | cart-service |
| `/api/orders/**` | order-service |
| `/api/payments/**` | payment-service |
| `/api/notifications/**` | notification-service |
| `/docs/<service>/v3/api-docs` | OpenAPI JSON сервиса |

## Публичные пути

`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/actuator/health`,
Swagger; `GET /api/products/**`, `GET /api/categories/**`, `GET /api/inventory/availability`.
Все остальные пути требуют access token; `/actuator/**` (кроме health/info) требует `ROLE_ADMIN`.

## Переменные окружения

| Переменная | Обязательна | По умолчанию |
|---|---|---|
| `CONFIG_SERVER_URL` | | `http://localhost:8888` |
| `CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD` | ✔ | |
| `EUREKA_USERNAME` / `EUREKA_PASSWORD` | ✔ | |
| `JWT_SECRET` | ✔ | |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:3000` |
| `RATE_LIMIT_ENABLED` | | `true` |
| `TRUSTED_PROXY_SECRET` | | пусто: `X-Client-Ip` не доверяется, rate limit по TCP-адресу |
| `API_GATEWAY_PORT` | | `8080` |

## Адрес клиента за фронтендом

Все запросы браузера проходят через сервер Next.js, поэтому TCP-адрес, который видит gateway, принадлежит контейнеру
фронтенда. `ClientAddressWebFilter` (выполняется раньше всех остальных фильтров) определяет адрес клиента:

1. Если `TRUSTED_PROXY_SECRET` задан, запрос содержит совпадающий `X-Proxy-Secret` (сравнение за постоянное время),
   а `X-Client-Ip` — корректный IP-адрес → используется `X-Client-Ip`.
2. Иначе → используется TCP-адрес.

Клиент, обращающийся к gateway напрямую, не знает секрета и поэтому не может подделать адрес. Как фронтенд определяет
адрес браузера, см. [frontend/README](../../frontend/README.md#rate-limit-по-браузеру).

## Известные ограничения

Состояние rate limit хранится в памяти каждого экземпляра. При нескольких экземплярах gateway фактический лимит
умножается на их число; для точного глобального лимита нужно общее хранилище (Redis).

## Запуск и тесты

```bash
cd backend
./mvnw -pl api-gateway spring-boot:run     # нужны запущенные config-server и discovery-server
./mvnw -pl api-gateway test                # Docker не нужен
```
