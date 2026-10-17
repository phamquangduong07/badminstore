# Checklist tiêu chí mức KHÁ (7–8 điểm) – Loại đề tài: Bán hàng

> Nguồn: `Tieu_Chi_DATN_Fall2024.xlsx`. Mức Khá = **đạt toàn bộ mức Trung bình + các mục nâng cao**.
> Đánh dấu `[x]` khi đã làm xong VÀ đã tự test. Dùng lệnh `/check-criteria` để AI đối chiếu.

## A. Giao diện – "Đẹp mắt: mức TB + dùng thêm framework FE"
- [ ] Layout gọn gàng, dùng Thymeleaf layout + fragments (header/footer/flash)
- [ ] Bootstrap 5, responsive (mobile/tablet/desktop)
- [ ] Dùng thêm ≥ 2 FE lib ngoài Bootstrap: Swiper (banner/slider), AOS (hiệu ứng), SweetAlert2 (xác nhận/thông báo), Chart.js (dashboard)
- [ ] Thống nhất màu sắc/font, logo, favicon, trang 404/403/500 đẹp

## B. Tính năng CƠ BẢN (bắt buộc – mức TB)
| # | Yêu cầu | Gợi ý triển khai | Hỗ trợ DB |
|---|---|---|---|
| 1 | Đăng ký/đăng nhập, phân quyền ADMIN/CUSTOMER | Spring Security + BCrypt | `users` |
| 2 | Giỏ hàng | Thêm/sửa/xoá dòng, theo biến thể (màu×U×G) | `cart_items` |
| 3 | Thanh toán (checkout) | COD / chuyển khoản / tiền mặt; chọn địa chỉ | `orders`, `order_items`, `payments`, `user_addresses` |
| 4 | Tìm kiếm | LIKE theo tên + lọc hãng/loại/khoảng giá, phân trang | `products`, `product_variants` |
| 5 | Đổi trạng thái đơn | PENDING→CONFIRMED→SHIPPING→DELIVERED / CANCELLED | `orders`, `order_status_histories` |
| 6 | **Xác nhận = trừ số lượng** | UPDATE atomic `stock_quantity >= qty` | `product_variants`, `CK_orders_stock` |
| 7 | **Xác nhận/thông báo khi thao tác CSDL** | SweetAlert2 confirm + flash message | – (UI) |
| 8 | **Tạo đơn thủ công (admin)** | `order_source='MANUAL'`, `created_by` | `orders` |
| 9 | Quản trị sản phẩm cơ bản | CRUD sản phẩm, biến thể, ảnh, hãng, loại | `products`, `product_variants`, `product_images`, `brands`, `categories` |
| 10 | **Voucher cho tổng hoá đơn** | Kiểm tra min/max/thời hạn/lượt dùng | `vouchers`, `orders.voucher_id` |

## C. Tính năng NÂNG CAO (mức Khá cần "một số" chức năng nâng cao – tiêu chí liệt kê theo kiểu "như")
> Nhóm đã quyết định **không làm khuyến mãi nhiều sản phẩm**; bù bằng việc làm thật tốt 4 mục còn lại + dashboard.
| # | Yêu cầu | Gợi ý triển khai | Hỗ trợ DB |
|---|---|---|---|
| 11 | **So sánh sản phẩm** | Lưu id trong session (tối đa 3–4 SP); bảng so sánh: chiều dài, điểm cân bằng, độ cứng thân, mức căng dây, chất liệu, giá | dùng cột kỹ thuật của `products` (không cần bảng mới) |
| 12 | **Đánh giá sản phẩm** | Sao 1–5 + bình luận; chỉ khách đã mua & đơn `DELIVERED`; mỗi dòng đơn đánh giá 1 lần; hiển thị điểm TB | **`product_reviews`** (patch) |
| 13 | ~~Khuyến mãi nhiều sản phẩm~~ → **Bỏ** (dự án chỉ dùng voucher). Tuỳ chọn nhẹ: admin sửa hàng loạt `original_price`/`price` của nhiều biến thể để hiện giá gạch ngang | Dùng voucher + cột `original_price` có sẵn | `product_variants` (không cần bảng mới) |
| 14 | **Validate chính xác số lượng** | Chặn thêm giỏ/đặt vượt tồn; kiểm tra lại lúc checkout & lúc xác nhận; báo rõ SKU thiếu | `CK_variants_stock`, logic Service |
| 15 | **Hoàn trả 100% đơn hàng** | `DELIVERED→RETURNED`, cộng lại kho, trả voucher, payment `REFUNDED`, ghi lý do | `orders.cancel_reason`, `payments` |

## D. Trải nghiệm người dùng – "Tốt: dễ dùng, thân thiện"
- [ ] Luồng mua hàng ngắn gọn (xem SP → chọn biến thể → giỏ → thanh toán ≤ 3 bước)
- [ ] Hiển thị tồn kho/"hết hàng", giá gốc (gạch ngang) – giá bán
- [ ] Trang "Đơn hàng của tôi" có timeline trạng thái; khách tự huỷ được khi `PENDING`
- [ ] Thông báo lỗi/thành công rõ ràng, tiếng Việt; trạng thái rỗng; phân trang
- [ ] Tốc độ ổn: ảnh nén, phân trang, index DB đã có sẵn

## E. Việc nên làm thêm để "chắc điểm Khá"
- [ ] Dashboard admin (Chart.js): doanh thu theo ngày/tháng, đơn theo trạng thái, top sản phẩm
- [ ] Dữ liệu mẫu đủ đẹp (≥ 20 vợt, ≥ 6 hãng, ảnh thật) để demo
- [ ] Test Service cho: state machine đơn, trừ/cộng kho, voucher, hoàn trả
- [ ] Báo cáo ghi rõ cách dùng AI (xem `docs/AI_USAGE_LOG.md`)
