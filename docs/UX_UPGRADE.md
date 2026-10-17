# Mẫu nâng cấp UX (AI dùng đúng các mẫu này, không tự chế)

`static/js/app.js` đã có sẵn hành vi chung. AI chỉ cần gắn thuộc tính HTML, không viết lại JS.

## 1. Xác nhận khi ghi DB
```html
<form th:action="@{/admin/products/{id}/delete(id=${p.id})}" method="post"
      data-confirm="Xoá sản phẩm này? Hành động không thể hoàn tác.">
  <button class="btn btn-sm btn-outline-danger" aria-label="Xoá sản phẩm"><i class="bi bi-trash"></i></button>
</form>
```
Sau redirect, thông báo hiện qua `fragments/flash.html` (`success` / `error`).

## 2. Bộ chọn số lượng (giỏ hàng, chi tiết)
```html
<div data-qty class="input-group qty-box">
  <button type="button" class="btn btn-outline-secondary" data-qty-minus aria-label="Giảm">−</button>
  <input type="number" name="quantity" class="form-control" min="1" th:max="${variant.stockQuantity}" value="1">
  <button type="button" class="btn btn-outline-secondary" data-qty-plus aria-label="Tăng">+</button>
</div>
```

## 3. Chọn biến thể bằng chip
```html
<input type="radio" class="btn-check" name="variantId" id="v1" th:value="${v.id}" th:disabled="${v.stockQuantity == 0}">
<label class="variant-chip" for="v1" th:text="${v.label}">4U – G5</label>
```

## 4. Thanh mua cố định trên mobile (trang chi tiết)
Bọc nội dung trang bằng `class="has-sticky-buy"`, thêm cuối trang:
```html
<div class="sticky-buy d-lg-none d-flex align-items-center justify-content-between gap-2">
  <span class="price" th:text="${#numbers.formatDecimal(price,0,'POINT',0,'COMMA')} + ' ₫'"></span>
  <button class="btn btn-primary flex-grow-1" form="addToCartForm">Thêm vào giỏ</button>
</div>
```

## 5. Mẫu theo trang
- **Danh sách:** số kết quả; chip bộ lọc đang chọn + nút "Xoá lọc"; sắp xếp; ảnh `loading="lazy"`; sản phẩm hết hàng làm mờ + nhãn.
- **Chi tiết:** breadcrumb; gallery + thumbnail; chip biến thể (hết hàng thì disabled); stepper `max = tồn kho`; thanh mua dính mobile; dải tin cậy (chính hãng / đổi trả); sản phẩm liên quan; tóm tắt sao đánh giá.
- **Giỏ hàng:** stepper, thành tiền từng dòng, xoá có confirm, tóm tắt `sticky-summary`, giỏ rỗng có nút "Tiếp tục mua sắm".
- **Thanh toán:** thanh bước; `type="tel" inputmode="numeric" autocomplete="tel"`, `autocomplete` cho tên/địa chỉ; lỗi dưới từng ô; nút submit loading; tóm tắt đơn thu gọn trên mobile.
- **Đơn của tôi:** tab lọc theo trạng thái, `status-pill`, timeline, huỷ đơn có confirm.
- **Admin:** sidebar thu gọn (offcanvas) trên mobile; bảng `table-responsive table-sticky`; ô tìm kiếm + lọc trên bảng; mọi thao tác ghi có confirm + toast; stat-card + Chart.js ở dashboard.
- **Toàn site:** skip-link, breadcrumb, `aria-label` cho nút icon, ảnh có `alt`, nút lên đầu trang, favicon, trang lỗi 403/404/500 có nút về trang chủ.

## 6. Ngoài phạm vi (AI chỉ ĐỀ XUẤT, không tự làm vì cần sửa Java/DB)
Thêm giỏ hàng bằng AJAX, gợi ý tìm kiếm tự động, lưu danh sách yêu thích, lọc không tải lại trang.
