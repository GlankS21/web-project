# user-service

Профили пользователей и адресная книга доставки. Порт по умолчанию: **8082**. База данных: `user_db`.

Первичный ключ профиля — это id учётной записи в auth-service (claim `sub` в JWT); этот сервис
не хранит ни пароли, ни роли.

## Создание профиля

1. Получение события `user.registered` в очереди `user.user-registered` → создание профиля с именем и фамилией.
   Обработка ошибок: 3 повторные попытки, затем перемещение в `user.user-registered.dlq`.
2. Если событие ещё не пришло или потеряно, `GET /api/users/me` сам создаёт пустой профиль по email из токена.
   Когда событие приходит с опозданием, имя и фамилия дозаполняются. Обработка идемпотентна.

## API (все требуют access token)

| Method | Путь | Права | Описание |
|---|---|---|---|
| GET | `/api/users/me` | вход | Мой профиль |
| PUT | `/api/users/me` | вход | Обновление имени и фамилии, телефона, даты рождения |
| GET | `/api/users/me/addresses` | вход | Список адресов (адрес по умолчанию первым) |
| GET | `/api/users/me/addresses/{id}` | вход | Подробности |
| POST | `/api/users/me/addresses` | вход | Добавление адреса |
| PUT | `/api/users/me/addresses/{id}` | вход | Изменение |
| DELETE | `/api/users/me/addresses/{id}` | вход | Удаление |
| PATCH | `/api/users/me/addresses/{id}/default` | вход | Сделать адресом по умолчанию |
| GET | `/api/users?keyword=&page=&size=&sort=` | ADMIN | Поиск по email/имени |
| GET | `/api/users/{userId}` | ADMIN | Подробности профиля |
| GET | `/api/users/{userId}/addresses` | ADMIN | Адресная книга пользователя (адрес по умолчанию первым) |

## Правила адресной книги

- Не более 10 адресов на пользователя.
- Первый адрес всегда становится адресом по умолчанию; адрес по умолчанию всегда **ровно один** (обеспечивается partial unique
  index `uk_addresses_one_default_per_user` в БД).
- При удалении адреса по умолчанию им становится самый новый из оставшихся.
- Чтобы сменить адрес по умолчанию, выберите другой адрес по умолчанию; просто снять отметку нельзя.
- Чужой адрес возвращает 404 (его существование не раскрывается).
- `countryCode` по ISO 3166-1 alpha-2 (по умолчанию `RU`); телефон в формате `+7XXXXXXXXXX` (проверка `^\+7\d{10}$`,
  например `+79161234567`). Сообщения об ошибках проверки — на русском.

## Начальные данные (Flyway)

- `V1__init_schema.sql`: `user_profiles`, `addresses`
- `V2__seed_admin_profile.sql`: профиль администратора (тот же id, что у учётной записи администратора в auth-service)

## Переменные окружения

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `USER_DB_PASSWORD`, `ADMIN_EMAIL`;
необязательные: `USER_SERVICE_PORT`.

## Тесты

```bash
cd backend
./mvnw -pl user-service test
```

- Unit-тесты: `UserProfileServiceImplTest`, `AddressServiceImplTest`
- Testcontainers: `AddressRepositoryIntegrationTest`, `UserFlowIntegrationTest` (настоящие PostgreSQL + RabbitMQ)
