# Kiểm thử

## Các tầng kiểm thử

| Tầng | Kiểm tra gì | Công cụ | Cần Docker |
|---|---|---|---|
| Unit | Quy tắc nghiệp vụ trong service và entity: định giá giỏ hàng, chuyển trạng thái đơn, giữ/trả hàng, thanh toán giả lập, xoay vòng token, mẫu e-mail… | JUnit 5, Mockito, AssertJ | Không |
| Integration | Một service thật với PostgreSQL và RabbitMQ thật: API qua MockMvc, bảo mật JWT, migration Flyway, consumer và outbox của Saga | Spring Boot Test, Testcontainers | Có (tự bỏ qua khi không có Docker) |
| Gateway | Định tuyến, xác thực JWT, rate limit, địa chỉ client sau proxy tin cậy | WebTestClient, unit test | Không |
| Frontend | Đọc JWT, lấy IP client, làm mới token single-flight, chống open redirect, định dạng tiền/ngày, tham số URL | Vitest | Không |
| End-to-end | Toàn hệ thống trong Docker qua website: mua hàng trọn Saga, quản trị, CSRF, làm mới token đồng thời, rate limit, Swagger | `scripts/smoke-test.sh` (curl) | Có |

Service được gọi qua Feign (product, inventory) được giả lập ở mức Feign client trong integration
test, nên mỗi service được kiểm thử độc lập; sự phối hợp giữa các service được kiểm chứng bằng
smoke test.

## Số lượng

Kết quả `./mvnw clean verify` (21/10/2026, Docker đang chạy, không test nào bị bỏ qua). Độ phủ lấy từ
JaCoCo, chỉ tính test của chính module:

| Module | Test | Độ phủ dòng | Độ phủ nhánh |
|---|---:|---:|---:|
| common-lib | 20 | 35% | 52% |
| config-server | 3 | 100% | — |
| discovery-server | 3 | 100% | — |
| api-gateway | 15 | 77% | 54% |
| auth-service | 35 | 88% | 86% |
| user-service | 25 | 88% | 73% |
| product-service | 32 | 87% | 71% |
| inventory-service | 27 | 91% | 73% |
| cart-service | 13 | 95% | 85% |
| order-service | 20 | 92% | 76% |
| payment-service | 13 | 92% | 72% |
| notification-service | 12 | 92% | 67% |
| **Backend** | **218** | | |
| Frontend (Vitest) | 28 | | |
| Smoke test | 64 bước | | |

Độ phủ của common-lib thấp vì phần lớn mã của nó (auto-configuration, truyền correlation id qua
Feign và RabbitMQ, xử lý lỗi chung) chỉ chạy bên trong các service và được kiểm chứng bởi integration
test của các service đó, mà JaCoCo không cộng vào module common-lib.

## Chạy

### Backend

```bash
cd backend
./mvnw clean verify                          # toàn bộ test + báo cáo độ phủ
./mvnw -pl order-service -am verify          # một service (kèm common-lib)
./mvnw -pl cart-service test -Dtest=CartServiceImplTest   # một lớp test
```

- Docker Desktop phải đang chạy để các integration test được thực thi; nếu không, chúng được đánh
  dấu *skipped* và build vẫn thành công.
- Báo cáo độ phủ: `backend/<module>/target/site/jacoco/index.html` (mapper sinh tự động của
  MapStruct và lớp `*Application` không được tính).

### Frontend

```bash
cd frontend
npm run typecheck && npm run lint && npm test && npm run build
```

### Smoke test toàn hệ thống

```bash
docker compose up -d --build --wait
ADMIN_EMAIL=admin@shop.local ADMIN_PASSWORD='<ADMIN_PASSWORD>' scripts/smoke-test.sh
```

Script tạo dữ liệu thật (danh mục, sản phẩm, khách hàng, đơn hàng), nên chỉ chạy trên môi trường
local hoặc test. Biến tuỳ chọn: `FRONTEND_URL` (mặc định `http://localhost:3000`), `GATEWAY_URL`
(`http://localhost:8080`). Bước kiểm tra rate limit theo trình duyệt cần `TRUSTED_PROXY_SECRET` đã
được đặt và `TRUSTED_PROXY_HOPS=0`.

Các bước kiểm tra:

1. OpenAPI của 8 service qua gateway và Swagger UI.
2. Trang công khai, header bảo mật, chuyển hướng khi chưa đăng nhập, chặn request khác origin.
3. Admin: đăng nhập (cookie HttpOnly), mọi trang quản trị, tạo danh mục và sản phẩm, đặt tồn kho.
4. Khách: đăng ký, không vào được trang/API quản trị, giỏ hàng, lưu địa chỉ, đặt đơn thẻ,
   `Idempotency-Key` trả lại đúng đơn, Saga đưa đơn sang `PAID`, thanh toán `SUCCEEDED`, sản phẩm
   rời giỏ, admin chuyển đơn sang `SHIPPED`.
5. Access token hết hạn với 5 request đồng thời: tất cả thành công, chỉ một refresh token mới,
   phiên không bị thu hồi.
6. Đăng xuất; rate limit chặn một địa chỉ mà không ảnh hưởng địa chỉ khác.

## CI

`.github/workflows/ci.yml` chạy trên mỗi push vào `main` và mỗi pull request:

| Job | Nội dung |
|---|---|
| Backend | `mvnw clean verify` trên JDK 21 (runner có Docker nên integration test chạy đầy đủ), đính kèm báo cáo JaCoCo |
| Frontend | `npm ci`, typecheck, lint, Vitest, `next build` trên Node 22 |
| Compose | `docker compose config` với `.env.example` |

Smoke test không chạy trong CI vì cần dựng 15 container; chạy nó trước khi phát hành.
