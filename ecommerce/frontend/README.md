# frontend

Website bán hàng **TechShop**: Next.js 16 (App Router), React 19, TypeScript, Tailwind CSS 4.
Port mặc định: **3000**.

## Trang

| Khu vực | Đường dẫn | Ghi chú |
|---|---|---|
| Công khai | `/`, `/products`, `/products/[slug]` | Lọc theo danh mục, thương hiệu, khoảng giá; sắp xếp; phân trang; tồn kho thời gian thực |
| | `/login`, `/register` | `?next=` chỉ nhận đường dẫn nội bộ (chống open redirect) |
| Khách hàng | `/cart` | Giá và tồn kho được cart-service tính lại mỗi lần tải; chặn đặt hàng khi thiếu hàng |
| | `/checkout` | Chọn địa chỉ đã lưu hoặc nhập mới, phương thức thanh toán; `Idempotency-Key` sinh một lần cho mỗi lần vào trang, nên bấm hai lần không tạo hai đơn |
| | `/orders`, `/orders/[id]` | Trang chi tiết tự làm mới mỗi 2 giây khi Saga đang chạy (`PENDING`, `AWAITING_PAYMENT`); huỷ đơn chưa thanh toán |
| | `/account`, `/account/addresses`, `/account/password`, `/account/notifications` | Đổi mật khẩu thu hồi mọi phiên, người dùng được đưa về trang đăng nhập |
| Quản trị (`ROLE_ADMIN`) | `/admin` | Số đơn chờ giao, chờ thanh toán, sản phẩm sắp hết, e-mail lỗi; đơn gần đây |
| | `/admin/orders` | Tìm theo mã đơn / e-mail, chuyển `PAID → SHIPPED → DELIVERED`, huỷ |
| | `/admin/products`, `/admin/categories` | Thêm, sửa, đổi trạng thái sản phẩm; cây danh mục hai cấp |
| | `/admin/inventory` | Nhập/xuất kho, kiểm kê, ngưỡng cảnh báo, lịch sử biến động |
| | `/admin/accounts`, `/admin/notifications` | Cấp/thu hồi quyền, khoá tài khoản; gửi lại e-mail |

## Kiến trúc: Backend-for-Frontend

```
Trình duyệt ──(cookie HttpOnly, cùng origin)──► Next.js ──(Authorization: Bearer)──► API gateway ──► services
             /, /products, ... (trang render ở server)
             /api/*  (route handler chuyển tiếp)
```

- **Trình duyệt không bao giờ thấy token.** Access token và refresh token nằm trong cookie
  `shop_at`, `shop_rt` (`HttpOnly`, `SameSite=Lax`, `Secure` khi chạy HTTPS). JavaScript phía
  trình duyệt chỉ gọi `/api/*` cùng origin; route handler `src/app/api/[...path]/route.ts` gắn
  token từ cookie rồi chuyển tiếp tới gateway.
- **Đăng nhập / đăng ký / đăng xuất** có route handler riêng (`src/app/api/auth/*`) để đặt và
  xoá cookie. `/api/auth/refresh` không được chuyển tiếp: chỉ server làm mới token.
- **CSRF**: mọi request khác `GET` phải có `Origin` trùng host (hoặc `Sec-Fetch-Site: same-origin`),
  nếu không trả 403. Cùng với `SameSite=Lax`, trang khác không thể gửi request thay người dùng.
- **Phân quyền ở frontend chỉ phục vụ giao diện.** `src/proxy.ts` đọc JWT (không kiểm tra chữ ký)
  để chuyển người chưa đăng nhập về `/login` và người không phải admin khỏi `/admin`. Quyền thật
  do gateway và từng service kiểm tra.
- **Header bảo mật** (`next.config.ts`): Content-Security-Policy (script, style, request chỉ từ
  `'self'`; ảnh từ `https:`; `frame-ancestors 'none'`), `X-Frame-Options: DENY`, `nosniff`,
  `Referrer-Policy`, `Permissions-Policy`. `script-src` vẫn có `'unsafe-inline'` vì payload React
  Server Components được nhúng dạng script inline; muốn bỏ cần chuyển sang CSP dùng nonce.

### Làm mới token

Access token sống 15 phút. Khi token sắp hết hạn (còn dưới 30 giây) mà vẫn còn refresh token,
`src/proxy.ts` gọi `/api/auth/refresh` trước khi render trang hoặc chuyển tiếp API, rồi ghi cookie mới.

auth-service xoay vòng refresh token và coi việc dùng lại token cũ là dấu hiệu bị đánh cắp (thu hồi
mọi phiên). Một lần tải trang phát sinh nhiều request đồng thời cùng mang token cũ, nên
`src/lib/server/token-refresh.ts` gom chúng lại (single-flight): chỉ một lời gọi refresh, các
request khác (kể cả request đến trong 30 giây sau) dùng chung kết quả.

> **Giới hạn**: bộ nhớ đệm này nằm trong tiến trình. Chạy **một** instance frontend, hoặc dùng
> sticky session theo người dùng khi mở rộng; nếu không, hai instance có thể cùng làm mới một token
> và auth-service sẽ thu hồi phiên.

### Rate limit theo trình duyệt

Mọi request tới gateway đều đi ra từ container frontend, nên nếu không làm gì thêm, gateway sẽ áp
một hạn mức chung cho tất cả người dùng. Để tránh điều đó, frontend gửi kèm
`X-Client-Ip` (địa chỉ trình duyệt) và `X-Proxy-Secret`. Gateway chỉ tin `X-Client-Ip` khi secret
trùng `TRUSTED_PROXY_SECRET` của nó.

Địa chỉ trình duyệt lấy từ `X-Forwarded-For`. Next.js chỉ tự đặt header này khi request chưa có,
nên giá trị trình duyệt tự gửi được giữ nguyên. Vì vậy:

- `TRUSTED_PROXY_HOPS=0` (mặc định): lấy phần tử cuối. Khi trình duyệt kết nối thẳng tới frontend,
  họ có thể **tự chọn** địa chỉ này để lách giới hạn. Chấp nhận được khi phát triển.
- Production: đặt frontend sau reverse proxy (nginx, load balancer) có ghi thêm vào
  `X-Forwarded-For`, rồi đặt `TRUSTED_PROXY_HOPS` bằng số proxy đó. Frontend lấy phần tử thứ
  `TRUSTED_PROXY_HOPS` tính từ cuối, tức địa chỉ do proxy ngoài cùng ghi vào, không giả mạo được.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `API_GATEWAY_URL` | `http://localhost:8080` | Địa chỉ gateway, chỉ server dùng |
| `TRUSTED_PROXY_SECRET` | (trống: tắt) | Trùng với gateway |
| `TRUSTED_PROXY_HOPS` | `0` | Số reverse proxy phía trước frontend |

Không có biến `NEXT_PUBLIC_*`: trình duyệt không cần biết địa chỉ backend.

## Chạy & kiểm thử

```bash
cd frontend
npm ci
cp .env.example .env.local     # điền TRUSTED_PROXY_SECRET giống .env ở thư mục gốc (hoặc để trống)
npm run dev                    # http://localhost:3000, cần gateway ở :8080

npm run typecheck              # tsc --noEmit
npm run lint                   # ESLint (next/core-web-vitals + typescript)
npm test                       # Vitest: phân tích JWT, địa chỉ client, refresh single-flight, redirect an toàn, định dạng
npm run build && npm start     # bản production (output: standalone)
```

Trong Docker Compose, service `frontend` được build từ `Dockerfile` (multi-stage,
`node:22-alpine`, chạy bằng user không phải root) và có healthcheck `GET /api/health`.

## Cấu trúc

```
src/
├── proxy.ts                 # làm mới token, chuyển hướng trang cần đăng nhập / quyền admin
├── app/                     # trang (App Router) và route handler /api/*
├── components/              # UI dùng chung, giỏ hàng, đặt hàng, đơn hàng, tài khoản, quản trị
└── lib/
    ├── types.ts             # kiểu DTO của backend
    ├── format.ts            # tiền tệ, ngày giờ (Asia/Ho_Chi_Minh), nhãn trạng thái tiếng Việt
    ├── session.ts           # đọc JWT cho giao diện
    ├── errors.ts            # ApiError, thông báo lỗi tiếng Việt
    ├── client/api.ts        # fetch phía trình duyệt tới /api/*
    └── server/              # chỉ chạy trên server: gọi gateway, cookie, refresh, IP client
```
