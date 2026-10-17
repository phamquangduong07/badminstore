# Hướng dẫn giao diện BadStore (AI và người làm đều phải theo)

Mục tiêu: nhìn như một shop thể thao thật – sạch, nhiều khoảng trắng, nhất quán. Chỉ dùng Bootstrap 5 + `static/css/theme.css`.

## 1. Nhận diện
- Màu chính xanh sân cầu `#0f8a5f`; nhấn CTA vàng `#ffb703`; giá/giảm giá đỏ `#e63946`; nền `#f7f8fa`; chữ `#1f2937`.
- Font **Be Vietnam Pro** (hỗ trợ tiếng Việt đẹp), nạp bằng Google Fonts: weights 400/500/600/700.
- Icon: Bootstrap Icons. Không trộn nhiều bộ icon.
- Bo góc lớn (0.75–1rem), bóng đổ nhẹ, nút bo tròn. Không viền đen dày, không gradient sặc sỡ.
- **Không viết CSS inline / màu hard-code** trong template: dùng class của theme.css hoặc biến `--bad-*`.

## 2. Khoảng cách & lưới
- Container `container` (max ~1140px), section cách nhau `py-5`, thẻ cách nhau `g-3`/`g-4`.
- Lưới sản phẩm: `row-cols-2 row-cols-md-3 row-cols-lg-4`. Mobile luôn 2 cột.
- Một trang chỉ có 1 nút CTA chính (xanh/vàng); các nút phụ dùng `btn-outline-*`.

## 3. Trang khách – bố cục chuẩn
- **Trang chủ:** navbar dính trên cùng (logo, ô tìm kiếm, giỏ hàng có badge số lượng, đăng nhập) → banner Swiper (3 slide, có CTA) → dải lợi ích (Chính hãng / Giao nhanh / Đổi trả) → "Sản phẩm mới" → "Theo hãng" (Yonex, Li-Ning, Victor...) → footer.
- **Danh sách:** sidebar lọc bên trái (hãng, loại, khoảng giá, độ cứng thân) – mobile chuyển thành offcanvas; sắp xếp ở phía trên; phân trang dưới; hiện số kết quả.
- **Product card:** ảnh vuông nền trắng `object-fit: contain`, tên tối đa 2 dòng, nhãn hãng, giá (đỏ) + giá gạch ngang, nút "Thêm vào giỏ"; hết hàng thì làm mờ + nhãn "Hết hàng".
- **Chi tiết:** ảnh lớn + thumbnail bên trái; bên phải: tên, hãng, giá, chọn biến thể (màu/U/G) bằng nút chọn, số lượng (+/−), tồn kho, nút Thêm vào giỏ / Mua ngay; bên dưới tab: Mô tả – Thông số kỹ thuật (bảng) – Đánh giá (sao + bình luận).
- **Giỏ hàng/Thanh toán:** 2 cột (danh sách | tóm tắt đơn dính bên phải); thanh bước Giỏ hàng → Thanh toán → Hoàn tất; tóm tắt luôn hiện tạm tính, voucher, phí ship, tổng.
- **Đơn của tôi:** thẻ đơn + `status-pill` màu theo trạng thái, timeline trạng thái trong chi tiết.

## 4. Trang admin
- Layout: sidebar xanh đậm bên trái + thanh trên; nội dung nền sáng, mỗi khối là `card-soft`.
- Dashboard: 4 `stat-card` (doanh thu, đơn mới, đơn chờ xác nhận, sắp hết hàng) + biểu đồ doanh thu (Chart.js line) + đơn theo trạng thái (doughnut) + bảng đơn mới nhất.
- Bảng danh sách: tìm kiếm + lọc ở đầu, hành động dạng nút icon nhỏ, trạng thái dùng `status-pill`.
- Form: chia 2 cột, nhóm trường có tiêu đề, nút Lưu/Huỷ ở cuối, lỗi hiện dưới từng ô.

## 5. Tương tác (tạo cảm giác "xịn")
- SweetAlert2: xác nhận trước khi xoá/huỷ/đổi trạng thái; toast thành công/thất bại sau khi redirect.
- AOS `data-aos="fade-up"` cho các section trang chủ (không dùng cho bảng admin).
- Hover nổi nhẹ ở card; nút có trạng thái loading (disable + spinner) khi submit.
- Giỏ hàng cập nhật số lượng ở navbar ngay sau khi thêm.

## 6. Ảnh & nội dung
- Ảnh sản phẩm nền trắng/trong suốt, cùng tỉ lệ; nén < 300KB; luôn có `alt`. Lưu vào `uploads/` hoặc `static/images/`, **không hotlink** ảnh từ web khác (dễ hỏng khi demo).
- Dữ liệu mẫu thật: ≥ 20 vợt, tên đúng kiểu hãng, giá hợp lý. Nội dung tiếng Việt có dấu, không "Lorem ipsum".
- Có trang 403/404/500 đẹp và trạng thái rỗng (`empty-state`).

## 7. Checklist trước khi demo
- [ ] Xem ở 3 kích thước: điện thoại (375px), tablet (768px), desktop (1366px)
- [ ] Mọi trang dùng chung layout + font + màu; không còn trang "mặc định Bootstrap trơn"
- [ ] Không có ảnh vỡ, chữ tràn, nút lệch; không có chữ tiếng Anh sót lại
- [ ] Mọi thao tác ghi DB có xác nhận + thông báo
- [ ] Giá hiển thị `1.250.000 ₫`, ngày `dd/MM/yyyy HH:mm`
- [ ] Contrast chữ đủ rõ; nút/link bấm được bằng bàn phím (focus nhìn thấy)
