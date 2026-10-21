# auth-service

Đăng ký, đăng nhập, phát/xoay vòng/thu hồi token, phân quyền. Port mặc định: **8081**. Database: `auth_db`.

## Token

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT HS256 | Chuỗi ngẫu nhiên 256 bit (opaque) |
| Thời hạn | 15 phút (`JWT_ACCESS_TOKEN_TTL`) | 7 ngày (`JWT_REFRESH_TOKEN_TTL`), gia hạn mỗi lần refresh |
| Lưu trữ | Không lưu | Chỉ lưu SHA-256 của token |
| Thu hồi | Không (hết hạn tự nhiên) | Có: logout, logout-all, đổi mật khẩu, khoá tài khoản |

Claim của access token: `sub` (id tài khoản), `email`, `roles` (ví dụ `["ROLE_CUSTOMER"]`), `token_type=access`, `iss`, `jti`.

**Xoay vòng refresh token:** mỗi lần `/refresh`, token cũ bị thu hồi và trỏ tới token mới. Nếu một token
đã bị thu hồi được dùng lại (dấu hiệu token bị đánh cắp), toàn bộ phiên của tài khoản bị thu hồi.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | công khai | Tạo tài khoản `ROLE_CUSTOMER`, trả token |
| POST | `/api/auth/login` | công khai | Đăng nhập |
| POST | `/api/auth/refresh` | công khai | Đổi refresh token lấy cặp token mới |
| POST | `/api/auth/logout` | công khai | Thu hồi refresh token được gửi lên |
| POST | `/api/auth/logout-all` | đăng nhập | Thu hồi mọi phiên |
| GET | `/api/auth/me` | đăng nhập | Thông tin tài khoản |
| PUT | `/api/auth/me/password` | đăng nhập | Đổi mật khẩu (thu hồi mọi phiên) |
| GET | `/api/auth/admin/accounts?email=&page=&size=&sort=` | ADMIN | Tìm tài khoản |
| GET | `/api/auth/admin/accounts/{id}` | ADMIN | Chi tiết |
| PUT | `/api/auth/admin/accounts/{id}/roles` | ADMIN | Đặt lại quyền (không tự gỡ quyền admin của mình) |
| PATCH | `/api/auth/admin/accounts/{id}/status` | ADMIN | Khoá/mở (không tự khoá mình) |

Mật khẩu: 8–72 ký tự, có ít nhất một chữ cái và một chữ số; hash bằng BCrypt.
Email sai và mật khẩu sai trả cùng một thông báo, và thời gian xử lý tương đương nhau.

## Sự kiện

Publish `user.registered` (`UserRegisteredEvent`) lên exchange `shop.user.events` **sau khi transaction commit**.
Nếu RabbitMQ không khả dụng thì sự kiện bị mất; user-service tự tạo hồ sơ ở lần gọi `GET /api/users/me` đầu tiên.

## Dữ liệu mẫu (Flyway)

- `V1__init_schema.sql`: bảng `roles`, `user_accounts`, `user_account_roles`, `refresh_tokens`
- `V2__seed_roles_and_admin.sql`: `ROLE_CUSTOMER`, `ROLE_ADMIN` và tài khoản admin
  (id `00000000-0000-0000-0000-000000000001`) với email/mật khẩu lấy từ `ADMIN_EMAIL` / `ADMIN_PASSWORD`,
  hash bcrypt bằng `pgcrypto`. Seed chỉ chạy một lần; đổi biến sau đó không đổi mật khẩu admin.

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `AUTH_DB_PASSWORD`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`;
tuỳ chọn: `JWT_ISSUER`, `JWT_ACCESS_TOKEN_TTL`, `JWT_REFRESH_TOKEN_TTL`, `AUTH_SERVICE_PORT`.

## Test

```bash
cd backend
./mvnw -pl auth-service test
```

- Unit test (Mockito): `AuthServiceImplTest`, `TokenServiceImplTest`, `AccountAdminServiceImplTest`
- Testcontainers (tự bỏ qua khi không có Docker): `UserAccountRepositoryIntegrationTest`,
  `RefreshTokenRepositoryIntegrationTest`, `AuthFlowIntegrationTest` (PostgreSQL + RabbitMQ thật)
