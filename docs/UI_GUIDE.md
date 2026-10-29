# Hướng dẫn giao diện BadStore (AI và người làm đều phải theo)

Mục tiêu: nhìn như một shop thể thao thật – sạch, nhiều khoảng trắng, nhất quán. Chỉ dùng Bootstrap 5 + `static/css/theme.css`.

## 1. Nhận diện
- Palette thật nằm trong `:root` của `static/css/theme.css` (`--bad-*`, `--bs-primary`, `--bad-chart-*`).
  - Xanh nền nút: `#0b6b4a` (chữ trắng 6.53:1 – đạt AA), hover `#0a5c40`.
  - Xanh dùng làm **màu chữ** (link, nút outline, nhãn): `#0c7852` (5.49:1 trên trắng).
  - Nhấn CTA/vàng: `#ffb703` cho nền; chữ vàng dùng `#8a6100` (5.54:1).
  - Giá bán / giảm giá: `#d92b39` (4.82:1 trên trắng).
  - Nền `#f7f8fa`; chữ `#1f2937`; border `--bad-border`; nền thẻ `--bad-surface`.
  - Màu biểu đồ dashboard: `--bad-chart-1..6` (khai báo ở theme.css, JS đọc bằng `getComputedStyle`).
- Font **Be Vietnam Pro** (hỗ trợ tiếng Việt đẹp), nạp bằng Google Fonts: weights 400/500/600/700.
- Icon: Bootstrap Icons. Không trộn nhiều bộ icon. Icon trang trí luôn có `aria-hidden="true"`; nút/liên kết chỉ có icon phải có `aria-label`.
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
- **Tiêu đề trang:** dùng class `.page-title` (giỏ hàng, thanh toán, đơn hàng, địa chỉ, so sánh và toàn bộ khu admin) –
  cỡ hiển thị `clamp(1.75rem → 2.25rem)`, đậm 700, icon cùng dòng. Tiêu đề danh mục/sản phẩm dùng `.h3`.
  Nhờ vậy trang có điểm nhấn rõ (tỉ lệ tiêu đề/chữ thường ≥ 2.2 lần), không còn "đều đều" như bản cũ 24px.

## 4. Trang admin
- Layout: sidebar xanh đậm bên trái + thanh trên; nội dung nền sáng, mỗi khối là `card-soft`.
- Dashboard: 4 `stat-card` (doanh thu, đơn mới, đơn chờ xác nhận, sắp hết hàng) + biểu đồ doanh thu (Chart.js line) + đơn theo trạng thái (doughnut) + bảng đơn mới nhất.
- Bảng danh sách: tìm kiếm + lọc ở đầu, hành động dạng nút icon nhỏ, trạng thái dùng `status-pill`.
- Form: chia 2 cột, nhóm trường có tiêu đề, nút Lưu/Huỷ ở cuối, lỗi hiện dưới từng ô.

## 5. Tương tác (tạo cảm giác "xịn")
- SweetAlert2: xác nhận trước khi xoá/huỷ/đổi trạng thái; toast thành công/thất bại sau khi redirect.
- AOS `data-aos="fade-up"`: gắn ở **khối/hàng** (section, `.row`), không gắn từng thẻ trong lưới dài
  (tránh hàng chục phần tử ẩn phải quan sát, cuộn bị giật trên máy yếu). Không dùng cho bảng admin.
  Người bật "giảm chuyển động" (prefers-reduced-motion) được xem nội dung ngay, không hiệu ứng.
  `theme.css` ghi đè biên độ dịch chuyển từ 100px (mặc định AOS) xuống **16px** để lúc chờ hiệu ứng
  thẻ sản phẩm không bị đẩy chồng lên phân trang/nút bên dưới (giảm layout shift).
- Hover nổi nhẹ ở card; nút có trạng thái loading (disable + spinner) khi submit.
- Giỏ hàng là luồng Post-Redirect-Get: badge ở navbar cập nhật sau khi trang tải lại (không AJAX).
- Validate 2 lớp: HTML5 (`required`, `min`, `maxlength`, `pattern`) + Bean Validation; lỗi hiện dưới từng ô
  (`is-invalid` + `th:errors`), ô lỗi có `aria-invalid="true"`.
- Vùng nội dung tự đổi (kết quả POS, tồn kho theo phiên bản, mã giảm giá) cần `role="status"`/`aria-live`.

## 6. Ảnh & nội dung
- Ảnh sản phẩm nền trắng/trong suốt, cùng tỉ lệ; nén < 300KB; luôn có `alt`. Lưu vào `uploads/` hoặc `static/images/`, **không hotlink** ảnh từ web khác (dễ hỏng khi demo).
- Dữ liệu mẫu thật: ≥ 20 vợt, tên đúng kiểu hãng, giá hợp lý. Nội dung tiếng Việt có dấu, không "Lorem ipsum".
- Có trang 403/404/500 đẹp và trạng thái rỗng (`empty-state`).

## 7. Checklist trước khi demo
- [ ] Xem ở 4 kích thước: 320px, 375px, 768px, 1440px (không tràn ngang)
- [ ] Mọi trang dùng chung layout + font + màu; không còn trang "mặc định Bootstrap trơn"
- [ ] Không có ảnh vỡ, chữ tràn, nút lệch; không có chữ tiếng Anh sót lại
- [ ] Mọi thao tác ghi DB có xác nhận + thông báo
- [ ] Giá hiển thị `1.250.000 ₫`, ngày `dd/MM/yyyy HH:mm`
- [ ] Contrast chữ đủ rõ; nút/link bấm được bằng bàn phím (focus nhìn thấy)
