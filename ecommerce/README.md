# E-commerce Microservices Platform

Website bán hàng theo kiến trúc microservice: Java 21, Spring Boot 3.5, Spring Cloud 2025.0,
PostgreSQL (database-per-service), RabbitMQ, Next.js.

## Tính năng

- **Khách hàng**: duyệt và tìm sản phẩm (lọc danh mục, thương hiệu, giá), giỏ hàng định giá theo
  thời gian thực, đặt hàng với COD / thẻ / ví điện tử, theo dõi đơn, huỷ đơn, sổ địa chỉ, e-mail
  xác nhận.
- **Quản trị**: sản phẩm và danh mục, tồn kho (nhập/xuất, kiểm kê, cảnh báo sắp hết, sổ biến
  động), xử lý đơn (giao hàng, huỷ), tài khoản và phân quyền, theo dõi e-mail.
- **Kỹ thuật**: 8 service nghiệp vụ, mỗi service một database; Saga choreography qua RabbitMQ với
  transactional outbox, consumer idempotent và DLQ; API gateway với JWT, rate limit, circuit breaker;
  frontend dạng Backend-for-Frontend (token trong cookie HttpOnly); Swagger gộp tại gateway;
  unit test + integration test với Testcontainers + smoke test toàn hệ thống; CI.

## Tài liệu

| Tài liệu | Nội dung |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Sơ đồ hệ thống, dữ liệu, Saga, bảo mật, quan sát, giới hạn |
| [docs/api.md](docs/api.md) | Danh sách endpoint, quyền, mã lỗi (Swagger UI: http://localhost:8080/swagger-ui.html) |
| [docs/testing.md](docs/testing.md) | Chiến lược kiểm thử, cách chạy, độ phủ, smoke test, CI |
| [frontend/README.md](frontend/README.md) | Website, BFF, cookie, làm mới token |
| `backend/<service>/README.md` | Chi tiết từng service: API, sự kiện, cấu hình |

## Tiến độ

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 1 | Parent POM, common-lib, config-server, discovery-server, Docker Compose (PostgreSQL, RabbitMQ) | ✅ Xong |
| 2 | api-gateway, auth-service, user-service | ✅ Xong |
| 3 | product-service, inventory-service + dữ liệu mẫu | ✅ Xong |
| 4 | cart, order, payment, notification + Saga qua RabbitMQ | ✅ Xong |
| 5 | Frontend Next.js (cửa hàng + trang quản trị) | ✅ Xong |
| 6 | Test, Swagger, tài liệu hoàn chỉnh | ✅ Xong |

## Cấu trúc thư mục

```
ecommerce/
├── docker-compose.yml
├── .env.example                  # mẫu biến môi trường (secret) — copy thành .env
├── .github/workflows/ci.yml      # CI: build + test backend, frontend, kiểm tra compose
├── docs/                         # kiến trúc, API, kiểm thử
├── scripts/smoke-test.sh         # kiểm thử end-to-end trên hệ thống đang chạy
├── infrastructure/
│   └── postgres/init/            # script tạo database + role riêng cho từng service
├── backend/
│   ├── pom.xml                   # parent POM (multi-module)
│   ├── mvnw, mvnw.cmd            # Maven Wrapper (không cần cài Maven)
│   ├── common-lib/               # DTO, event, exception, hằng số, correlation id
│   ├── config-server/            # Spring Cloud Config Server (+ config-repo)
│   ├── discovery-server/         # Eureka
│   ├── api-gateway/              # điểm vào duy nhất: route, JWT, CORS, rate limit
│   ├── auth-service/             # đăng ký, đăng nhập, token, phân quyền
│   ├── user-service/             # hồ sơ, sổ địa chỉ
│   ├── product-service/          # danh mục, sản phẩm, ảnh, tìm kiếm
│   ├── inventory-service/        # tồn kho, giữ hàng cho Saga, sổ biến động kho
│   ├── cart-service/             # giỏ hàng, định giá lại theo catalogue và tồn kho
│   ├── order-service/            # đặt hàng, điều phối Saga, vòng đời đơn
│   ├── payment-service/          # thanh toán (cổng giả lập), hoàn tiền
│   └── notification-service/     # e-mail đăng ký và trạng thái đơn
└── frontend/                     # Next.js: cửa hàng, tài khoản, quản trị (BFF tới gateway)
```

## Yêu cầu

- Docker Desktop (Compose v2)
- JDK 21+ (chỉ cần khi chạy/kiểm thử service ngoài Docker)
- Node.js 22+ (chỉ cần khi chạy frontend ngoài Docker)

## Chạy bằng Docker Compose

```bash
cp .env.example .env              # Windows PowerShell: Copy-Item .env.example .env
# sửa toàn bộ giá trị change-me trong .env; JWT_SECRET: openssl rand -base64 64 | tr -d '\r\n'
docker compose up -d --build --wait
docker compose ps                 # tất cả ở trạng thái healthy
```

| Thành phần | URL | Đăng nhập |
|---|---|---|
| **Website (frontend)** | http://localhost:3000 | khách tự đăng ký; admin: `ADMIN_EMAIL` / `ADMIN_PASSWORD` → menu tài khoản → *Quản trị* |
| **API Gateway** | http://localhost:8080 | JWT (đăng nhập qua `/api/auth/login`) |
| Swagger UI (gộp) | http://localhost:8080/swagger-ui.html | nút *Authorize* → dán access token |
| Config Server | http://localhost:8888/application/default | `CONFIG_SERVER_USERNAME` / `CONFIG_SERVER_PASSWORD` |
| Eureka Dashboard | http://localhost:8761 | `EUREKA_USERNAME` / `EUREKA_PASSWORD` |
| RabbitMQ Management | http://localhost:15672 | `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` |
| Mailpit (hộp thư giả) | http://localhost:8025 | — (mọi e-mail của notification-service) |
| PostgreSQL | localhost:5432 | role riêng mỗi service, xem bên dưới |

Các service nghiệp vụ không publish port ra ngoài; chỉ truy cập qua gateway. Website gọi gateway từ
phía server (mô hình Backend-for-Frontend, token nằm trong cookie HttpOnly); chi tiết trong
[frontend/README](frontend/README.md).

| Service | Port nội bộ | Đường dẫn qua gateway | Tài liệu |
|---|---|---|---|
| auth-service | 8081 | `/api/auth/**` | [README](backend/auth-service/README.md) |
| user-service | 8082 | `/api/users/**` | [README](backend/user-service/README.md) |
| product-service | 8083 | `/api/products/**`, `/api/categories/**` | [README](backend/product-service/README.md) |
| inventory-service | 8084 | `/api/inventory/**` | [README](backend/inventory-service/README.md) |
| cart-service | 8085 | `/api/cart/**` | [README](backend/cart-service/README.md) |
| order-service | 8086 | `/api/orders/**` | [README](backend/order-service/README.md) |
| payment-service | 8087 | `/api/payments/**` | [README](backend/payment-service/README.md) |
| notification-service | 8088 | `/api/notifications/**` | [README](backend/notification-service/README.md) |

### Thử nhanh

```bash
# đăng nhập admin (ADMIN_EMAIL / ADMIN_PASSWORD trong .env)
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@shop.local","password":"<ADMIN_PASSWORD>"}'

# đăng ký khách hàng
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"Secret123","firstName":"John","lastName":"Doe"}'

# gọi API cần đăng nhập
curl -s http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"

# duyệt catalogue (công khai): laptop rẻ nhất trước, và số lượng còn hàng
curl -s "http://localhost:8080/api/products?categoryId=4&sort=price,asc&size=5"
curl -s "http://localhost:8080/api/inventory/availability?productIds=7,10,17"

# admin: nhập thêm 20 sản phẩm id 10 vào kho
curl -s -X POST http://localhost:8080/api/inventory/10/adjustments   -H "Authorization: Bearer <adminAccessToken>" -H "Content-Type: application/json"   -d '{"quantityChange":20,"reason":"Nhập hàng"}'
```

### Đặt hàng (Saga)

```bash
TOKEN="<accessToken của khách>"
curl -s -X POST http://localhost:8080/api/cart/items -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json" -d '{"productId":7,"quantity":1}'
curl -s -X POST http://localhost:8080/api/orders -H "Authorization: Bearer $TOKEN"   -H "Content-Type: application/json" -H "Idempotency-Key: checkout-0001"   -d '{"items":[{"productId":7,"quantity":1}],"paymentMethod":"CARD",
       "shippingAddress":{"recipientName":"John Doe","phoneNumber":"+84901234567",
       "addressLine1":"12 Nguyen Hue","city":"Ho Chi Minh City","countryCode":"VN"}}'
# vài giây sau (mỗi bước qua outbox ~1s): PAID, có mã giao dịch; e-mail xuất hiện trong Mailpit
curl -s http://localhost:8080/api/orders/<orderId> -H "Authorization: Bearer $TOKEN"
```

Luồng sự kiện (choreography, không có service điều phối trung tâm):

```
order-service ──order.created──► inventory-service ──stock.reserved──► order-service
     │                                └──stock.reservation-failed──► order-service ──► CANCELLED
     ├──order.created──► cart-service (xoá sản phẩm đã đặt), notification-service
order-service ──order.payment-requested──► payment-service ──payment.succeeded──► order-service ──► PAID
                                                           └──payment.failed────► order-service ──► CANCELLED
PAID      ──order.confirmed──► inventory (trừ kho), notification
CANCELLED ──order.cancelled──► inventory (trả hàng), payment (hoàn tiền nếu đã thu), notification
```

- Mọi sự kiện Saga đi qua **transactional outbox** + publisher confirm: không mất sự kiện khi RabbitMQ gián đoạn.
- Consumer idempotent (giao nhận at-least-once); lỗi được thử lại 3 lần rồi vào `<queue>.dlq`.
- Đơn chưa thanh toán sau 15 phút bị huỷ tự động, hàng giữ được trả lại.
- Thử nhánh thất bại: đặt đơn `CARD` trên 50 triệu (vượt `PAYMENT_SIMULATOR_MAX_AMOUNT`), hoặc sản phẩm id 17 (hết hàng).

### Dữ liệu mẫu

Flyway tự nạp khi service khởi động lần đầu:

| Service | Dữ liệu |
|---|---|
| auth-service | quyền `CUSTOMER`, `ADMIN`; tài khoản admin (`ADMIN_EMAIL` / `ADMIN_PASSWORD`) |
| user-service | hồ sơ admin |
| product-service | 12 danh mục (4 gốc × 2 con), 24 sản phẩm điện tử giá VND (22 đang bán, 1 nháp, 1 ngừng bán), ảnh placeholder |
| inventory-service | tồn kho cho 24 sản phẩm trên (có sản phẩm sắp hết: id 6, 10; hết hàng: id 17) |

### Database-per-service

Một PostgreSQL instance chứa database riêng cho từng service; mỗi database thuộc một role riêng,
role này không truy cập được database khác:

| Service | Database | Role | Mật khẩu (.env) |
|---|---|---|---|
| auth-service | `auth_db` | `auth_user` | `AUTH_DB_PASSWORD` |
| user-service | `user_db` | `user_user` | `USER_DB_PASSWORD` |
| product-service | `product_db` | `product_user` | `PRODUCT_DB_PASSWORD` |
| inventory-service | `inventory_db` | `inventory_user` | `INVENTORY_DB_PASSWORD` |
| cart-service | `cart_db` | `cart_user` | `CART_DB_PASSWORD` |
| order-service | `order_db` | `order_user` | `ORDER_DB_PASSWORD` |
| payment-service | `payment_db` | `payment_user` | `PAYMENT_DB_PASSWORD` |
| notification-service | `notification_db` | `notification_user` | `NOTIFICATION_DB_PASSWORD` |

Script khởi tạo chỉ chạy **lần đầu** khi volume trống. Nếu đổi mật khẩu trong `.env` sau đó,
cần tạo lại volume: `docker compose down -v` (xoá toàn bộ dữ liệu).

## Chạy local (service trên máy, hạ tầng trong Docker)

```bash
docker compose up -d postgres rabbitmq mailpit
cd backend
./mvnw clean install                        # build + test toàn bộ, cài common-lib vào ~/.m2
./mvnw -pl config-server spring-boot:run    # terminal 1
./mvnw -pl discovery-server spring-boot:run # terminal 2
./mvnw -pl auth-service spring-boot:run     # terminal 3
./mvnw -pl user-service spring-boot:run     # terminal 4
./mvnw -pl product-service spring-boot:run  # terminal 5
./mvnw -pl inventory-service spring-boot:run # terminal 6
./mvnw -pl cart-service spring-boot:run     # terminal 7
./mvnw -pl order-service spring-boot:run    # terminal 8
./mvnw -pl payment-service spring-boot:run  # terminal 9
./mvnw -pl notification-service spring-boot:run # terminal 10
./mvnw -pl api-gateway spring-boot:run      # terminal 11

cd ../frontend && npm ci && npm run dev     # terminal 12 → http://localhost:3000
```

Test có dùng Testcontainers (PostgreSQL, RabbitMQ thật) và tự bỏ qua khi Docker không chạy.

Profile mặc định là `dev`; ở profile này các service tự đọc file `.env` ở thư mục gốc dự án,
nên không cần export biến môi trường thủ công.

## Kiểm thử

```bash
cd backend && ./mvnw clean verify           # unit + integration (Testcontainers), báo cáo độ phủ JaCoCo
cd frontend && npm run typecheck && npm run lint && npm test && npm run build

# toàn hệ thống, sau "docker compose up -d --build --wait" (tạo dữ liệu thử: chỉ dùng ở môi trường local/test)
ADMIN_EMAIL=admin@shop.local ADMIN_PASSWORD='<ADMIN_PASSWORD>' scripts/smoke-test.sh
```

Chi tiết: [docs/testing.md](docs/testing.md).

## Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `required variable ... is missing a value` khi `docker compose up` | `.env` thiếu biến | Chép các biến còn thiếu từ `.env.example` |
| Service khởi động lỗi `password authentication failed` | Đổi mật khẩu database sau khi volume đã được tạo | `docker compose down -v` rồi chạy lại (xoá dữ liệu) |
| Mọi request đăng nhập bị 401 / token không hợp lệ | `JWT_SECRET` có ký tự xuống dòng (`\r` trên Windows) hoặc quá ngắn | Tạo lại bằng `openssl rand -base64 64 \| tr -d '\r\n'` |
| Gateway trả `503` ngay sau khi khởi động | Eureka chưa cập nhật danh sách service (khoảng 30 giây) | Đợi rồi thử lại |
| Website báo "Dịch vụ tạm thời gián đoạn" | Gateway hoặc service liên quan chưa healthy | `docker compose ps`, xem `docker compose logs api-gateway` |
| Đơn hàng đứng ở `PENDING` / `AWAITING_PAYMENT` | inventory hoặc payment-service chưa chạy, hoặc sự kiện vào DLQ | `docker compose ps`; xem queue `*.dlq` ở RabbitMQ Management |
| Không thấy e-mail | notification-service hoặc Mailpit chưa chạy | http://localhost:8025, admin → *E-mail* để gửi lại |
| Bị `429` khi thử nhiều lần | Rate limit (đăng nhập/đăng ký 10 lần/phút) | Đợi theo header `Retry-After`; khi phát triển có thể đặt `RATE_LIMIT_ENABLED=false` |

Mỗi phản hồi lỗi có `correlationId`; tìm id này trong log (`docker compose logs | grep <id>`)
để thấy toàn bộ đường đi của request qua các service.

## Quy ước chung

- **Response thành công**: `ApiResponse { success, message, data, timestamp }`
- **Response lỗi**: `ErrorResponse { timestamp, status, error, code, message, path, correlationId, violations[] }`
- **Correlation id**: header `X-Correlation-Id`, được sinh nếu thiếu, ghi vào mọi dòng log
  (`[service-name,correlationId]`), truyền tiếp qua Feign và RabbitMQ.
- **Secret**: không có trong mã nguồn hay config-repo; luôn lấy từ biến môi trường.
- **Xác thực**: access token JWT HS256 (15 phút) + refresh token opaque xoay vòng (7 ngày).
  Gateway kiểm tra JWT, mỗi service kiểm tra lại (defense in depth); quyền chi tiết bằng `@PreAuthorize`.
- **Phân trang**: `?page=0&size=20&sort=createdAt,desc` (tối đa 100 phần tử/trang), trả `PageResponse`.
- **Sự kiện**: RabbitMQ, mỗi domain một topic exchange, mỗi queue có DLQ (danh sách trong
  [common-lib](backend/common-lib/README.md)). Sự kiện Saga của order, inventory, payment đi qua
  transactional outbox (không mất khi broker gián đoạn); consumer xử lý idempotent vì giao nhận là at-least-once.
- **Gọi đồng bộ giữa service**: OpenFeign qua Eureka + Resilience4j circuit breaker (cart, order → product,
  inventory); lỗi phụ thuộc trả `503` thay vì treo request.
