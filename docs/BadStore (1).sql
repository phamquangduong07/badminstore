/* =====================================================================
   BadStore v3 - Website bán vợt cầu lông (chỉ bán vợt)
   Mục tiêu: bám tiêu chí ĐATN "Bán hàng" mức 5-7 điểm, nhóm 3 người
   Stack: Spring Boot + SQL Server + Vue
   Quy ước: tiền VND dùng DECIMAL(18,0); updated_at do tầng ứng dụng cập nhật

   Luồng đơn hàng (đúng tiêu chí):
     PENDING (đặt hàng) -> CONFIRMED (xác nhận, TRỪ KHO) -> SHIPPING (vận chuyển)
       -> DELIVERED (thành công)
     PENDING/CONFIRMED/SHIPPING -> CANCELLED (hủy)
     DELIVERED -> RETURNED (hoàn trả 100% đơn, cộng lại kho)
   ===================================================================== */
IF DB_ID('BadminStore') IS NULL
    CREATE DATABASE BadminStore COLLATE Vietnamese_100_CI_AS;
GO
USE BadminStore;
GO

/* ===================== 1. USERS ===================== */
CREATE TABLE users (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    email         VARCHAR(100)  NOT NULL UNIQUE,
    password_hash VARCHAR(255)  NOT NULL,             -- BCrypt
    full_name     NVARCHAR(100) NOT NULL,
    phone         VARCHAR(20)   NULL,
    avatar        VARCHAR(500)  NULL,
    role          VARCHAR(20)   NOT NULL CONSTRAINT DF_users_role DEFAULT 'CUSTOMER',
    status        VARCHAR(20)   NOT NULL CONSTRAINT DF_users_status DEFAULT 'ACTIVE',
    created_at    DATETIME2     NOT NULL CONSTRAINT DF_users_created DEFAULT SYSDATETIME(),
    updated_at    DATETIME2     NOT NULL CONSTRAINT DF_users_updated DEFAULT SYSDATETIME(),
    CONSTRAINT CK_users_role   CHECK (role IN ('ADMIN','CUSTOMER')),
    CONSTRAINT CK_users_status CHECK (status IN ('ACTIVE','INACTIVE','LOCKED'))
);
GO

/* Địa chỉ giao hàng - id/code lưu NGUYÊN giá trị GHN trả về (dùng tính phí ship) */
CREATE TABLE user_addresses (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    user_id        INT           NOT NULL,
    receiver_name  NVARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(20)   NOT NULL,
    province_id    INT           NULL,               -- GHN ProvinceID
    province_name  NVARCHAR(100) NULL,
    district_id    INT           NULL,               -- GHN DistrictID
    district_name  NVARCHAR(100) NULL,
    ward_code      VARCHAR(20)   NULL,               -- GHN WardCode
    ward_name      NVARCHAR(100) NULL,
    address_detail NVARCHAR(255) NOT NULL,           -- số nhà, tên đường
    is_default     BIT           NOT NULL CONSTRAINT DF_addr_default DEFAULT 0,
    is_deleted     BIT           NOT NULL CONSTRAINT DF_addr_deleted DEFAULT 0,
    created_at     DATETIME2     NOT NULL CONSTRAINT DF_addr_created DEFAULT SYSDATETIME(),
    updated_at     DATETIME2     NOT NULL CONSTRAINT DF_addr_updated DEFAULT SYSDATETIME(),
    CONSTRAINT FK_user_addresses_users FOREIGN KEY (user_id) REFERENCES users(id)
);
GO
CREATE INDEX IX_user_addresses_user ON user_addresses(user_id);
-- Mỗi user tối đa 1 địa chỉ mặc định (đổi mặc định: bỏ cờ cũ TRƯỚC, đặt cờ mới SAU, trong 1 transaction)
CREATE UNIQUE INDEX UX_user_addresses_default ON user_addresses(user_id)
    WHERE is_default = 1 AND is_deleted = 0;
GO

/* ===================== 2. CATALOG ===================== */

CREATE TABLE brands (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    description NVARCHAR(500) NULL,
    logo        VARCHAR(500)  NULL,
    is_deleted  BIT           NOT NULL CONSTRAINT DF_brands_deleted DEFAULT 0,
    created_at  DATETIME2     NOT NULL CONSTRAINT DF_brands_created DEFAULT SYSDATETIME(),
    updated_at  DATETIME2     NOT NULL CONSTRAINT DF_brands_updated DEFAULT SYSDATETIME()
);
GO
CREATE UNIQUE INDEX UX_brands_name ON brands(name) WHERE is_deleted = 0;

GO

/* Category = LỐI CHƠI (Tấn công / Phòng thủ / Toàn diện).
   Thông số kỹ thuật thật nằm ở bảng products. */
CREATE TABLE categories (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    name        NVARCHAR(100) NOT NULL,
    description NVARCHAR(500) NULL,
    is_deleted  BIT           NOT NULL CONSTRAINT DF_cat_deleted DEFAULT 0,
    created_at  DATETIME2     NOT NULL CONSTRAINT DF_cat_created DEFAULT SYSDATETIME(),
    updated_at  DATETIME2     NOT NULL CONSTRAINT DF_cat_updated DEFAULT SYSDATETIME()
);
GO
CREATE UNIQUE INDEX UX_categories_name ON categories(name) WHERE is_deleted = 0;
GO

/* Thông số chung của một mẫu vợt. Các cột dạng số/enum này cũng là dữ liệu để
   làm chức năng "so sánh sản phẩm" (mức Khá) mà không cần thêm bảng. */
CREATE TABLE products (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    product_code      VARCHAR(30)   NOT NULL,
    name              NVARCHAR(200) NOT NULL,
    description       NVARCHAR(MAX) NULL,
    brand_id          INT           NOT NULL,
    category_id       INT           NOT NULL,

    length_mm         INT           NULL,            -- Chiều dài vợt
    balance_point_mm  INT           NULL,            -- Điểm cân bằng
    balance_type      VARCHAR(20)   NULL,            -- HEAD_HEAVY / EVEN / HEAD_LIGHT
    shaft_flexibility VARCHAR(20)   NULL,            -- SOFT / MEDIUM / STIFF / EXTRA_STIFF
    max_tension_kg    DECIMAL(4,1)  NULL,            -- Mức căng dây tối đa
    frame_material    NVARCHAR(150) NULL,
    shaft_material    NVARCHAR(150) NULL,

    status            VARCHAR(20)   NOT NULL CONSTRAINT DF_products_status DEFAULT 'ACTIVE',
    is_deleted        BIT           NOT NULL CONSTRAINT DF_products_deleted DEFAULT 0,
    created_at        DATETIME2     NOT NULL CONSTRAINT DF_products_created DEFAULT SYSDATETIME(),
    updated_at        DATETIME2     NOT NULL CONSTRAINT DF_products_updated DEFAULT SYSDATETIME(),

    CONSTRAINT FK_products_brand    FOREIGN KEY (brand_id)    REFERENCES brands(id),
    CONSTRAINT FK_products_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT CK_products_length   CHECK (length_mm IS NULL OR length_mm > 0),
    CONSTRAINT CK_products_balance  CHECK (balance_point_mm IS NULL OR balance_point_mm > 0),
    CONSTRAINT CK_products_tension  CHECK (max_tension_kg IS NULL OR max_tension_kg > 0),
    CONSTRAINT CK_products_balance_type CHECK (balance_type IS NULL
        OR balance_type IN ('HEAD_HEAVY','EVEN','HEAD_LIGHT')),
    CONSTRAINT CK_products_flex CHECK (shaft_flexibility IS NULL
        OR shaft_flexibility IN ('SOFT','MEDIUM','STIFF','EXTRA_STIFF')),
    CONSTRAINT CK_products_status CHECK (status IN ('ACTIVE','DRAFT','HIDDEN'))
);
GO
CREATE UNIQUE INDEX UX_products_code ON products(product_code) WHERE is_deleted = 0;
CREATE INDEX IX_products_brand    ON products(brand_id);
CREATE INDEX IX_products_category ON products(category_id);
GO

/* Biến thể = màu x trọng lượng (2U-5U) x cỡ cán (G3-G6).
   Mỗi biến thể có thể có ảnh riêng (image_url) -> dùng cho giỏ hàng + snapshot đơn hàng. */
CREATE TABLE product_variants (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    product_id     INT           NOT NULL,
    sku            VARCHAR(50)   NOT NULL UNIQUE,
    color          NVARCHAR(50)  NOT NULL,
    weight_class   VARCHAR(10)   NOT NULL,           -- 2U, 3U, 4U, 5U (KHÔNG phải gram)
    grip_size      VARCHAR(10)   NOT NULL,           -- G3, G4, G5, G6
    image_url      VARCHAR(500)  NULL,
    original_price DECIMAL(18,0) NULL,               -- giá gạch ngang (nếu giảm giá)
    price          DECIMAL(18,0) NOT NULL,           -- giá bán hiện tại
    stock_quantity INT           NOT NULL CONSTRAINT DF_variants_stock DEFAULT 0,
    status         VARCHAR(20)   NOT NULL CONSTRAINT DF_variants_status DEFAULT 'ACTIVE',
    created_at     DATETIME2     NOT NULL CONSTRAINT DF_variants_created DEFAULT SYSDATETIME(),
    updated_at     DATETIME2     NOT NULL CONSTRAINT DF_variants_updated DEFAULT SYSDATETIME(),

    CONSTRAINT FK_product_variants_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT UQ_product_variant    UNIQUE (product_id, color, weight_class, grip_size),
    CONSTRAINT CK_variants_weight    CHECK (weight_class IN ('2U','3U','4U','5U')),
    CONSTRAINT CK_variants_grip      CHECK (grip_size IN ('G3','G4','G5','G6')),
    CONSTRAINT CK_variants_price     CHECK (price >= 0),
    CONSTRAINT CK_variants_original  CHECK (original_price IS NULL OR original_price >= price),
    CONSTRAINT CK_variants_stock     CHECK (stock_quantity >= 0),
    CONSTRAINT CK_variants_status    CHECK (status IN ('ACTIVE','INACTIVE'))
);
GO
-- Trang danh sách: lấy "giá từ" = MIN(price) theo sản phẩm, lọc theo khoảng giá
CREATE INDEX IX_variants_product_price ON product_variants(product_id, price) WHERE status = 'ACTIVE';
GO

/* Bộ ảnh (gallery) của sản phẩm */
CREATE TABLE product_images (
    id         INT IDENTITY(1,1) PRIMARY KEY,
    product_id INT          NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    is_primary BIT          NOT NULL CONSTRAINT DF_images_primary DEFAULT 0,
    sort_order INT          NOT NULL CONSTRAINT DF_images_sort DEFAULT 0,
    created_at DATETIME2    NOT NULL CONSTRAINT DF_images_created DEFAULT SYSDATETIME(),
    CONSTRAINT FK_product_images_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT CK_product_images_sort CHECK (sort_order >= 0)
);
GO
CREATE INDEX IX_product_images_product ON product_images(product_id);
CREATE UNIQUE INDEX UX_product_images_primary ON product_images(product_id) WHERE is_primary = 1;
GO

/* ===================== 3. CART (gộp carts vào cart_items) ===================== */
CREATE TABLE cart_items (
    id                 INT IDENTITY(1,1) PRIMARY KEY,
    user_id            INT       NOT NULL,
    product_variant_id INT       NOT NULL,
    quantity           INT       NOT NULL CONSTRAINT DF_cart_items_qty DEFAULT 1,
    created_at         DATETIME2 NOT NULL CONSTRAINT DF_cart_items_created DEFAULT SYSDATETIME(),
    updated_at         DATETIME2 NOT NULL CONSTRAINT DF_cart_items_updated DEFAULT SYSDATETIME(),
    CONSTRAINT FK_cart_items_users    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT FK_cart_items_variants FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT UQ_cart_items_user_variant UNIQUE (user_id, product_variant_id),
    CONSTRAINT CK_cart_items_quantity CHECK (quantity > 0)
);
GO
CREATE INDEX IX_cart_items_variant ON cart_items(product_variant_id);
GO

/* ===================== 4. VOUCHER (áp dụng cho tổng hóa đơn) ===================== */
CREATE TABLE vouchers (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    code                VARCHAR(50)   NOT NULL UNIQUE,
    name                NVARCHAR(100) NOT NULL,
    description         NVARCHAR(500) NULL,
    discount_type       VARCHAR(20)   NOT NULL,
    discount_value      DECIMAL(18,2) NOT NULL,      -- PERCENT: 0-100, FIXED: số tiền VND
    min_order_value     DECIMAL(18,0) NOT NULL CONSTRAINT DF_vouchers_min DEFAULT 0,
    max_discount_amount DECIMAL(18,0) NULL,
    quantity            INT           NOT NULL,      -- tổng lượt phát hành
    used_quantity       INT           NOT NULL CONSTRAINT DF_vouchers_used DEFAULT 0,
    start_at            DATETIME2     NOT NULL,
    end_at              DATETIME2     NOT NULL,
    status              VARCHAR(20)   NOT NULL CONSTRAINT DF_vouchers_status DEFAULT 'ACTIVE',
    created_at          DATETIME2     NOT NULL CONSTRAINT DF_vouchers_created DEFAULT SYSDATETIME(),
    updated_at          DATETIME2     NOT NULL CONSTRAINT DF_vouchers_updated DEFAULT SYSDATETIME(),

    CONSTRAINT CK_vouchers_type     CHECK (discount_type IN ('PERCENT','FIXED')),
    CONSTRAINT CK_vouchers_value    CHECK (discount_value >= 0),
    CONSTRAINT CK_vouchers_percent  CHECK (discount_type <> 'PERCENT' OR discount_value <= 100),
    CONSTRAINT CK_vouchers_min      CHECK (min_order_value >= 0),
    CONSTRAINT CK_vouchers_max      CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0),
    CONSTRAINT CK_vouchers_qty      CHECK (quantity >= 0),
    CONSTRAINT CK_vouchers_used     CHECK (used_quantity >= 0 AND used_quantity <= quantity),
    CONSTRAINT CK_vouchers_date     CHECK (end_at > start_at),
    CONSTRAINT CK_vouchers_status   CHECK (status IN ('ACTIVE','INACTIVE'))
);
GO

/* ===================== 5. ORDER / PAYMENT ===================== */
CREATE TABLE orders (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    order_code           VARCHAR(30)   NOT NULL UNIQUE,

    -- ONLINE: khách tự đặt trên web (bắt buộc có user_id)
    -- MANUAL: admin tạo đơn thủ công (khách vãng lai/đặt qua điện thoại; user_id có thể NULL)
    order_source         VARCHAR(20)   NOT NULL CONSTRAINT DF_orders_source DEFAULT 'ONLINE',
    user_id              INT           NULL,
    created_by           INT           NULL,         -- admin tạo đơn thủ công
    address_id           INT           NULL,

    -- Snapshot thông tin khách/giao hàng tại thời điểm đặt
    receiver_name        NVARCHAR(100) NOT NULL,
    receiver_phone       VARCHAR(20)   NOT NULL,
    shipping_address     NVARCHAR(500) NULL,         -- NULL được phép với đơn MANUAL mua tại quầy
    shipping_province_id INT           NULL,         -- GHN
    shipping_district_id INT           NULL,         -- GHN
    shipping_ward_code   VARCHAR(20)   NULL,         -- GHN

    subtotal             DECIMAL(18,0) NOT NULL,
    shipping_fee         DECIMAL(18,0) NOT NULL CONSTRAINT DF_orders_ship DEFAULT 0,
    discount_amount      DECIMAL(18,0) NOT NULL CONSTRAINT DF_orders_discount DEFAULT 0,
    total_amount         DECIMAL(18,0) NOT NULL,
    voucher_id           INT           NULL,

    order_status         VARCHAR(30)   NOT NULL CONSTRAINT DF_orders_status DEFAULT 'PENDING',
    -- 1 = kho đã bị trừ cho đơn này (chỉ khi CONFIRMED/SHIPPING/DELIVERED). Xem CK_orders_stock
    stock_deducted       BIT           NOT NULL CONSTRAINT DF_orders_stock DEFAULT 0,
    note                 NVARCHAR(500) NULL,
    cancel_reason        NVARCHAR(500) NULL,         -- lý do hủy / hoàn trả
    ghn_order_code       VARCHAR(30)   NULL,         -- mã vận đơn GHN (nếu tạo đơn GHN)

    created_at           DATETIME2     NOT NULL CONSTRAINT DF_orders_created DEFAULT SYSDATETIME(),
    updated_at           DATETIME2     NOT NULL CONSTRAINT DF_orders_updated DEFAULT SYSDATETIME(),

    CONSTRAINT FK_orders_users      FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT FK_orders_created_by FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT FK_orders_addresses  FOREIGN KEY (address_id) REFERENCES user_addresses(id),
    CONSTRAINT FK_orders_vouchers   FOREIGN KEY (voucher_id) REFERENCES vouchers(id),
    CONSTRAINT CK_orders_source    CHECK (order_source IN ('ONLINE','MANUAL')),
    -- Đơn ONLINE phải có tài khoản + địa chỉ giao; đơn MANUAL phải biết admin nào tạo
    CONSTRAINT CK_orders_online    CHECK (order_source <> 'ONLINE'
        OR (user_id IS NOT NULL AND shipping_address IS NOT NULL)),
    CONSTRAINT CK_orders_manual    CHECK (order_source <> 'MANUAL' OR created_by IS NOT NULL),
    CONSTRAINT CK_orders_subtotal  CHECK (subtotal >= 0),
    CONSTRAINT CK_orders_shipping  CHECK (shipping_fee >= 0),
    CONSTRAINT CK_orders_discount  CHECK (discount_amount >= 0 AND discount_amount <= subtotal),
    CONSTRAINT CK_orders_total     CHECK (total_amount = subtotal + shipping_fee - discount_amount),
    CONSTRAINT CK_orders_status    CHECK (order_status IN
        ('PENDING','CONFIRMED','SHIPPING','DELIVERED','CANCELLED','RETURNED')),
    -- Chốt tính đúng đắn số lượng ở tầng DB: kho bị trừ <=> đơn đang ở CONFIRMED/SHIPPING/DELIVERED
    CONSTRAINT CK_orders_stock     CHECK (
        (order_status IN ('CONFIRMED','SHIPPING','DELIVERED') AND stock_deducted = 1)
     OR (order_status IN ('PENDING','CANCELLED','RETURNED')   AND stock_deducted = 0))
);
GO
CREATE INDEX IX_orders_user_created ON orders(user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IX_orders_status       ON orders(order_status, created_at DESC);
CREATE INDEX IX_orders_voucher      ON orders(voucher_id, user_id) WHERE voucher_id IS NOT NULL;
CREATE UNIQUE INDEX UX_orders_ghn_code ON orders(ghn_order_code) WHERE ghn_order_code IS NOT NULL;
GO

CREATE TABLE order_items (
    id                 INT IDENTITY(1,1) PRIMARY KEY,
    order_id           INT           NOT NULL,
    product_variant_id INT           NOT NULL,

    -- Snapshot sản phẩm tại thời điểm mua
    product_name       NVARCHAR(200) NOT NULL,
    sku                VARCHAR(50)   NOT NULL,
    color              NVARCHAR(50)  NOT NULL,
    weight_class       VARCHAR(10)   NOT NULL,
    grip_size          VARCHAR(10)   NOT NULL,
    image_url          VARCHAR(500)  NULL,

    unit_price         DECIMAL(18,0) NOT NULL,
    quantity           INT           NOT NULL,
    subtotal           DECIMAL(18,0) NOT NULL,

    CONSTRAINT FK_order_items_orders   FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT FK_order_items_variants FOREIGN KEY (product_variant_id) REFERENCES product_variants(id),
    CONSTRAINT UQ_order_items_order_variant UNIQUE (order_id, product_variant_id),
    CONSTRAINT CK_order_items_price    CHECK (unit_price >= 0),
    CONSTRAINT CK_order_items_qty      CHECK (quantity > 0),
    CONSTRAINT CK_order_items_subtotal CHECK (subtotal = unit_price * quantity)
);
GO
CREATE INDEX IX_order_items_variant ON order_items(product_variant_id);
GO

/* Thanh toán: COD, chuyển khoản (admin tự xác nhận đã nhận tiền), tiền mặt (đơn thủ công tại quầy) */
CREATE TABLE payments (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    order_id         INT           NOT NULL UNIQUE,
    payment_method   VARCHAR(30)   NOT NULL,         -- COD | BANK_TRANSFER | CASH
    payment_status   VARCHAR(30)   NOT NULL CONSTRAINT DF_payments_status DEFAULT 'PENDING',
    amount           DECIMAL(18,0) NOT NULL,         -- = orders.total_amount (kiểm tra ở tầng ứng dụng)
    transaction_code VARCHAR(100)  NULL,             -- mã giao dịch ngân hàng (nếu có)
    confirmed_by     INT           NULL,             -- admin xác nhận đã nhận tiền
    paid_at          DATETIME2     NULL,
    created_at       DATETIME2     NOT NULL CONSTRAINT DF_payments_created DEFAULT SYSDATETIME(),
    updated_at       DATETIME2     NOT NULL CONSTRAINT DF_payments_updated DEFAULT SYSDATETIME(),

    CONSTRAINT FK_payments_orders       FOREIGN KEY (order_id)     REFERENCES orders(id),
    CONSTRAINT FK_payments_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id),
    CONSTRAINT CK_payments_method CHECK (payment_method IN ('COD','BANK_TRANSFER','CASH')),
    CONSTRAINT CK_payments_status CHECK (payment_status IN ('PENDING','PAID','FAILED','REFUNDED')),
    CONSTRAINT CK_payments_amount CHECK (amount >= 0),
    CONSTRAINT CK_payments_paid   CHECK (payment_status <> 'PAID' OR paid_at IS NOT NULL)
);
GO
CREATE UNIQUE INDEX UX_payments_transaction_code ON payments(transaction_code)
    WHERE transaction_code IS NOT NULL;
GO

/* Lịch sử thay đổi trạng thái - timeline đơn hàng + bằng chứng "ai đổi, khi nào" */
CREATE TABLE order_status_histories (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    order_id    INT           NOT NULL,
    from_status VARCHAR(30)   NULL,
    to_status   VARCHAR(30)   NOT NULL,
    changed_by  INT           NULL,                  -- NULL = hệ thống
    note        NVARCHAR(500) NULL,
    created_at  DATETIME2     NOT NULL CONSTRAINT DF_osh_created DEFAULT SYSDATETIME(),
    CONSTRAINT FK_osh_orders FOREIGN KEY (order_id)   REFERENCES orders(id),
    CONSTRAINT FK_osh_users  FOREIGN KEY (changed_by) REFERENCES users(id)
);
GO
CREATE INDEX IX_osh_order ON order_status_histories(order_id, created_at);
GO
CREATE TABLE product_reviews (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    product_id    INT           NOT NULL,
    user_id       INT           NOT NULL,
    order_item_id INT           NOT NULL,
    rating        TINYINT       NOT NULL,
    comment       NVARCHAR(1000) NULL,
    status        VARCHAR(20)   NOT NULL CONSTRAINT DF_reviews_status DEFAULT 'VISIBLE',
    created_at    DATETIME2     NOT NULL CONSTRAINT DF_reviews_created DEFAULT SYSDATETIME(),
    updated_at    DATETIME2     NOT NULL CONSTRAINT DF_reviews_updated DEFAULT SYSDATETIME(),
    CONSTRAINT FK_reviews_products    FOREIGN KEY (product_id)    REFERENCES products(id),
    CONSTRAINT FK_reviews_users       FOREIGN KEY (user_id)       REFERENCES users(id),
    CONSTRAINT FK_reviews_order_items FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT UQ_reviews_order_item  UNIQUE (order_item_id),
    CONSTRAINT CK_reviews_rating      CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT CK_reviews_status      CHECK (status IN ('VISIBLE','HIDDEN'))
);
GO
CREATE INDEX IX_reviews_product ON product_reviews(product_id, status, created_at DESC);
GO
/* ===================== 6. SEED DATA TỐI THIỂU ===================== */
INSERT INTO brands(name) VALUES
 (N'Yonex'), (N'Li-Ning'), (N'Victor'),
 (N'Mizuno'), (N'Kumpoo'), (N'Apacs');

INSERT INTO categories(name, description) VALUES
 (N'Vợt tấn công',    N'Dành cho lối chơi tấn công, smash mạnh'),
 (N'Vợt phòng thủ',  N'Dành cho lối chơi phòng thủ, phản tạt linh hoạt'),
 (N'Vợt toàn diện',  N'Dành cho lối chơi cân bằng công thủ');
-- Tài khoản ADMIN: tạo bằng CommandLineRunner (BCrypt hash do Spring sinh), không hardcode hash vào SQL.
GO

/* ===================== GHI CHÚ KHI CODE ===================== */
/*
 A. THAY ĐỔI TRẠNG THÁI ĐƠN (mỗi lần = 1 transaction; luôn ghi order_status_histories)
    Luồng hợp lệ - kiểm tra ở Service, DB chặn trạng thái/stock sai bằng CK_orders_stock:
      PENDING   -> CONFIRMED | CANCELLED
      CONFIRMED -> SHIPPING  | CANCELLED
      SHIPPING  -> DELIVERED | CANCELLED
      DELIVERED -> RETURNED   (hoàn trả 100% đơn)

 B. XÁC NHẬN ĐƠN (PENDING -> CONFIRMED) = TRỪ KHO. Duyệt từng order_item, sắp xếp theo
    product_variant_id tăng dần (tránh deadlock):
      UPDATE product_variants SET stock_quantity = stock_quantity - @qty
      WHERE id = @variantId AND stock_quantity >= @qty;      -- rows affected = 0 => rollback,
                                                             -- báo "Không đủ tồn kho cho SKU ..."
    Sau đó, trong CÙNG câu UPDATE: order_status = 'CONFIRMED', stock_deducted = 1.

 C. HỦY / HOÀN TRẢ:
    - Hủy khi PENDING: không đụng kho (chưa trừ). stock_deducted vẫn = 0.
    - Hủy khi CONFIRMED/SHIPPING, hoặc RETURNED từ DELIVERED: CỘNG lại kho
        UPDATE product_variants SET stock_quantity = stock_quantity + @qty WHERE id = @variantId;
      rồi UPDATE orders SET order_status = ..., stock_deducted = 0 (cùng 1 câu UPDATE).
    - Mọi trường hợp hủy: trả lượt voucher (vouchers.used_quantity - 1). Nếu RETURNED: payment REFUNDED nếu đã PAID.

 D. ĐẶT HÀNG (ONLINE) - chưa trừ kho nhưng PHẢI validate số lượng:
    kiểm tra stock_quantity >= quantity, variant.status = 'ACTIVE', sản phẩm status = 'ACTIVE'.
    Tạo orders + order_items (snapshot) + payments + dùng voucher + xóa cart_items của user
    trong 1 transaction.

 E. TẠO ĐƠN THỦ CÔNG (admin): order_source = 'MANUAL', created_by = id admin,
    user_id có thể NULL (khách vãng lai), shipping_address có thể NULL (mua tại quầy),
    payment_method = 'CASH' nếu thu tiền ngay. Có thể tạo PENDING rồi confirm luôn trong cùng transaction.

 F. VOUCHER (áp cho tổng hóa đơn, tính trên subtotal):
      UPDATE vouchers SET used_quantity = used_quantity + 1
      WHERE id = @id AND used_quantity < quantity AND status = 'ACTIVE'
        AND SYSDATETIME() BETWEEN start_at AND end_at;      -- rows affected = 1
    Kiểm tra min_order_value, giới hạn max_discount_amount ở Service.
    Muốn "mỗi khách dùng 1 lần":
      SELECT COUNT(*) FROM orders
      WHERE user_id = @u AND voucher_id = @v AND order_status NOT IN ('CANCELLED');

 G. THANH TOÁN: COD -> payment_status = 'PAID' (+ paid_at) khi đơn DELIVERED.
    Chuyển khoản -> khách chuyển với nội dung = order_code, admin kiểm tra rồi bấm xác nhận
    (confirmed_by = admin, paid_at = now). Nếu sau này làm webhook ngân hàng thì thêm lại cột
    expires_at, gateway_payload (xem BadStore_v2.sql).

 H. GHN: phí ship lấy từ fee API lúc checkout -> orders.shipping_fee.
    Khối lượng gói hàng (gram) để hằng số trong cấu hình, KHÔNG dùng cột weight_class '3U/4U'.
    Token GHN để trong application.yml, không commit lên Git.

 I. UI: mọi thao tác ghi CSDL (thêm/sửa/xóa sản phẩm, đổi trạng thái đơn, hủy đơn...) cần có
    hộp thoại xác nhận trước + thông báo thành công/thất bại sau (đúng tiêu chí đề tài).

 J. TÌM KIẾM: LIKE N'%từ khóa%' trên products.name (+ lọc brand_id, category_id, khoảng giá theo
    MIN(price) từ product_variants). Lọc hiển thị: is_deleted = 0 AND status = 'ACTIVE'.

 K. Hibernate: ddl-auto=none (hoặc validate); @Nationalized cho cột NVARCHAR;
    @Enumerated(EnumType.STRING) cho các cột status/method/role/balance_type/shaft_flexibility.
*/
