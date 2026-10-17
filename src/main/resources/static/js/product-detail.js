/* =============================================================================
   BadStore – JS riêng TRANG CHI TIẾT SẢN PHẨM (chỉ nạp ở shop/product-detail.html)
   (1) Bấm nút chọn phiên bản ⇒ đổi giá đang xem + tồn kho + giới hạn ô số lượng.
   (2) Nút "Chọn" ở bảng phiên bản (tab Thông số) ⇒ tick đúng phiên bản rồi cuộn lên khối mua hàng.
   (3) Mở sẵn tab "Đánh giá" khi URL có #danh-gia (sau khi khách gửi đánh giá).
   Tồn kho/giá thật vẫn được server kiểm tra lại khi thêm vào giỏ (CartService).
   ============================================================================= */
(function () {
    'use strict';

    /** Định dạng tiền ở trình duyệt cho khớp server: 1.250.000 ₫ */
    function formatMoney(value) {
        return new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫';
    }

    function initBuyBox() {
        var form = document.querySelector('[data-pdp-form]');
        if (!form) {
            return;
        }

        var variantInputs = Array.prototype.slice.call(form.querySelectorAll('input[name="variantId"]'));
        if (variantInputs.length === 0) {
            return;
        }

        var quantity = form.querySelector('input[name="quantity"]');
        var priceBox = document.querySelector('[data-pdp-price]');
        var oldPriceBox = document.querySelector('[data-pdp-old-price]');
        var stockText = document.querySelector('[data-pdp-stock-text]');
        // Thanh mua dính trên mobile (nếu có) – giữ giá khớp với phiên bản đang chọn
        var stickyPriceBox = document.querySelector('[data-pdp-price-sticky]');

        function checkedInput() {
            return variantInputs.filter(function (input) {
                return input.checked;
            })[0] || null;
        }

        /** Vẽ lại giá + tồn kho của phiên bản đang chọn và kẹp số lượng trong 1..tồn kho. */
        function render() {
            var input = checkedInput();
            if (!input) {
                return;
            }

            var stock = parseInt(input.getAttribute('data-stock') || '0', 10);
            var price = Number(input.getAttribute('data-price') || 0);
            var original = Number(input.getAttribute('data-original-price') || 0);

            if (priceBox && price > 0) {
                priceBox.textContent = formatMoney(price);
            }
            if (stickyPriceBox && price > 0) {
                stickyPriceBox.textContent = formatMoney(price);
            }
            if (oldPriceBox) {
                if (original > price) {
                    oldPriceBox.textContent = formatMoney(original);
                    oldPriceBox.classList.remove('d-none');
                } else {
                    oldPriceBox.textContent = '';
                    oldPriceBox.classList.add('d-none');
                }
            }

            if (stockText) {
                stockText.textContent = stock <= 0
                    ? 'Tạm hết hàng'
                    : (stock <= 3 ? 'Sắp hết hàng – chỉ còn ' + stock + ' sản phẩm' : 'Còn ' + stock + ' sản phẩm');
                stockText.classList.toggle('is-low', stock > 0 && stock <= 3);
                stockText.classList.toggle('is-out', stock <= 0);
            }

            if (quantity) {
                var max = stock > 0 ? stock : 1;
                quantity.setAttribute('max', String(max));
                var value = parseInt(quantity.value, 10);
                if (isNaN(value) || value < 1) {
                    value = 1;
                }
                quantity.value = String(Math.min(value, max));
                quantity.disabled = stock <= 0;
            }
        }

        variantInputs.forEach(function (input) {
            input.addEventListener('change', render);
        });

        document.querySelectorAll('[data-pdp-pick]').forEach(function (button) {
            button.addEventListener('click', function () {
                var wanted = button.getAttribute('data-pdp-pick');
                var target = variantInputs.filter(function (input) {
                    return input.value === wanted && !input.disabled;
                })[0];
                if (!target) {
                    return;
                }
                target.checked = true;
                render();
                var buyBox = document.querySelector('[data-pdp-buy]');
                if (buyBox) {
                    buyBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            });
        });

        render();
    }

    /** Gửi đánh giá xong server redirect về /san-pham/{id}#danh-gia ⇒ mở đúng tab Đánh giá. */
    function openReviewsTabFromHash() {
        if (window.location.hash !== '#danh-gia') {
            return;
        }
        var tabButton = document.getElementById('danh-gia-tab');
        if (tabButton && window.bootstrap && window.bootstrap.Tab) {
            window.bootstrap.Tab.getOrCreateInstance(tabButton).show();
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        initBuyBox();
        openReviewsTabFromHash();
    });

    // Khách đã ở trong trang rồi bấm liên kết tới #danh-gia (hoặc Back/Forward) cũng mở đúng tab
    window.addEventListener('hashchange', openReviewsTabFromHash);
})();
