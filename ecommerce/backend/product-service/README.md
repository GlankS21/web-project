# product-service

Danh mục sản phẩm: cây danh mục, sản phẩm, ảnh, tìm kiếm/lọc. Port mặc định: **8083**. Database: `product_db`.

Đọc dữ liệu là công khai (gateway mở `GET /api/products/**`, `GET /api/categories/**`); mọi thao tác ghi
cần `ROLE_ADMIN`. Khách hàng chỉ thấy sản phẩm `ACTIVE`; admin (gửi kèm access token) thấy cả `DRAFT`
và `ARCHIVED` — sản phẩm ẩn trả về 404 với người khác.

## Mô hình

| Khái niệm | Quy tắc |
|---|---|
| Danh mục | Cây **2 cấp**: danh mục gốc và danh mục con trực tiếp. Lọc theo danh mục gốc bao gồm cả danh mục con |
| Slug | Tự sinh từ tên nếu không gửi (bỏ dấu tiếng Việt: "Điện thoại" → `dien-thoai`); trùng slug sản phẩm thì nối thêm SKU. Khi cập nhật, slug chỉ đổi nếu gửi rõ ràng |
| SKU | `^[A-Z0-9][A-Z0-9-]{2,63}$`, duy nhất, **không đổi được** sau khi tạo (kho và đơn hàng tham chiếu) |
| Giá | `price > 0`, tối đa 2 chữ số thập phân; `compareAtPrice` (giá gạch) phải lớn hơn `price`. Một loại tiền cho cả cửa hàng: `CATALOG_CURRENCY` (mặc định `VND`) |
| Trạng thái | `DRAFT` (mặc định khi tạo) → `ACTIVE` → `ARCHIVED`. `DELETE` = chuyển sang `ARCHIVED` (xoá mềm) |
| Ảnh | Tối đa 10 URL http(s), ảnh đầu tiên là thumbnail. Cập nhật có gửi `images` thì thay toàn bộ, không gửi thì giữ nguyên |
| Xoá danh mục | Bị từ chối (422) khi còn danh mục con hoặc còn sản phẩm (kể cả đã archive) |

Tạo sản phẩm phát sự kiện `product.created` (exchange `shop.product.events`) sau khi commit;
inventory-service mở bản ghi tồn kho rỗng cho sản phẩm đó.

## API

| Method | Đường dẫn | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/products?keyword=&categoryId=&brand=&minPrice=&maxPrice=&page=&size=&sort=` | công khai | Tìm kiếm; `keyword` khớp tên/SKU/brand; sort theo `price`, `name`, `createdAt`, `updatedAt`, `sku` |
| GET | `/api/products?status=DRAFT` | ADMIN | Lọc theo trạng thái (bị bỏ qua với người không phải admin) |
| GET | `/api/products/{id}` | công khai | Chi tiết (kèm ảnh, danh mục) |
| GET | `/api/products/slug/{slug}` | công khai | Chi tiết theo slug (cho URL frontend) |
| GET | `/api/products/by-ids?ids=1,2,3` | công khai | Tối đa 100 id, giữ thứ tự, bỏ id không tồn tại/ẩn — cart/order dùng để lấy giá hiện tại |
| POST | `/api/products` | ADMIN | Tạo |
| PUT | `/api/products/{id}` | ADMIN | Cập nhật (phải gửi đúng SKU hiện tại) |
| PATCH | `/api/products/{id}/status` | ADMIN | `{"status":"ACTIVE"}` |
| DELETE | `/api/products/{id}` | ADMIN | Archive |
| GET | `/api/categories` | công khai | Cây danh mục |
| GET | `/api/categories/{id}`, `/api/categories/slug/{slug}` | công khai | Chi tiết + danh mục con |
| POST / PUT / DELETE | `/api/categories[/{id}]` | ADMIN | Quản lý danh mục (PUT không có `parentId` ⇒ thành danh mục gốc) |

Ví dụ:

```bash
curl -s "http://localhost:8080/api/products?categoryId=4&sort=price,asc&size=5"
curl -s -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <adminAccessToken>" -H "Content-Type: application/json" \
  -d '{"sku":"ACC-USB-HUB","name":"USB-C Hub 7 in 1","brand":"UGREEN","price":590000,
       "categoryId":11,"status":"ACTIVE","images":[{"url":"https://picsum.photos/seed/hub/800/800"}]}'
```

## Dữ liệu mẫu (Flyway)

- `V1__init_schema.sql`: `categories`, `products`, `product_images` (CHECK cho giá, trạng thái; unique SKU/slug)
- `V2__seed_catalog.sql`: 4 danh mục gốc, 8 danh mục con, **24 sản phẩm** (22 `ACTIVE`, 1 `DRAFT` id 23,
  1 `ARCHIVED` id 24), mỗi sản phẩm 2 ảnh placeholder từ picsum.photos. Id cố định vì inventory-service
  seed tồn kho cho cùng id.

## Biến môi trường

`CONFIG_SERVER_*`, `EUREKA_*`, `RABBITMQ_*`, `JWT_SECRET`, `PRODUCT_DB_PASSWORD`;
tuỳ chọn: `CATALOG_CURRENCY` (mặc định `VND`), `PRODUCT_SERVICE_PORT`.

## Test

```bash
cd backend
./mvnw -pl product-service test
```

- Unit test: `ProductServiceImplTest`, `CategoryServiceImplTest`, `SlugUtilsTest`
- Testcontainers: `ProductCatalogIntegrationTest` (PostgreSQL + RabbitMQ thật, dữ liệu mẫu, sự kiện `product.created`)
