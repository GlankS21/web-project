# auth-service

Регистрация, вход, выпуск/ротация/отзыв токенов, права доступа. Порт по умолчанию: **8081**. База данных: `auth_db`.

## Токены

| | Access token | Refresh token |
|---|---|---|
| Формат | JWT HS256 | Случайная строка 256 бит (opaque) |
| Срок действия | 15 минут (`JWT_ACCESS_TOKEN_TTL`) | 7 дней (`JWT_REFRESH_TOKEN_TTL`), продлевается при каждом refresh |
| Хранение | Не хранится | Хранится только SHA-256 токена |
| Отзыв | Нет (истекает естественным образом) | Да: logout, logout-all, смена пароля, блокировка учётной записи |

Claim access token: `sub` (id учётной записи), `email`, `roles` (например, `["ROLE_CUSTOMER"]`), `token_type=access`, `iss`, `jti`.

**Ротация refresh token:** при каждом `/refresh` старый токен отзывается и указывает на новый. Если уже отозванный
токен используется повторно (признак кражи токена), отзываются все сеансы учётной записи.

## API

| Method | Путь | Права | Описание |
|---|---|---|---|
| POST | `/api/auth/register` | публично | Создание учётной записи `ROLE_CUSTOMER`, возвращает токены |
| POST | `/api/auth/login` | публично | Вход |
| POST | `/api/auth/refresh` | публично | Обмен refresh token на новую пару токенов |
| POST | `/api/auth/logout` | публично | Отзыв переданного refresh token |
| POST | `/api/auth/logout-all` | вход | Отзыв всех сеансов |
| GET | `/api/auth/me` | вход | Данные учётной записи |
| PUT | `/api/auth/me/password` | вход | Смена пароля (отзывает все сеансы) |
| POST | `/api/auth/password/forgot` | публично | Восстановление пароля: отправка ссылки для сброса по e-mail |
| POST | `/api/auth/password/reset` | публично | Установка нового пароля по токену из ссылки (отзывает все сеансы) |
| GET | `/api/auth/admin/accounts?email=&page=&size=&sort=` | ADMIN | Поиск учётных записей |
| GET | `/api/auth/admin/accounts/{id}` | ADMIN | Подробности |
| PUT | `/api/auth/admin/accounts/{id}/roles` | ADMIN | Переназначение ролей (нельзя снять роль администратора с себя) |
| PATCH | `/api/auth/admin/accounts/{id}/status` | ADMIN | Блокировка/разблокировка (нельзя заблокировать себя) |

Пароль: 8–72 символа, минимум одна буква и одна цифра; хешируется BCrypt.
Неверный email и неверный пароль дают одно и то же сообщение, а время обработки примерно одинаково.

## Восстановление пароля

1. `POST /api/auth/password/forgot` с `{"email": "..."}` всегда возвращает `200` с одним и тем же сообщением
   (`auth.password.reset-requested`), независимо от того, существует ли e-mail (учётные записи не раскрываются).
   Только если учётная запись существует, активна и за период `PASSWORD_RESET_COOLDOWN` (по умолчанию 1 минута)
   для неё не создавалось токенов: старые неиспользованные токены аннулируются, создаётся новый токен
   (256 бит, в таблице `password_reset_tokens` хранится только SHA-256) со сроком действия
   `PASSWORD_RESET_TTL` (по умолчанию 30 минут) и публикуется событие `user.password-reset-requested`.
2. notification-service отправляет письмо (на русском) со ссылкой `<FRONTEND_URL>/reset-password?token=...`.
3. `POST /api/auth/password/reset` с `{"token": "...", "newPassword": "..."}` (те же правила для пароля):
   строка токена блокируется (`SELECT ... FOR UPDATE`), токен должен существовать, быть неиспользованным и неистёкшим,
   а учётная запись — активной. Новый пароль хешируется BCrypt, этот токен и все остальные токены сброса учётной записи
   помечаются как использованные, все refresh token отзываются. Возвращает `200` (`auth.password.reset-done`).
   Неверный/истёкший/использованный токен: `400 BAD_REQUEST` с `auth.error.reset-token-invalid`.

Gateway оставляет эти два эндпоинта публичными и ограничивает их 10 запросами в минуту на клиента.
Тексты сообщений — ключи в `src/main/resources/messages_ru.properties` (на русском; общие сообщения — в
`common-messages_ru.properties` из common-lib, локаль по умолчанию ru-RU).
`PasswordResetTokenCleanupJob` ежедневно в 03:30 удаляет истёкшие или использованные токены
(`app.auth.password-reset-token-cleanup-cron`).

## События

Публикует `user.registered` (`UserRegisteredEvent`) в exchange `shop.user.events` **после commit транзакции**.
Если RabbitMQ недоступен, событие теряется; user-service сам создаёт профиль при первом вызове `GET /api/users/me`.

Публикует `user.password-reset-requested` (`PasswordResetRequestedEvent`: `userId`, `email`, исходный `token`,
`expiresAt`) в тот же exchange, также **после commit транзакции**. Исходный токен передаётся только через внутренний
брокер и никогда не записывается в лог. Если событие потеряно, покупатель может запросить новую ссылку после периода ожидания.

## Начальные данные (Flyway)

- `V1__init_schema.sql`: таблицы `roles`, `user_accounts`, `user_account_roles`, `refresh_tokens`
- `V2__seed_roles_and_admin.sql`: `ROLE_CUSTOMER`, `ROLE_ADMIN` и учётная запись администратора
  (id `00000000-0000-0000-0000-000000000001`) с email/паролем из `ADMIN_EMAIL` / `ADMIN_PASSWORD`,
  хеш bcrypt через `pgcrypto`. Seed выполняется один раз; последующее изменение переменных не меняет пароль администратора.
- `V3__password_reset_tokens.sql`: таблица `password_reset_tokens` (токены сброса пароля, хранится только хеш)

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `AUTH_DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`;
необязательные: `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`, `AUTH_SERVICE_PORT`,
`PASSWORD_RESET_TTL` (по умолчанию `30m`), `PASSWORD_RESET_COOLDOWN` (по умолчанию `1m`).

## Тесты

```bash
cd backend
./mvnw -pl auth-service test
```

- Unit-тесты (Mockito): `AuthServiceImplTest`, `TokenServiceImplTest`, `AccountAdminServiceImplTest`,
  `PasswordResetServiceImplTest` (несуществующий e-mail, заблокированная учётная запись, период ожидания, истёкший/использованный токен)
- Testcontainers (автоматически пропускаются без Docker): `UserAccountRepositoryIntegrationTest`,
  `RefreshTokenRepositoryIntegrationTest`, `AuthFlowIntegrationTest` (настоящие PostgreSQL + RabbitMQ; включает сценарий
  восстановление пароля → получение токена из события → сброс → вход с новым паролем, токен нельзя использовать повторно)
