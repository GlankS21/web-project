# user-service

Hồ sơ người dùng và sổ địa chỉ giao hàng. Port mặc định: **8082**. Database: `user_db`.

Khoá chính của hồ sơ chính là id tài khoản bên auth-service (claim `sub` của JWT); service này
không lưu mật khẩu hay quyền.

## Tạo hồ sơ

1. Nhận sự kiện `user.registered` trên queue `user.user-registered` → tạo hồ sơ có họ tên.
   Xử lý lỗi: thử lại 3 lần, sau đó chuyển vào `user.user-registered.dlq`.
2. Nếu sự kiện chưa tới hoặc bị mất, `GET /api/users/me` tự tạo hồ sơ trống từ email trong token.
   Khi sự kiện tới muộn, họ tên được điền bổ sung. Xử lý idempotent.

## API (tất cả cần access token)

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/users/me` | đăng nhập | Hồ sơ của tôi |
| PUT | `/api/users/me` | đăng nhập | Cập nhật họ tên, số điện thoại, ngày sinh |
| GET | `/api/users/me/addresses` | đăng nhập | Danh sách địa chỉ (mặc định đứng đầu) |
| GET | `/api/users/me/addresses/{id}` | đăng nhập | Chi tiết |
| POST | `/api/users/me/addresses` | đăng nhập | Thêm địa chỉ |
| PUT | `/api/users/me/addresses/{id}` | đăng nhập | Sửa |
| DELETE | `/api/users/me/addresses/{id}` | đăng nhập | Xoá |
| PATCH | `/api/users/me/addresses/{id}/default` | đăng nhập | Đặt làm mặc định |
| GET | `/api/users?keyword=&page=&size=&sort=` | ADMIN | Tìm theo email/họ tên |
| GET | `/api/users/{userId}` | ADMIN | Chi tiết hồ sơ |

## Quy tắc sổ địa chỉ

- Tối đa 10 địa chỉ mỗi người.
- Địa chỉ đầu tiên luôn là mặc định; luôn có **đúng một** địa chỉ mặc định (đảm bảo bằng partial unique
  index `uk_addresses_one_default_per_user` trong DB).
- Xoá địa chỉ mặc định → địa chỉ mới nhất còn lại trở thành mặc định.
- Muốn đổi mặc định thì chọn địa chỉ khác làm mặc định; không thể bỏ mặc định một cách trống.
- Địa chỉ của người khác trả về 404 (không lộ sự tồn tại).
- `countryCode` theo ISO 3166-1 alpha-2 (ví dụ `VN`); số điện thoại 9–15 chữ số, có thể có `+`.

## Dữ liệu mẫu (Flyway)

- `V1__init_schema.sql`: `user_profiles`, `addresses`
- `V2__seed_admin_profile.sql`: hồ sơ của admin (cùng id với tài khoản admin bên auth-service)

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `USER_DB_PASSWORD`, `ADMIN_EMAIL`;
tuỳ chọn: `USER_SERVICE_PORT`.

## Test

```bash
cd backend
./mvnw -pl user-service test
```

- Unit test: `UserProfileServiceImplTest`, `AddressServiceImplTest`
- Testcontainers: `AddressRepositoryIntegrationTest`, `UserFlowIntegrationTest` (PostgreSQL + RabbitMQ thật)
