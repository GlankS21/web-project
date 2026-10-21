# api-gateway

Điểm vào duy nhất của hệ thống (Spring Cloud Gateway, WebFlux). Port mặc định: **8080**.
Frontend và client bên ngoài chỉ gọi API qua gateway; các service nghiệp vụ không publish port.

## Chức năng

| Chức năng | Cách làm |
|---|---|
| Định tuyến | Route `lb://<service>` qua Eureka, khai báo trong `config-repo/api-gateway.yml` |
| Xác thực | Kiểm tra JWT HS256 (chữ ký, hạn, `iss`, `token_type=access`). Các service phía sau kiểm tra lại token |
| Danh tính phía sau | Xoá mọi header `X-User-*` client gửi lên, rồi gắn `X-User-Id`, `X-User-Email`, `X-User-Roles` từ JWT |
| CORS | Cấu hình duy nhất tại gateway (`CORS_ALLOWED_ORIGINS`), preflight được trả lời trước bước xác thực |
| Rate limiting | Token bucket theo địa chỉ client: mặc định 40 request burst, 20/s; `/api/auth/login|register|refresh` 10/phút. Vượt ngưỡng → 429 + `Retry-After` |
| Proxy tin cậy | Frontend (BFF) gửi `X-Client-Ip` + `X-Proxy-Secret`; chỉ khi secret khớp `TRUSTED_PROXY_SECRET` thì địa chỉ đó được dùng cho rate limit. Hai header luôn bị xoá trước khi chuyển xuống service |
| Chịu lỗi | Resilience4j circuit breaker cho từng route, fallback 503 tại `/fallback/<service>` |
| Correlation id | Nhận hoặc sinh `X-Correlation-Id`, chuyển xuống service, trả về client, ghi vào access log |
| Lỗi | Mọi lỗi (401, 403, 404, 429, 503, 504) đều theo định dạng `ErrorResponse` chung |
| Swagger | `http://localhost:8080/swagger-ui.html` gộp tài liệu của các service |

## Route hiện có

| Đường dẫn | Service |
|---|---|
| `/api/auth/**` | auth-service |
| `/api/users/**` | user-service |
| `/api/products/**`, `/api/categories/**` | product-service |
| `/api/inventory/**` | inventory-service |
| `/api/cart/**` | cart-service |
| `/api/orders/**` | order-service |
| `/api/payments/**` | payment-service |
| `/api/notifications/**` | notification-service |
| `/docs/<service>/v3/api-docs` | OpenAPI JSON của service |

## Đường dẫn công khai

`/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`, `/actuator/health`,
Swagger; `GET /api/products/**`, `GET /api/categories/**`, `GET /api/inventory/availability`.
Mọi đường dẫn khác cần access token; `/actuator/**` (trừ health/info) cần `ROLE_ADMIN`.

## Biến môi trường

| Biến | Bắt buộc | Mặc định |
|---|---|---|
| `CONFIG_SERVER_URL` | | `http://localhost:8888` |
| `CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD` | ✔ | |
| `EUREKA_USERNAME` / `EUREKA_PASSWORD` | ✔ | |
| `JWT_SECRET` | ✔ | |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:3000` |
| `RATE_LIMIT_ENABLED` | | `true` |
| `TRUSTED_PROXY_SECRET` | | trống: không tin `X-Client-Ip`, rate limit theo địa chỉ TCP |
| `API_GATEWAY_PORT` | | `8080` |

## Địa chỉ client sau frontend

Mọi request của trình duyệt đi qua server Next.js, nên địa chỉ TCP gateway thấy là của container
frontend. `ClientAddressWebFilter` (chạy trước mọi filter khác) xác định địa chỉ client:

1. Nếu `TRUSTED_PROXY_SECRET` được đặt, request có `X-Proxy-Secret` trùng (so sánh thời gian hằng)
   và `X-Client-Ip` là một địa chỉ IP hợp lệ → dùng `X-Client-Ip`.
2. Ngược lại → dùng địa chỉ TCP.

Client gọi thẳng gateway không biết secret nên không giả mạo được địa chỉ. Cách frontend lấy địa chỉ
trình duyệt xem [frontend/README](../../frontend/README.md#rate-limit-theo-trình-duyệt).

## Giới hạn đã biết

Trạng thái rate limit nằm trong bộ nhớ của từng instance. Khi chạy nhiều instance gateway, giới hạn
thực tế nhân theo số instance; cần store dùng chung (Redis) nếu muốn giới hạn toàn cục chính xác.

## Chạy & test

```bash
cd backend
./mvnw -pl api-gateway spring-boot:run     # cần config-server và discovery-server đang chạy
./mvnw -pl api-gateway test                # không cần Docker
```
