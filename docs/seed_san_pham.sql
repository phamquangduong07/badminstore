/* ==========================================================================================
   seed_san_pham.sql – Nạp dữ liệu mẫu cho danh mục sản phẩm (15 cây vợt cầu lông)
   ------------------------------------------------------------------------------------------
   Cách chạy (chạy SAU BadStore.sql và BadStore_patch_kha.sql):
       sqlcmd -S localhost,1433 -U test -P 123456 -C -I -f 65001 -d BadminStore -i docs\seed_san_pham.sql

   Lưu ý: file này chỉ có 15 cây vợt. Muốn đủ 20 mẫu như lúc demo thì chạy thêm
       docs\seed_san_pham_bo_sung.sql   (5 cây nữa, độc lập với file này).

   Đặc điểm:
   - Đây là script DỮ LIỆU, không phải script schema: không tạo/sửa/xoá bảng, chỉ thêm dòng cho
     15 mã sản phẩm ở bảng @codes bên dưới (chạy lại nhiều lần vẫn ra đúng kết quả đó).
   - Ảnh là file SVG tự vẽ trong static/images/rackets/ nên không phụ thuộc internet, không vi phạm bản quyền.
   - Tuân thủ mọi ràng buộc của BadStore.sql:
       CK_products_status (ACTIVE|DRAFT|HIDDEN), CK_products_balance_type, CK_products_flex,
       CK_products_length/balance/tension (> 0), CK_products_grip (G3..G6), CK_variants_weight (2U..5U),
       CK_variants_original (original_price >= price), CK_variants_price/stock (>= 0),
       CK_variants_status (ACTIVE|INACTIVE),
       UQ_product_variant(product_id, color, weight_class, grip_size), UX_products_code(product_code),
       UX_product_images_primary (mỗi sản phẩm tối đa 1 ảnh is_primary = 1).
   - created_at được đặt lệch nhau 1 giờ để sắp xếp "Mới nhất" luôn xác định.
   ========================================================================================== */
SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRAN;

DECLARE @codes TABLE (product_code VARCHAR(30) PRIMARY KEY);
INSERT INTO @codes (product_code) VALUES
    ('YX-AX100ZZ'), ('YX-AX88DP'), ('YX-NF800'), ('YX-ARC11P'),
    ('LN-HLBT9000'), ('LN-AXF90T'), ('LN-WS78'),
    ('VC-TKRYUGA2'), ('VC-ARS90K2'), ('VC-JS12SE'),
    ('MZ-AS7'), ('MZ-ALT01S'),
    ('KP-PS9900'), ('AP-ZZIGGLER2'), ('AP-FW55');

/* Xoá dữ liệu seed cũ (nếu chạy lần 2). Thứ tự: đánh giá → ảnh → biến thể → sản phẩm,
   vì các bảng con đều là FK NO ACTION tới products. */
DELETE r FROM product_reviews  r JOIN products p ON p.id = r.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE i FROM product_images   i JOIN products p ON p.id = i.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE v FROM product_variants v JOIN products p ON p.id = v.product_id JOIN @codes c ON c.product_code = p.product_code;
DELETE p FROM products         p JOIN @codes c ON c.product_code = p.product_code;

/* ---- Khoá ngoại tra theo tên (không hard-code id) ---- */
DECLARE @yonex   INT = (SELECT id FROM brands WHERE name = N'Yonex'   AND is_deleted = 0);
DECLARE @lining  INT = (SELECT id FROM brands WHERE name = N'Li-Ning' AND is_deleted = 0);
DECLARE @victor  INT = (SELECT id FROM brands WHERE name = N'Victor'  AND is_deleted = 0);
DECLARE @mizuno  INT = (SELECT id FROM brands WHERE name = N'Mizuno'  AND is_deleted = 0);
DECLARE @kumpoo  INT = (SELECT id FROM brands WHERE name = N'Kumpoo'  AND is_deleted = 0);
DECLARE @apacs   INT = (SELECT id FROM brands WHERE name = N'Apacs'   AND is_deleted = 0);

DECLARE @tanCong   INT = (SELECT id FROM categories WHERE name = N'Vợt tấn công'  AND is_deleted = 0);
DECLARE @phongThu  INT = (SELECT id FROM categories WHERE name = N'Vợt phòng thủ' AND is_deleted = 0);
DECLARE @toanDien  INT = (SELECT id FROM categories WHERE name = N'Vợt toàn diện' AND is_deleted = 0);

IF @yonex IS NULL OR @lining IS NULL OR @victor IS NULL OR @mizuno IS NULL OR @kumpoo IS NULL OR @apacs IS NULL
   OR @tanCong IS NULL OR @phongThu IS NULL OR @toanDien IS NULL
BEGIN
    RAISERROR (N'Thiếu dữ liệu tra cứu: cần có 6 hãng (Yonex, Li-Ning, Victor, Mizuno, Kumpoo, Apacs) và 3 loại vợt (tấn công, phòng thủ, toàn diện).', 16, 1);
    ROLLBACK TRAN;
    RETURN;
END

DECLARE @p INT;

/* =========================== 1. Yonex Astrox 100 ZZ =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-AX100ZZ', N'Vợt cầu lông Yonex Astrox 100 ZZ', N'Dòng vợt tấn công cao cấp nhất của Yonex với khung Namd siêu cứng và thân Hyper Slim cho ra đường cầu cắm sân. Phù hợp người chơi tấn công, đã quen vợt nặng đầu.',
        @yonex, @tanCong, 675, 305, 'HEAD_HEAVY', 'EXTRA_STIFF', 13.5,
        N'Namd (graphite + ống nano carbon)', N'Namd + Super Slim Shaft', 'ACTIVE', 0,
        DATEADD(HOUR, -1, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-ax100zz-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/yx-ax100zz-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-AX100ZZ-01', N'Xanh đen',  '4U', 'G5', 5190000, 4690000, 12, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX100ZZ-02', N'Đỏ đô',    '3U', 'G5', 5190000, 4690000,  6, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX100ZZ-03', N'Trắng bạc', '4U', 'G4', 4990000, 4490000,  0, 'INACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 2. Yonex Astrox 88D Pro =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-AX88DP', N'Vợt cầu lông Yonex Astrox 88D Pro', N'Phiên bản Pro của Astrox 88D: nặng đầu vừa phải, thân cứng, đập cầu uy lực nhưng vẫn kiểm soát tốt ở lưới. Lựa chọn quen thuộc của các tay vợt đôi tấn công.',
        @yonex, @tanCong, 675, 300, 'HEAD_HEAVY', 'STIFF', 13.0,
        N'Namd (graphite + ống nano carbon)', N'Namd', 'ACTIVE', 0,
        DATEADD(HOUR, -2, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-ax88dp-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/yx-ax88dp-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-AX88DP-01', N'Xanh lam', '4U', 'G5', NULL, 4290000, 12, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX88DP-02', N'Đen',      '3U', 'G5', NULL, 4290000,  1, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-AX88DP-03', N'Đồng',     '4U', 'G5', NULL, 4390000,  0, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 3. Yonex Nanoflare 800 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-NF800', N'Vợt cầu lông Yonex Nanoflare 800', N'Vợt nhẹ đầu, thân siêu mảnh cho tốc độ vung vợt nhanh, phòng thủ và phản tạt rất tốt. Dành cho lối đánh nhanh, phòng thủ phản công.',
        @yonex, @phongThu, 675, 285, 'HEAD_LIGHT', 'STIFF', 12.5,
        N'M40X graphite', N'Ultra Slim Shaft (graphite)', 'ACTIVE', 0,
        DATEADD(HOUR, -3, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-nf800-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-NF800-01', N'Xanh ngọc', '4U', 'G5', 4190000, 3990000, 9, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-NF800-02', N'Đỏ',        '4U', 'G5', 4190000, 3990000, 4, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-NF800-03', N'Hồng',      '3U', 'G6', 4190000, 3990000, 2, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 4. Yonex Arcsaber 11 Pro =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('YX-ARC11P', N'Vợt cầu lông Yonex Arcsaber 11 Pro', N'Vợt cân bằng kinh điển: khung giữ form giúp đường cầu chính xác, phù hợp lối đánh kiểm soát, đôi nam nữ và người mới lên trình.',
        @yonex, @toanDien, 675, 295, 'EVEN', 'MEDIUM', 12.5,
        N'Graphite + Namd', N'Namd', 'ACTIVE', 0,
        DATEADD(HOUR, -4, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/yx-arc11p-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'YX-ARC11P-01', N'Xanh dương', '4U', 'G5', NULL, 4190000, 10, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'YX-ARC11P-02', N'Bạc',        '3U', 'G5', NULL, 4190000,  0, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 5. Li-Ning Halbertec 9000 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('LN-HLBT9000', N'Vợt cầu lông Li-Ning Halbertec 9000', N'Vợt tấn công đầu bảng của Li-Ning với khung khí động học và thân siêu cứng, cho lực đập rất mạnh. Khuyến nghị cho người có lực cổ tay tốt.',
        @lining, @tanCong, 675, 305, 'HEAD_HEAVY', 'EXTRA_STIFF', 13.5,
        N'TB Nano + khung khí động học', N'Ultra High Density Carbon', 'ACTIVE', 0,
        DATEADD(HOUR, -5, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ln-hlbt9000-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/ln-hlbt9000-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'LN-HLBT9000-01', N'Đen nhám', '4U', 'G5', 4990000, 4590000, 5, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'LN-HLBT9000-02', N'Đen nhám', '3U', 'G5', 4990000, 4590000, 3, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 6. Li-Ning Axforce 90 Tiger =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('LN-AXF90T', N'Vợt cầu lông Li-Ning Axforce 90 Tiger', N'Thiết kế hoạ tiết hổ, nặng đầu và thân cứng vừa – cây vợt tấn công quốc dân tầm giá 3,5 triệu. Phù hợp cả đánh đơn và đánh đôi.',
        @lining, @tanCong, 675, 300, 'HEAD_HEAVY', 'STIFF', 13.0,
        N'Military-grade carbon', N'TB Nano', 'ACTIVE', 0,
        DATEADD(HOUR, -6, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ln-axf90t-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'LN-AXF90T-01', N'Đỏ',        '4U', 'G5', NULL, 3590000, 11, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'LN-AXF90T-02', N'Xanh rêu',  '4U', 'G5', NULL, 3590000,  7, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'LN-AXF90T-03', N'Đen',       '3U', 'G5', NULL, 3590000,  2, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 7. Li-Ning Windstorm 78 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('LN-WS78', N'Vợt cầu lông Li-Ning Windstorm 78', N'Vợt nhẹ, dễ đánh, giá mềm – rất hợp cho người mới chơi hoặc phong trào cần cây vợt bền, dễ làm quen.',
        @lining, @phongThu, 675, 285, 'HEAD_LIGHT', 'MEDIUM', 11.5,
        N'Graphite', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -7, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ln-ws78-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'LN-WS78-01', N'Xanh dương', '4U', 'G5', 1890000, 1690000, 15, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'LN-WS78-02', N'Trắng',      '5U', 'G5', 1890000, 1690000,  8, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 8. Victor Thruster Ryuga II =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('VC-TKRYUGA2', N'Vợt cầu lông Victor Thruster Ryuga II', N'Vợt tấn công nặng đầu của Victor với công nghệ Hard Cored tạo độ ổn định khi đập mạnh. Dòng vợt được nhiều tuyển thủ đôi nam tin dùng.',
        @victor, @tanCong, 675, 306, 'HEAD_HEAVY', 'EXTRA_STIFF', 13.5,
        N'Pyrofil + Hard Cored', N'Pyrofil', 'ACTIVE', 0,
        DATEADD(HOUR, -8, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/vc-tkryuga2-1.svg', 1, 0, SYSDATETIME()),
    (@p, '/images/rackets/vc-tkryuga2-2.svg', 0, 1, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'VC-TKRYUGA2-01', N'Đen kim tuyến', '4U', 'G5', NULL, 4350000, 0, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-TKRYUGA2-02', N'Đỏ',           '3U', 'G5', NULL, 4350000, 0, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-TKRYUGA2-03', N'Xanh',         '4U', 'G6', NULL, 4350000, 0, 'INACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 9. Victor Auraspeed 90K II =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('VC-ARS90K2', N'Vợt cầu lông Victor Auraspeed 90K II', N'Cân bằng thiên công, thân cứng vừa cho tốc độ vung tay nhanh mà vẫn đủ lực dứt điểm. Lựa chọn linh hoạt cho người chơi đa phong cách.',
        @victor, @toanDien, 675, 292, 'EVEN', 'STIFF', 12.5,
        N'Pyrofil', N'Pyrofil + thân cứng', 'ACTIVE', 0,
        DATEADD(HOUR, -9, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/vc-ars90k2-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'VC-ARS90K2-01', N'Xanh đen', '4U', 'G5', 4150000, 3890000, 9, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-ARS90K2-02', N'Trắng',    '4U', 'G5', 4150000, 3890000, 5, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-ARS90K2-03', N'Đỏ',       '3U', 'G5', 4150000, 3890000, 3, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 10. Victor JetSpeed 12 SE =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('VC-JS12SE', N'Vợt cầu lông Victor JetSpeed 12 SE', N'Bản đặc biệt của dòng JetSpeed: khung mỏng giảm lực cản không khí, hỗ trợ phòng thủ và phản tạt nhanh. Dễ chơi với người trình trung bình.',
        @victor, @toanDien, 675, 288, 'EVEN', 'MEDIUM', 12.0,
        N'Graphite khung mỏng', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -10, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/vc-js12se-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'VC-JS12SE-01', N'Xanh navy', '4U', 'G5', NULL, 2890000, 10, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'VC-JS12SE-02', N'Đen',       '4U', 'G5', NULL, 2890000,  6, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 11. Mizuno Acrospeed 7 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('MZ-AS7', N'Vợt cầu lông Mizuno Acrospeed 7', N'Vợt cân bằng dễ đánh của Mizuno, cảm giác cầu êm tay và kiểm soát tốt ở lưới. Phù hợp người chơi phong trào muốn cây vợt đa dụng.',
        @mizuno, @toanDien, 675, 290, 'EVEN', 'MEDIUM', 12.0,
        N'Graphite', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -11, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/mz-as7-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'MZ-AS7-01', N'Xanh', '4U', 'G5', 2890000, 2590000, 12, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'MZ-AS7-02', N'Đen',  '4U', 'G5', 2890000, 2590000,  4, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 12. Mizuno Altius 01 Speed =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('MZ-ALT01S', N'Vợt cầu lông Mizuno Altius 01 Speed', N'Vợt nhẹ đầu hướng tốc độ, phù hợp đánh đôi và lối chơi phòng thủ phản công. Thân cứng vừa nên vẫn đủ lực khi cần bung cầu.',
        @mizuno, @phongThu, 675, 285, 'HEAD_LIGHT', 'STIFF', 11.5,
        N'Graphite', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -12, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/mz-alt01s-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'MZ-ALT01S-01', N'Bạc',       '4U', 'G5', NULL, 2190000, 7, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'MZ-ALT01S-02', N'Xanh ngọc', '4U', 'G5', NULL, 2190000, 0, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 13. Kumpoo Power Shot 9900 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('KP-PS9900', N'Vợt cầu lông Kumpoo Power Shot 9900', N'Vợt tấn công giá rẻ đáng chơi: nặng đầu, thân cứng, cho lực đập tốt trong tầm giá dưới 2 triệu.',
        @kumpoo, @tanCong, 675, 300, 'HEAD_HEAVY', 'STIFF', 12.0,
        N'Graphite', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -13, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/kp-ps9900-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'KP-PS9900-01', N'Vàng đen', '4U', 'G5', 2190000, 1890000, 13, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'KP-PS9900-02', N'Đỏ đen',   '3U', 'G5', 2190000, 1890000,  5, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 14. Apacs Z-Ziggler 2 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('AP-ZZIGGLER2', N'Vợt cầu lông Apacs Z-Ziggler 2', N'Vợt tấn công phổ thông bán chạy của Apacs: nặng đầu, khung chắc, chịu được mức căng cao trong tầm giá rẻ.',
        @apacs, @tanCong, 675, 300, 'HEAD_HEAVY', 'STIFF', 12.5,
        N'Graphite dệt Woven', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -14, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ap-zziggler2-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'AP-ZZIGGLER2-01', N'Đen vàng',  '4U', 'G5', NULL, 1290000, 14, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'AP-ZZIGGLER2-02', N'Trắng xanh', '3U', 'G5', NULL, 1290000,  6, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

/* =========================== 15. Apacs Feather Weight 55 =========================== */
INSERT INTO products (product_code, name, description, brand_id, category_id, length_mm, balance_point_mm,
                      balance_type, shaft_flexibility, max_tension_kg, frame_material, shaft_material, status,
                      is_deleted, created_at, updated_at)
VALUES ('AP-FW55', N'Vợt cầu lông Apacs Feather Weight 55', N'Cây vợt siêu nhẹ (khoảng 55 gram) dễ vung, phù hợp người mới, nữ chơi phong trào hoặc cần vợt nhẹ cho cổ tay yếu.',
        @apacs, @phongThu, 675, 280, 'HEAD_LIGHT', 'MEDIUM', 11.0,
        N'Graphite siêu nhẹ', N'Graphite', 'ACTIVE', 0,
        DATEADD(HOUR, -15, SYSDATETIME()), SYSDATETIME());
SET @p = SCOPE_IDENTITY();
INSERT INTO product_images (product_id, image_url, is_primary, sort_order, created_at) VALUES
    (@p, '/images/rackets/ap-fw55-1.svg', 1, 0, SYSDATETIME());
INSERT INTO product_variants (product_id, sku, color, weight_class, grip_size, original_price, price, stock_quantity, status, created_at, updated_at) VALUES
    (@p, 'AP-FW55-01', N'Trắng', '5U', 'G5', 1190000, 990000, 16, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'AP-FW55-02', N'Hồng',  '5U', 'G5', 1190000, 990000,  9, 'ACTIVE', SYSDATETIME(), SYSDATETIME()),
    (@p, 'AP-FW55-03', N'Đen',   '4U', 'G5', 1190000, 990000,  0, 'ACTIVE', SYSDATETIME(), SYSDATETIME());

COMMIT TRAN;

/* ---- Báo cáo kết quả nạp ---- */
SELECT N'Tổng sản phẩm đang bán' AS muc, COUNT(*) AS so_luong FROM products WHERE is_deleted = 0 AND status = 'ACTIVE'
UNION ALL SELECT N'Biến thể đang bán', COUNT(*) FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.status = 'ACTIVE' AND p.is_deleted = 0
UNION ALL SELECT N'Biến thể đã ngừng bán (khách không thấy)', COUNT(*) FROM product_variants WHERE status = 'INACTIVE'
UNION ALL SELECT N'Ảnh sản phẩm', COUNT(*) FROM product_images
UNION ALL SELECT N'Sản phẩm tạm hết hàng (mọi biến thể tồn = 0)', COUNT(*) FROM products p WHERE p.is_deleted = 0 AND p.status = 'ACTIVE'
      AND NOT EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.status = 'ACTIVE' AND v.stock_quantity > 0);
