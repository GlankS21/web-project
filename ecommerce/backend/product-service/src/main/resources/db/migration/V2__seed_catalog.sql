-- Sample catalogue. Product ids are fixed because inventory-service seeds stock for the same ids
-- (inventory-service V2__seed_stock.sql). Images are placeholders served by picsum.photos.

INSERT INTO categories (id, name, slug, description, parent_id, sort_order, created_at, updated_at)
VALUES (1, 'Điện thoại & Tablet', 'dien-thoai-tablet', 'Điện thoại thông minh và máy tính bảng', NULL, 1, now(), now()),
       (4, 'Laptop', 'laptop', 'Máy tính xách tay', NULL, 2, now(), now()),
       (7, 'Âm thanh', 'am-thanh', 'Tai nghe và loa', NULL, 3, now(), now()),
       (10, 'Phụ kiện', 'phu-kien', 'Sạc, cáp, chuột, bàn phím', NULL, 4, now(), now());

INSERT INTO categories (id, name, slug, description, parent_id, sort_order, created_at, updated_at)
VALUES (2, 'Điện thoại', 'dien-thoai', NULL, 1, 1, now(), now()),
       (3, 'Máy tính bảng', 'may-tinh-bang', NULL, 1, 2, now(), now()),
       (5, 'Laptop văn phòng', 'laptop-van-phong', NULL, 4, 1, now(), now()),
       (6, 'Laptop gaming', 'laptop-gaming', NULL, 4, 2, now(), now()),
       (8, 'Tai nghe', 'tai-nghe', NULL, 7, 1, now(), now()),
       (9, 'Loa', 'loa', NULL, 7, 2, now(), now()),
       (11, 'Sạc & cáp', 'sac-cap', NULL, 10, 1, now(), now()),
       (12, 'Chuột & bàn phím', 'chuot-ban-phim', NULL, 10, 2, now(), now());

INSERT INTO products (id, sku, name, slug, short_description, description, brand, price, compare_at_price,
                      category_id, status, created_at, updated_at)
VALUES (1, 'PHN-IP15-128', 'iPhone 15 128GB', 'iphone-15-128gb',
        'Chip A16 Bionic, camera 48MP, Dynamic Island',
        'Màn hình Super Retina XDR 6.1 inch, cổng USB-C, pin dùng cả ngày.', 'Apple', 19990000, 22990000, 2, 'ACTIVE', now(), now()),
       (2, 'PHN-S24-256', 'Samsung Galaxy S24 256GB', 'samsung-galaxy-s24-256gb',
        'Galaxy AI, màn hình 120Hz, camera 50MP',
        'Màn hình Dynamic AMOLED 2X 6.2 inch, chip Exynos 2400, 8GB RAM.', 'Samsung', 18490000, 22990000, 2, 'ACTIVE', now(), now()),
       (3, 'PHN-PX8-128', 'Google Pixel 8 128GB', 'google-pixel-8-128gb',
        'Tensor G3, camera xử lý AI, 7 năm cập nhật',
        'Màn hình Actua 6.2 inch 120Hz, chống nước IP68.', 'Google', 15990000, NULL, 2, 'ACTIVE', now(), now()),
       (4, 'PHN-RN13-128', 'Xiaomi Redmi Note 13 128GB', 'xiaomi-redmi-note-13-128gb',
        'Màn hình AMOLED 120Hz, sạc nhanh 33W',
        'Camera 108MP, pin 5000mAh, 6GB RAM.', 'Xiaomi', 4890000, 5290000, 2, 'ACTIVE', now(), now()),
       (5, 'TAB-IPAD10-64', 'iPad Gen 10 Wi-Fi 64GB', 'ipad-gen-10-wifi-64gb',
        'Màn hình Liquid Retina 10.9 inch, chip A14',
        'Hỗ trợ Apple Pencil (USB-C) và Magic Keyboard Folio.', 'Apple', 9990000, 11990000, 3, 'ACTIVE', now(), now()),
       (6, 'TAB-TABS9-128', 'Samsung Galaxy Tab S9 128GB', 'samsung-galaxy-tab-s9-128gb',
        'Màn hình Dynamic AMOLED 11 inch, kèm S Pen',
        'Chip Snapdragon 8 Gen 2, chống nước IP68.', 'Samsung', 17990000, NULL, 3, 'ACTIVE', now(), now()),
       (7, 'LAP-MBA-M2', 'MacBook Air M2 13 inch 8GB/256GB', 'macbook-air-m2-13-8gb-256gb',
        'Chip M2, mỏng nhẹ 1.24kg, pin 18 giờ',
        'Màn hình Liquid Retina 13.6 inch, không quạt, sạc MagSafe.', 'Apple', 24990000, 27990000, 5, 'ACTIVE', now(), now()),
       (8, 'LAP-XPS13-I7', 'Dell XPS 13 i7/16GB/512GB', 'dell-xps-13-i7-16gb-512gb',
        'Thiết kế nhôm nguyên khối, màn hình InfinityEdge',
        'Intel Core i7 thế hệ 13, SSD 512GB, Windows 11.', 'Dell', 32990000, NULL, 5, 'ACTIVE', now(), now()),
       (9, 'LAP-TPE14-I5', 'Lenovo ThinkPad E14 i5/16GB/512GB', 'lenovo-thinkpad-e14-i5-16gb-512gb',
        'Bàn phím ThinkPad, độ bền chuẩn quân đội',
        'Intel Core i5, màn hình 14 inch WUXGA.', 'Lenovo', 18990000, 20490000, 5, 'ACTIVE', now(), now()),
       (10, 'LAP-ROG-G14', 'ASUS ROG Zephyrus G14 RTX 4060', 'asus-rog-zephyrus-g14-rtx-4060',
        'Ryzen 9, RTX 4060, màn hình OLED 120Hz',
        'Laptop gaming 14 inch nhỏ gọn, 16GB RAM, SSD 1TB.', 'ASUS', 45990000, 49990000, 6, 'ACTIVE', now(), now()),
       (11, 'LAP-LEG5-4060', 'Lenovo Legion 5 RTX 4060', 'lenovo-legion-5-rtx-4060',
        'Ryzen 7, RTX 4060, màn hình 165Hz',
        'Tản nhiệt Legion Coldfront, bàn phím RGB.', 'Lenovo', 34990000, NULL, 6, 'ACTIVE', now(), now()),
       (12, 'LAP-NITRO5-3050', 'Acer Nitro 5 RTX 3050', 'acer-nitro-5-rtx-3050',
        'Core i5, RTX 3050, màn hình 144Hz',
        'Lựa chọn gaming giá tốt cho sinh viên.', 'Acer', 19490000, 21990000, 6, 'ACTIVE', now(), now()),
       (13, 'AUD-APP2-USBC', 'AirPods Pro 2 USB-C', 'airpods-pro-2-usb-c',
        'Chống ồn chủ động, âm thanh không gian',
        'Hộp sạc USB-C, chống nước IP54.', 'Apple', 5990000, 6790000, 8, 'ACTIVE', now(), now()),
       (14, 'AUD-WH1000XM5', 'Sony WH-1000XM5', 'sony-wh-1000xm5',
        'Tai nghe chụp tai chống ồn hàng đầu',
        'Pin 30 giờ, sạc nhanh, kết nối đa điểm.', 'Sony', 7490000, 8990000, 8, 'ACTIVE', now(), now()),
       (15, 'AUD-BUDS2PRO', 'Samsung Galaxy Buds2 Pro', 'samsung-galaxy-buds2-pro',
        'Âm thanh Hi-Fi 24bit, chống ồn ANC',
        'Thiết kế nhỏ gọn, chống nước IPX7.', 'Samsung', 3290000, 4490000, 8, 'ACTIVE', now(), now()),
       (16, 'AUD-JBLFLIP6', 'Loa JBL Flip 6', 'loa-jbl-flip-6',
        'Loa bluetooth chống nước IP67',
        'Pin 12 giờ, kết nối PartyBoost.', 'JBL', 2590000, 2990000, 9, 'ACTIVE', now(), now()),
       (17, 'AUD-MARSHALL-EM2', 'Loa Marshall Emberton II', 'loa-marshall-emberton-ii',
        'Âm thanh 360 độ, pin hơn 30 giờ',
        'Thiết kế cổ điển, chống nước IP67.', 'Marshall', 4290000, NULL, 9, 'ACTIVE', now(), now()),
       (18, 'ACC-ANK-65W', 'Sạc Anker 65W GaN 3 cổng', 'sac-anker-65w-gan-3-cong',
        'Sạc nhanh laptop, điện thoại cùng lúc',
        '2 cổng USB-C, 1 cổng USB-A, công nghệ GaN nhỏ gọn.', 'Anker', 990000, 1290000, 11, 'ACTIVE', now(), now()),
       (19, 'ACC-UGR-C2C', 'Cáp UGREEN USB-C to USB-C 100W 2m', 'cap-ugreen-usb-c-100w-2m',
        'Hỗ trợ sạc 100W, vỏ bện nylon',
        'Truyền dữ liệu 480Mbps, độ bền 10.000 lần gập.', 'UGREEN', 249000, NULL, 11, 'ACTIVE', now(), now()),
       (20, 'ACC-MXM3S', 'Chuột Logitech MX Master 3S', 'chuot-logitech-mx-master-3s',
        'Chuột không dây cao cấp, click yên lặng',
        'Cảm biến 8000 DPI, cuộn MagSpeed, kết nối 3 thiết bị.', 'Logitech', 2290000, 2590000, 12, 'ACTIVE', now(), now()),
       (21, 'ACC-K380', 'Bàn phím Logitech K380', 'ban-phim-logitech-k380',
        'Bàn phím bluetooth nhỏ gọn đa thiết bị',
        'Pin 24 tháng, tương thích Windows, macOS, iPadOS.', 'Logitech', 690000, 790000, 12, 'ACTIVE', now(), now()),
       (22, 'ACC-KCK2', 'Bàn phím cơ Keychron K2 V2', 'ban-phim-co-keychron-k2-v2',
        'Bàn phím cơ 75% không dây, switch Gateron',
        'Đèn nền RGB, hỗ trợ macOS và Windows.', 'Keychron', 1890000, NULL, 12, 'ACTIVE', now(), now()),
       -- Not visible to customers: shows how DRAFT and ARCHIVED products behave
       (23, 'PHN-IP16-128', 'iPhone 16 128GB', 'iphone-16-128gb',
        'Sắp ra mắt', NULL, 'Apple', 22990000, NULL, 2, 'DRAFT', now(), now()),
       (24, 'AUD-APP1', 'AirPods Pro thế hệ 1', 'airpods-pro-the-he-1',
        'Ngừng kinh doanh', NULL, 'Apple', 3990000, NULL, 8, 'ARCHIVED', now(), now());

INSERT INTO product_images (product_id, url, alt_text, sort_order)
SELECT p.id,
       'https://picsum.photos/seed/' || lower(p.sku) || '-' || n || '/800/800',
       p.name,
       n - 1
FROM products p
         CROSS JOIN generate_series(1, 2) AS n;

-- Identity columns continue after the fixed ids above
SELECT setval(pg_get_serial_sequence('categories', 'id'), (SELECT max(id) FROM categories));
SELECT setval(pg_get_serial_sequence('products', 'id'), (SELECT max(id) FROM products));
