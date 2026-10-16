# Checklist tiêu chí mức KHÁ (7–8 điểm) – Loại đề tài: Bán hàng

> Nguồn: `Tieu_Chi_DATN_Fall2024.xlsx`. Mức Khá = **đạt toàn bộ mức Trung bình + các mục nâng cao**.
> Đánh dấu `[x]` khi đã làm xong VÀ đã tự test. Dùng lệnh `/check-criteria` để AI đối chiếu.

## A. Giao diện – "Đẹp mắt: mức TB + dùng thêm framework FE" ✅ (đã xong 16/10/2026)
- [x] Layout gọn gàng, dùng Thymeleaf layout + fragments (header/footer/flash) — `layout/base.html`, `layout/admin.html`, `fragments/header|footer|flash|confirm|product-card`
- [x] Bootstrap 5, responsive (mobile/tablet/desktop) — grid + `table-responsive`, navbar có offcanvas menu
- [x] Dùng thêm ≥ 2 FE lib ngoài Bootstrap: Swiper (slider banner), AOS (hiệu ứng), SweetAlert2 (xác nhận + thông báo), Chart.js (dashboard), Bootstrap Icons
- [x] Thống nhất màu sắc/font, logo, favicon (`images/favicon.svg`), trang 404/403/500 đẹp
- [x] **Đợt tối ưu giao diện 16/10/2026**: `static/css/style.css` chuyển sang hệ **design token** (`:root` – màu thương hiệu, bo góc, bóng, transition) rồi dùng lại cho mọi trang: header dính có **ô tìm kiếm nhanh**, footer 3 cột, trang chủ (hero + danh mục nhanh + khối dịch vụ), thẻ sản phẩm, khối *dính* (`filter-card`/`buy-box`/`summary-card`/`pos-summary`), thẻ số liệu dashboard, bảng dữ liệu, timeline, POS và quy tắc `@media print` khi in phiếu. Toàn bộ nhãn cũ giữ nguyên nên test giao diện vẫn xanh

## B. Tính năng CƠ BẢN (bắt buộc – mức TB) — ✅ đã xong cả 10 mục
| # | Yêu cầu | Gợi ý triển khai | Hỗ trợ DB |
|---|---|---|---|
| 1 ✅ | Đăng ký/đăng nhập, phân quyền ADMIN/CUSTOMER | Spring Security + BCrypt (đăng nhập bằng email) | `users` |
| 2 ✅ | Giỏ hàng | Thêm/sửa/xoá dòng, theo biến thể (màu×U×G) | `cart_items` |
| 3 ✅ | Thanh toán (checkout) | COD / chuyển khoản / tiền mặt; chọn địa chỉ + sổ địa chỉ | `orders`, `order_items`, `payments`, `user_addresses` |
| 4 ✅ | Tìm kiếm | LIKE theo tên + lọc hãng/loại/khoảng giá, phân trang | `products`, `product_variants` |
| 5 ✅ | Đổi trạng thái đơn | PENDING→CONFIRMED→SHIPPING→DELIVERED / CANCELLED (state machine + ghi lịch sử) | `orders`, `order_status_histories` |
| 6 ✅ | **Xác nhận = trừ số lượng** | UPDATE atomic `stock_quantity >= qty`, thiếu thì rollback + báo SKU | `product_variants`, `CK_orders_stock` |
| 7 ✅ | **Xác nhận/thông báo khi thao tác CSDL** | SweetAlert2 confirm + flash message (PRG) | – (UI) |
| 8 ✅ | **Tạo đơn thủ công (admin)** | `order_source='MANUAL'`, `created_by`, `user_id` có thể NULL | `orders` |
| 9 ✅ | Quản trị sản phẩm cơ bản | CRUD sản phẩm (xoá mềm), biến thể, ảnh (upload ≤ 2 MB), hãng, loại | `products`, `product_variants`, `product_images`, `brands`, `categories` |
| 10 ✅ | **Voucher cho tổng hoá đơn** | Kiểm tra min/max/thời hạn/lượt dùng, trừ lượt atomic | `vouchers`, `orders.voucher_id` |

## C. Tính năng NÂNG CAO (mức Khá cần "một số" chức năng nâng cao – tiêu chí liệt kê theo kiểu "như")
> Nhóm đã quyết định **không làm khuyến mãi nhiều sản phẩm**; bù bằng việc làm thật tốt 4 mục còn lại + dashboard.
> ✅ Cả 5 mục dưới đây đã làm xong và có test (16/10/2026).
| # | Yêu cầu | Gợi ý triển khai | Hỗ trợ DB |
|---|---|---|---|
| 11 ✅ | **So sánh sản phẩm** | Lưu id trong session (tối đa 4 SP, `CompareService.MAX_ITEMS`); bảng so sánh: chiều dài, điểm cân bằng, độ cứng thân, mức căng dây, chất liệu, giá, tồn kho | dùng cột kỹ thuật của `products` (không cần bảng mới) |
| 12 ✅ | **Đánh giá sản phẩm** | Sao 1–5 + bình luận; chỉ khách đã mua & đơn `DELIVERED`; mỗi dòng đơn đánh giá 1 lần; hiển thị điểm TB | **`product_reviews`** (bảng có sẵn trong CSDL, không phải chạy patch) |
| 13 ✅ | ~~Khuyến mãi nhiều sản phẩm~~ → **Bỏ** (dự án chỉ dùng voucher). Làm mục tuỳ chọn nhẹ: admin **cập nhật giá hàng loạt** `original_price`/`price` của nhiều biến thể (giảm % / bỏ giảm giá) | Dùng voucher + cột `original_price` có sẵn; form "Cập nhật giá đã tích" ở trang biến thể | `product_variants` (không cần bảng mới) |
| 14 ✅ | **Validate chính xác số lượng** | Chặn thêm giỏ/đặt vượt tồn; kiểm tra lại lúc checkout & lúc xác nhận; báo rõ SKU thiếu | `CK_variants_stock`, logic Service |
| 15 ✅ | **Hoàn trả 100% đơn hàng** | `DELIVERED→RETURNED`, cộng lại kho, trả voucher, payment `REFUNDED`, ghi lý do | `orders.cancel_reason`, `payments` |

## D. Trải nghiệm người dùng – "Tốt: dễ dùng, thân thiện" ✅
- [x] Luồng mua hàng ngắn gọn (xem SP → chọn biến thể → giỏ → thanh toán ≤ 3 bước) — nút "Mua ngay" đi thẳng tới thanh toán
- [x] Hiển thị tồn kho/"hết hàng", giá gốc (gạch ngang) – giá bán, nhãn giảm %
- [x] Trang "Đơn hàng của tôi" có timeline trạng thái; khách tự huỷ được khi `PENDING` (và yêu cầu hoàn trả khi `DELIVERED`)
- [x] Thông báo lỗi/thành công rõ ràng, tiếng Việt; trạng thái rỗng; phân trang
- [x] Tốc độ ổn: ảnh SVG nhẹ (~2 KB/ảnh), phân trang 12 SP/trang, index DB có sẵn trong `BadStore.sql`

## E. Việc nên làm thêm để "chắc điểm Khá"
- [x] Dashboard admin (Chart.js): doanh thu theo ngày/tháng, đơn theo trạng thái, top sản phẩm
- [x] Dữ liệu mẫu đủ đẹp: **20 vợt, 6 hãng** (`docs/seed_san_pham.sql` 15 cây + `docs/seed_san_pham_bo_sung.sql` 5 cây). Ảnh là SVG tự vẽ trong `static/images/rackets/` (nhóm tự thiết kế để không vi phạm bản quyền ảnh hãng – đã thống nhất với GV hướng dẫn khi demo)
- [x] Test Service cho: state machine đơn, trừ/cộng kho, voucher, hoàn trả (JUnit 5 + Mockito; tổng **225 test**, chạy `.\mvnw -B clean test` → BUILD SUCCESS) — trong đó 47 test mới cho 2 chức năng ở mục F dưới đây
- [x] Báo cáo ghi rõ cách dùng AI (xem `docs/AI_USAGE_LOG.md`)

## F. Hai chức năng làm thêm ngoài checklist (điểm cộng khi demo) — 16/10/2026 ✅
> Không thêm bảng nào vào CSDL, không thêm dependency Maven (GHN gọi bằng `RestClient` có sẵn của Spring).
> Cả hai đều đã được kiểm chứng bằng **unit test + test tích hợp + smoke HTTP trên app thật** (xem `docs/AI_USAGE_LOG.md` dòng 19–21).

| # | Chức năng | Cài đặt | Kiểm chứng |
|---|---|---|---|
| F1 ✅ | **Bán hàng tại quầy (POS) `/admin/ban-hang`** | `CounterSaleService` + `AdminPosController`; tái dùng `OrderService.createManualOrder` (`order_source='MANUAL'`, `created_by`=admin, `user_id` NULL cho khách lẻ, xác nhận ngay ⇒ trừ kho, payment tiền mặt `PAID`); tìm theo SKU/tên, khách quen theo SĐT, tính tiền ở **server** (`POST /admin/ban-hang/tinh-tien`), phiếu bán hàng in được ở `/admin/ban-hang/phieu/{id}` | `CounterSaleServiceTest` (12) + `AdminPosIntegrationTest` (10: bán thành công, thiếu kho, thiếu tiền thối, khách quen, in phiếu) + smoke thật `pos-ghn-smoke.ps1` |
| F2 ✅ | **Tích hợp GHN (địa chỉ + phí ship + vận đơn + đồng bộ trạng thái)** | `GhnClient` (RestClient, cache 10 phút, `GhnApiException`), `GhnAddressService`, API nội bộ `/api/ghn/tinh`, `/api/ghn/huyen`, `/api/ghn/xa`, `/api/ghn/phi-ship` (token GHN **không lộ ra trình duyệt**), dropdown Tỉnh→Huyện→Xã ở sổ địa chỉ & thanh toán (luôn điền được bằng tay nếu GHN tắt/lỗi), phí ship thật thay cho 30.000 ₫ cố định, lưu mã `province_id/district_id/ward_code` vào đơn, nút **Đẩy đơn sang GHN** + **Đồng bộ trạng thái GHN** ở chi tiết đơn (đồng bộ đi qua từng bước state machine: `CONFIRMED→SHIPPING→DELIVERED`), scheduler tuỳ chọn `app.ghn.sync-enabled` | `GhnClientTest` (9: MockRestServiceServer kiểm tra URL/header/body/cache/lỗi), `GhnShippingServiceTest` (12: đường đi trạng thái, ánh xạ đã giao/hoàn/huỷ, chặn đẩy sai), `GhnIntegrationTest` (4: API địa chỉ, lưu mã GHN + bỏ mã bịa, phí GHN vào đơn, đẩy + đồng bộ ⇒ `DELIVERED` và COD `PAID`) |
| F3 ✅ | **Chế độ dữ liệu giả để demo không cần mạng** | `app.ghn.mock=true` → `GhnMockData` (2 tỉnh, 6 quận, các phường, phí nội thành 24.000 ₫, `GHNMOCK…`, `mock-status` để diễn trạng thái giao hàng) | Smoke thật trên cổng 8081: API địa chỉ ✓, phí 24.000 ₫ ✓, đẩy đơn ⇒ mã `GHNMOCK100001` ✓, đồng bộ ⇒ `DELIVERED` ✓, phiếu POS in được ✓, dữ liệu smoke đã dọn sạch |
