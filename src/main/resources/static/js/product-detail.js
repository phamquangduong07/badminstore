/* =============================================================================
   BadStore – JS riêng TRANG CHI TIẾT SẢN PHẨM (chỉ nạp ở shop/product-detail.html)
   (1) Bấm nút chọn phiên bản ⇒ đổi giá đang xem + tồn kho + giới hạn ô số lượng.
   (2) Nút "Chọn" ở bảng phiên bản (tab Thông số) ⇒ tick đúng phiên bản rồi cuộn lên khối mua hàng.
   (3) Mở sẵn tab "Đánh giá" khi URL có #danh-gia (sau khi khách gửi đánh giá).
   (4) Chọn phiên bản có ảnh riêng (product_variants.image_url) ⇒ đổi ảnh lớn cho đúng biến thể:
       ảnh nằm sẵn trong bộ ảnh sản phẩm thì chuyển carousel tới ảnh đó; ảnh riêng ngoài bộ ảnh
       thì thay tạm ảnh đang xem, khách tự bấm mũi tên/thumbnail là trả lại ảnh gốc của sản phẩm.
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

        // Vùng ảnh lớn: carousel nhiều ảnh (#productGallery) hoặc ảnh đơn / chỗ trống ảnh
        var gallery = document.getElementById('productGallery');
        var gallerySlides = gallery
            ? Array.prototype.slice.call(gallery.querySelectorAll('[data-pdp-slide]'))
            : [];
        var singleImage = gallery ? null : document.querySelector('[data-pdp-single]');
        // Ảnh đang bị thay tạm bằng ảnh riêng của phiên bản (nhớ ảnh gốc để trả lại)
        var swappedImage = null;

        function checkedInput() {
            return variantInputs.filter(function (input) {
                return input.checked;
            })[0] || null;
        }

        /** So khớp 2 đường dẫn ảnh (bỏ domain/query) để biết ảnh phiên bản có sẵn trong bộ ảnh không. */
        function sameImageUrl(left, right) {
            if (!left || !right) {
                return false;
            }
            function normalize(url) {
                try {
                    return new URL(url, window.location.origin).pathname.replace(/\/+$/, '').toLowerCase();
                } catch (error) {
                    return String(url).toLowerCase();
                }
            }
            return normalize(left) === normalize(right);
        }

        /** Trả lại ảnh gốc cho ảnh lớn nếu trước đó JS đã thay tạm bằng ảnh riêng của phiên bản. */
        function restoreSwappedImage() {
            if (!swappedImage) {
                return;
            }
            var image = swappedImage.element;
            image.setAttribute('src', swappedImage.src);
            if (swappedImage.alt == null) {
                image.removeAttribute('alt');
            } else {
                image.setAttribute('alt', swappedImage.alt);
            }
            image.onerror = null;
            swappedImage = null;
        }

        /**
         * Đồng bộ ảnh lớn với phiên bản đang chọn:
         * – ảnh phiên bản có sẵn trong bộ ảnh sản phẩm ⇒ chuyển carousel tới đúng ảnh đó;
         * – ảnh riêng ngoài bộ ảnh ⇒ thay tạm ảnh đang xem, trả lại ngay khi khách tự chuyển ảnh;
         * – phiên bản chưa khai báo ảnh ⇒ giữ nguyên bộ ảnh của sản phẩm như trước.
         */
        function showVariantImage(input) {
            var wanted = (input.getAttribute('data-image') || '').trim();

            if (!wanted) {
                restoreSwappedImage();
                return;
            }

            if (gallery && window.bootstrap && window.bootstrap.Carousel) {
                var matchIndex = -1;
                gallerySlides.forEach(function (slide, index) {
                    if (sameImageUrl(slide.getAttribute('src'), wanted)) {
                        matchIndex = index;
                    }
                });
                if (matchIndex >= 0) {
                    restoreSwappedImage();
                    window.bootstrap.Carousel.getOrCreateInstance(gallery).to(matchIndex);
                    return;
                }
            }

            var target = gallery
                ? gallery.querySelector('.carousel-item.active [data-pdp-slide]')
                : singleImage;
            if (!target) {
                restoreSwappedImage();
                return;
            }
            if (swappedImage && swappedImage.element !== target) {
                restoreSwappedImage();
            }
            if (!swappedImage) {
                swappedImage = {
                    element: target,
                    src: target.getAttribute('src'),
                    alt: target.getAttribute('alt')
                };
            }
            target.setAttribute('src', wanted);
            var wantedAlt = (input.getAttribute('data-image-alt') || '').trim();
            if (wantedAlt) {
                target.setAttribute('alt', wantedAlt);
            }
            // Ảnh phiên bản lỗi thì trả lại ảnh gốc để khách không thấy ảnh vỡ
            target.onerror = function () {
                restoreSwappedImage();
            };
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

            showVariantImage(input);
        }

        variantInputs.forEach(function (input) {
            input.addEventListener('change', render);
        });

        // Nạp trước ảnh riêng của từng phiên bản để lúc bấm chọn không bị chớp trắng
        variantInputs.forEach(function (input) {
            var url = (input.getAttribute('data-image') || '').trim();
            if (url) {
                var preload = new Image();
                preload.src = url;
            }
        });

        // Khách tự bấm mũi tên / thumbnail thì trả lại bộ ảnh gốc của sản phẩm
        if (gallery) {
            gallery.addEventListener('slide.bs.carousel', restoreSwappedImage);
        }

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
                    // Người bật "giảm chuyển động" thì nhảy thẳng, không cuộn mượt
                    var reduceMotion = window.matchMedia
                        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                    buyBox.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'center' });
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
