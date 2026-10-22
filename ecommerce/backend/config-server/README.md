# config-server

Spring Cloud Config Server — централизованная конфигурация сервисов. Порт по умолчанию: **8888**.

## Backend конфигурации

Используется backend `native` (файловая система). Каталог конфигурации: `src/main/resources/config-repo/`

| Файл | Применяется к |
|---|---|
| `application.yml` | всем сервисам, всем профилям |
| `application-dev.yml` / `application-docker.yml` | всем сервисам, по профилю |
| `<имя-сервиса>.yml`, `<имя-сервиса>-<profile>.yml` | конкретному сервису (добавляются на последующих этапах) |

- Профиль `dev`: чтение из classpath (`classpath:/config-repo`).
- Профиль `docker`: каталог монтируется в контейнер в `/config-repo`; после изменения файла достаточно перезапустить
  сервис-клиент, пересобирать образ не нужно.
- Можно переопределить переменной `CONFIG_SEARCH_LOCATIONS`.

**Не храните секреты в config-repo.** Значения вида `${RABBITMQ_PASSWORD}` возвращаются как есть, в виде
placeholder, и клиент сам подставляет их из своих переменных окружения (это проверено тестом).

## Безопасность

Все эндпоинты (кроме `/actuator/health`, `/actuator/info`) требуют HTTP Basic с
`CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD`.

## Переменные окружения

| Переменная | Обязательна | По умолчанию |
|---|---|---|
| `CONFIG_SERVER_USERNAME` | ✔ | |
| `CONFIG_SERVER_PASSWORD` | ✔ | |
| `CONFIG_SERVER_PORT` | | `8888` |
| `CONFIG_SEARCH_LOCATIONS` | | `classpath:/config-repo` (dev), `file:/config-repo` (docker) |

## Запуск и проверка

```bash
cd backend
./mvnw install -DskipTests                            # первый раз: установка common-lib в ~/.m2
./mvnw -pl config-server spring-boot:run              # профиль dev, читает ecommerce/.env

curl http://localhost:8888/actuator/health
curl -u config:<password> http://localhost:8888/application/default
curl -u config:<password> http://localhost:8888/auth-service/docker   # когда файл сервиса уже есть
```

Docker: `docker compose up -d --build config-server`
