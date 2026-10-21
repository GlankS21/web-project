# discovery-server

Netflix Eureka Server — service registry. Port mặc định: **8761**.

Đây là hạ tầng nền nên service này tự chứa cấu hình, không lấy cấu hình từ config-server
(tránh phụ thuộc vòng khi khởi động).

## Bảo mật

Dashboard và REST API `/eureka/**` yêu cầu HTTP Basic với `EUREKA_USERNAME` / `EUREKA_PASSWORD`.
Các client đăng ký qua URL có kèm credentials (cấu hình chung trong config-repo):

```
http://${EUREKA_USERNAME}:${EUREKA_PASSWORD}@discovery-server:8761/eureka/
```

## Profile

| Profile | Khác biệt |
|---|---|
| `dev` (mặc định) | tắt self-preservation, loại instance chết sau ~5s, đọc `.env` |
| `docker` | hostname `discovery-server`, self-preservation bật (đổi bằng `EUREKA_SELF_PRESERVATION`) |

## Biến môi trường

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `EUREKA_USERNAME` | ✔ | |
| `EUREKA_PASSWORD` | ✔ | |
| `DISCOVERY_SERVER_PORT` | | `8761` |
| `EUREKA_HOSTNAME` | | `localhost` / `discovery-server` |

## Chạy & kiểm tra

```bash
cd backend
./mvnw install -DskipTests                 # lần đầu: cài common-lib vào ~/.m2
./mvnw -pl discovery-server spring-boot:run

curl http://localhost:8761/actuator/health
curl -u eureka:<password> -H "Accept: application/json" http://localhost:8761/eureka/apps
```

Dashboard: http://localhost:8761
