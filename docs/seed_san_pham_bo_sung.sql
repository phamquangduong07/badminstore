/* ==========================================================================================
   seed_san_pham_bo_sung.sql – Bổ sung 5 cây vợt mới cho danh mục (đủ 20 mẫu để demo)
   ------------------------------------------------------------------------------------------
   Cách chạy (chạy SAU BadStore.sql, BadStore_patch_kha.sql và docs\seed_san_pham.sql):
       sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d BadminStore -i docs\seed_san_pham_bo_sung.sql

   Đặc điểm:
   - Script DỮ LIỆU (không tạo/sửa bảng). Chạy lại nhiều lần vẫn ra đúng kết quả.
   - CHỈ xoá/nạp 5 mã sản phẩm ở bảng @codes bên dưới, KHÔNG đụng tới 15 mã của
     docs\seed_san_pham.sql nên không ảnh hưởng đơn hàng/đánh giá đang có.
   - Ảnh SVG tự vẽ sẵn trong static/images/rackets/ (mỗi mã có 2 ảnh: -1.svg, -2.svg).
   - Tuân thủ ràng buộc của BadStore.sql: CK_products_* , CK_variants_weight (2U..5U),
     CK_variants_original (original_price >= price), UQ_product_variant(product_id, color, weight_class, grip_size).
   ========================================================================================== */
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRAN;

DECLARE @codes TABLE (product_code VARCHAR(30) PRIMARY KEY);
INSERT INTO @codes (product_code) VALUES
    ('YX-AX99P'), ('YX-NF1000Z'), ('LN-AXF100'), ('VC-TKFALCON'), ('MZ-FT11P');

/* Xoá dữ liệu cũ của CHÍNH 5 mã này (nếu chạy lần 2): đánh giá → ảnh → biến thể → sản phẩm */
DELETE r FROM product_reviews  r JOIN products p ON p.id = r.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE i FROM product_images   i JOIN products p ON p.id = i.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE v FROM product_variants v JOIN products p ON p.id = v.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE p FROM products         p JOIN @codes c ON c.product_code = p.product_code;

/* ---- Khoá ngoại tra theo tên (không hard-code id) ---- */
DECLARE @yonex   INT = (SELECT id FROM brands WHERE name = N'Yonex'   AND is_deleted = 0);
DECLARE @lining  INT = (SELECT id FROM brands WHERE name = N'Li-Ning' AND is_deleted = 0);
DECLARE @victor  INT = (SELECT id FROM brands WHERE name = N'Victor'  AND is_deleted = 0);
DECLARE @mizuno  INT = (SELECT id FROM brands WHERE name = N'Mizuno'  AND is_deleted = 0);

DECLARE @tanCong   INT = (SELECT id FROM categories WHERE name = N'Vợt tấn công'  AND is_deleted = 0);
DECLARE @phongThu  INT = (SELECT id FROM categories WHERE name = N'Vợt phòng thủ' AND is_deleted = 0);
DECLARE @toanDien  INT = (SELECT id FROM categories WHERE name = N'Vợt toàn diện' AND is_deleted = 0);

IF @yonex IS NULL OR @lining IS NULL OR @victor IS NULL OR @mizuno IS NULL
   OR @tanCong IS NULL OR @phongThu IS NULL OR @toanDien IS NULL
BEGIN
    RAISERROR (N'Thiếu dữ liệu tra cứu: cần 4 hãng (Yonex, Li-Ning, Victor, Mizuno) và 3 loại vợt.', 16, 1);
    ROLLBACK TRAN;
    RETURN;
END

DECLARE @p INT;

/* =========================== 16. Yonex Astrox 99 Pro =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-AX99P', N'Vợt cầu lông Yonex Astrox 99 Pro', N'Vợt tấn công cho người đập cầu mạnh: khung Namd kết hợp Rotational Generator System giữ ổn định khi vung nhanh, thân cứng vừa dễ làm quen hơn Astrox 100 ZZ.',
        @yonex, @tanCong, 675, 310, 'HEAD_HEAVY', 'STIFF', 13.0,
        N'Namd + graphite', N'Namd', 'ACTIVE', 0,
        DATEADD(HOUR, -16, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-ax99p-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/yx-ax99p-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-AX99P-01', N'Trắng ngọc',  '4U', 'G5', 4790000, 4290000, 10, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX99P-02', N'Đỏ tía',      '4U', 'G4', 4790000, 4290000,  5, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX99P-03', N'Xanh tím',    '3U', 'G5', 4990000, 4490000,  0, 'INACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 17. Yonex Nanoflare 1000 Z =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-NF1000Z', N'Vợt cầu lông Yonex Nanoflare 1000 Z', N'Cây vợt nhẹ đầu nhất của Yonex dành cho lối đánh nhanh, phản tạt và đập cầu liên tục; khung Sonic Flare giảm rung, phù hợp đánh đôi tốc độ.',
        @yonex, @phongThu, 675, 290, 'HEAD_LIGHT', 'STIFF', 12.5,
        N'Graphite + M40X', N'Hyper Slim Shaft', 'ACTIVE', 0,
        DATEADD(HOUR, -17, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-nf1000z-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/yx-nf1000z-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-NF1000Z-01', N'Vàng đồng', '4U', 'G5', 5290000, 4790000, 9, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-NF1000Z-02', N'Trắng xanh', '4U', 'G4', 5290000, 4790000, 4, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 18. Li-Ning Axforce 100 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('LN-AXF100', N'Vợt cầu lông Li-Ning Axforce 100', N'Vợt tấn công đầu bảng của Li-Ning: khung 6 góc giảm xoắn, thân siêu cứng cho sức mạnh tối đa, dành cho người có lực cổ tay tốt.',
        @lining, @tanCong, 675, 312, 'HEAD_HEAVY', 'EXTRA_STIFF', 14.0,
        N'Carbon T1100G', N'Carbon T1100G + UHB', 'ACTIVE', 0,
        DATEADD(HOUR, -18, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ln-axf100-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/ln-axf100-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'LN-AXF100-01', N'Đen đỏ', '4U', 'G5', 5490000, 4990000, 8, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'LN-AXF100-02', N'Đen vàng', '3U', 'G4', 5590000, 5090000, 3, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 19. Victor Thruster K Falcon =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('VC-TKFALCON', N'Vợt cầu lông Victor Thruster K Falcon', N'Thuộc dòng Thruster nặng đầu của Victor, khung Turbo gọn khí động cho cú đập xoáy; chất vợt cân bằng giữa sức mạnh và tốc độ vung.',
        @victor, @tanCong, 675, 303, 'HEAD_HEAVY', 'STIFF', 13.0,
        N'Graphite + Hard Cored Technology', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -19, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/vc-tkfalcon-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/vc-tkfalcon-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'VC-TKFALCON-01', N'Đen xanh', '4U', 'G5', 3890000, 3490000, 11, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-TKFALCON-02', N'Đen bạc',  '4U', 'G4', 3890000, 3490000,  6, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-TKFALCON-03', N'Xanh dương', '5U', 'G5', 3690000, 3290000, 0, 'INACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 20. Mizuno Fortius 11 Power =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('MZ-FT11P', N'Vợt cầu lông Mizuno Fortius 11 Power', N'Vợt toàn diện hơi nặng đầu của Mizuno: dễ lấy lại thế trận ở cuối sân mà vẫn đủ nhanh khi chắn cầu, phù hợp người chơi phong trào lên trình.',
        @mizuno, @toanDien, 675, 298, 'HEAD_HEAVY', 'MEDIUM', 12.0,
        N'Graphite Japan', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -20, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/mz-ft11p-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/mz-ft11p-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'MZ-FT11P-01', N'Xanh lá',  '4U', 'G5', 2590000, 2290000, 13, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'MZ-FT11P-02', N'Đen',      '4U', 'G4', 2590000, 2290000,  7, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

COMMIT TRAN;

/* ---- Báo cáo kết quả nạp ---- */
SELECT N'Tổng sản phẩm đang bán' AS muc, COUNT(*) AS so_luong FROM products WHERE is_deleted = 0 AND status = 'ACTIVE'
UNION ALL SELECT N'Số hãng', COUNT(*) FROM brands WHERE is_deleted = 0
UNION ALL SELECT N'Biến thể đang bán', COUNT(*) FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.status = 'ACTIVE' AND p.is_deleted = 0
UNION ALL SELECT N'Ảnh sản phẩm', COUNT(*) FROM product_images;
