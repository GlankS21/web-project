# notification-service

Gửi e-mail khi khách đăng ký và theo diễn biến đơn hàng. Port mặc định: **8088**. Database: `notification_db`.

## E-mail

| Sự kiện (queue) | E-mail |
|---|---|
| `user.registered` (`notification.user-events`) | Chào mừng |
| `order.created` (`notification.order-events`) | Đã nhận đơn: danh sách sản phẩm, tổng tiền |
| `order.confirmed` (`notification.order-events`) | Thanh toán thành công, đơn được xác nhận |
| `order.cancelled` (`notification.order-events`) | Đơn bị huỷ kèm lý do |

Nội dung là văn bản thuần, tiếng Việt, số tiền dạng `25.140.000 VND`. Tên cửa hàng lấy từ `SHOP_NAME`.

## Độ tin cậy

- Mỗi e-mail được lưu vào bảng `notifications` trước khi gửi; unique `event_id` nên sự kiện gửi lại
  không sinh e-mail thứ hai.
- Gửi SMTP **ngoài transaction**. Gửi lỗi (SMTP ngừng) không làm hỏng message: e-mail được đánh dấu `FAILED`
  kèm lỗi, và `NotificationRetryJob` gửi lại mỗi `app.notification.retry.interval` (1 phút) cho tới
  `max-attempts` (5) lần. Sau đó admin có thể gửi lại thủ công.
- E-mail còn `PENDING` (service dừng giữa lúc lưu và gửi) cũng được job gửi lại.
- Lỗi xử lý message khác: thử lại 3 lần rồi vào `<queue>.dlq`.

## Môi trường phát triển: Mailpit

Docker Compose chạy [Mailpit](https://mailpit.axllent.org/) — SMTP giả bắt mọi e-mail, không gửi ra ngoài.
Xem thư tại **http://localhost:8025**. Khi chạy service ngoài Docker: `docker compose up -d mailpit`,
service gửi tới SMTP `localhost:1025` (cổng `MAILPIT_SMTP_PORT`).

`MAIL_ENABLED=false` thay SMTP bằng việc ghi e-mail ra log.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/notifications?page=&size=` | đăng nhập | E-mail đã gửi cho tôi, mới nhất trước |
| GET | `/api/notifications/admin?status=&type=` | ADMIN | Tất cả e-mail, lọc `PENDING`/`SENT`/`FAILED` và loại |
| POST | `/api/notifications/admin/{id}/resend` | ADMIN | Gửi lại một e-mail |

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `NOTIFICATION_DB_PASSWORD`; tuỳ chọn:
`NOTIFICATION_SERVICE_PORT`, `MAIL_HOST` (mặc định `localhost`, Docker: `mailpit`), `MAIL_PORT` (1025),
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_ENABLED`, `MAIL_FROM`, `SHOP_NAME`.

## Test

```bash
cd backend
./mvnw -pl notification-service test
```

- Unit test: `NotificationTemplatesTest` (nội dung, định dạng tiền)
- Testcontainers: `NotificationIntegrationTest` — sự kiện thật trên RabbitMQ, SMTP giả lập bằng mock:
  không gửi trùng, gửi lại khi lỗi, giới hạn số lần, admin gửi lại
