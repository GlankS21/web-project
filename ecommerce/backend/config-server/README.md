# config-server

Spring Cloud Config Server — cấu hình tập trung cho các service. Port mặc định: **8888**.

## Backend cấu hình

Dùng backend `native` (file system). Thư mục cấu hình: `src/main/resources/config-repo/`

| File | Áp dụng cho |
|---|---|
| `application.yml` | mọi service, mọi profile |
| `application-dev.yml` / `application-docker.yml` | mọi service, theo profile |
| `<tên-service>.yml`, `<tên-service>-<profile>.yml` | một service cụ thể (thêm ở các giai đoạn sau) |

- Profile `dev`: đọc từ classpath (`classpath:/config-repo`).
- Profile `docker`: thư mục được mount vào container tại `/config-repo`, sửa file rồi restart
  service client là có hiệu lực, không cần build lại image.
- Có thể ghi đè bằng biến `CONFIG_SEARCH_LOCATIONS`.

**Không lưu secret trong config-repo.** Giá trị như `${RABBITMQ_PASSWORD}` được trả về nguyên dạng
placeholder và được client tự resolve từ biến môi trường của nó (đã có test kiểm chứng).

## Bảo mật

Mọi endpoint (trừ `/actuator/health`, `/actuator/info`) yêu cầu HTTP Basic với
`CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD`.

## Biến môi trường

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `CONFIG_SERVER_USERNAME` | ✔ | |
| `CONFIG_SERVER_PASSWORD` | ✔ | |
| `CONFIG_SERVER_PORT` | | `8888` |
| `CONFIG_SEARCH_LOCATIONS` | | `classpath:/config-repo` (dev), `file:/config-repo` (docker) |

## Chạy & kiểm tra

```bash
cd backend
./mvnw install -DskipTests                            # lần đầu: cài common-lib vào ~/.m2
./mvnw -pl config-server spring-boot:run              # profile dev, đọc ecommerce/.env

curl http://localhost:8888/actuator/health
curl -u config:<password> http://localhost:8888/application/default
curl -u config:<password> http://localhost:8888/auth-service/docker   # khi đã có file của service
```

Docker: `docker compose up -d --build config-server`
