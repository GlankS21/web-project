# discovery-server

Netflix Eureka Server — реестр сервисов. Порт по умолчанию: **8761**.

Это базовая инфраструктура, поэтому сервис содержит собственную конфигурацию и не получает её из config-server
(чтобы избежать циклической зависимости при запуске).

## Безопасность

Dashboard и REST API `/eureka/**` требуют HTTP Basic с `EUREKA_USERNAME` / `EUREKA_PASSWORD`.
Клиенты регистрируются по URL с учётными данными (общая конфигурация в config-repo):

```
http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@discovery-server:8761/eureka/
```

## Профили

| Профиль | Отличия |
|---|---|
| `dev` (по умолчанию) | self-preservation выключен, упавшие экземпляры удаляются примерно через 5 с, читает `.env` |
| `docker` | hostname `discovery-server`, self-preservation включён (меняется через `EUREKA_SELF_PRESERVATION`) |

## Переменные окружения

| Переменная | Обязательна | По умолчанию |
|---|---|---|
| `EUREKA_USERNAME` | ✔ | |
| `EUREKA_PASSWORD` | ✔ | |
| `DISCOVERY_SERVER_PORT` | | `8761` |
| `EUREKA_HOSTNAME` | | `localhost` / `discovery-server` |

## Запуск и проверка

```bash
cd backend
./mvnw install -DskipTests                 # первый раз: установка common-lib в ~/.m2
./mvnw -pl discovery-server spring-boot:run

curl http://localhost:8761/actuator/health
curl -u eureka:<password> -H "Accept: application/json" http://localhost:8761/eureka/apps
```

Dashboard: http://localhost:8761
