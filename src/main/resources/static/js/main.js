/* BadStore – JavaScript dùng chung
   Nhiệm vụ: (1) toast thông báo flash bằng SweetAlert2, (2) hộp thoại xác nhận TRƯỚC mọi thao tác ghi CSDL,
   (3) khởi tạo AOS (hiệu ứng cuộn) và Swiper (slider banner), (4) bộ chọn số lượng (+/−). */
(function () {
    'use strict';

    var DEFAULT_CONFIRM_TEXT = 'Bạn chắc chắn muốn thực hiện thao tác này?';

    /** Toast góc phải trên – dùng chung cho cả thành công và thất bại. */
    function showToast(icon, title) {
        if (!window.Swal) {
            return;
        }
        Swal.fire({
            toast: true,
            position: 'top-end',
            icon: icon,
            title: title,
            showConfirmButton: false,
            timer: 4500,
            timerProgressBar: true,
            didOpen: function (element) {
                element.addEventListener('mouseenter', Swal.stopTimer);
                element.addEventListener('mouseleave', Swal.resumeTimer);
            }
        });
    }

    /** Đọc flash message do server gắn vào #flash-notifications rồi bật toast. */
    function initFlashToasts() {
        var flash = document.getElementById('flash-notifications');
        if (!flash) {
            return;
        }
        if (flash.dataset.success) {
            showToast('success', flash.dataset.success);
        }
        if (flash.dataset.error) {
            showToast('error', flash.dataset.error);
        }
        if (flash.dataset.info) {
            showToast('info', flash.dataset.info);
        }
    }

    /** Hộp thoại xác nhận trước khi submit form / theo link có data-confirm. */
    function askConfirm(message) {
        return Swal.fire({
            icon: 'warning',
            title: 'Xác nhận thao tác',
            text: message || DEFAULT_CONFIRM_TEXT,
            showCancelButton: true,
            confirmButtonText: 'Đồng ý',
            cancelButtonText: 'Huỷ bỏ',
            confirmButtonColor: '#0b6b4a', // trùng --bs-primary trong theme.css (chữ trắng 6.53:1)
            cancelButtonColor: '#6c757d',
            reverseButtons: true
        });
    }

    /**
     * Sau khi khách đồng ý: khoá nút gửi + gắn vòng xoay để biết máy chủ đang xử lý.
     * Chỉ khoá nút KHÔNG có name (nút có name sẽ bị bỏ khỏi dữ liệu form khi disable).
     */
    function setFormLoading(form) {
        form.querySelectorAll('button[type="submit"]').forEach(function (button) {
            var spinner = document.createElement('span');
            spinner.className = 'spinner-border spinner-border-sm me-1';
            spinner.setAttribute('aria-hidden', 'true');
            button.prepend(spinner);
            if (!button.name) {
                button.disabled = true;
            }
        });
    }

    function initConfirmations() {
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                // app.js (nạp sau) đánh dấu '1', main.js đánh dấu 'true' ⇒ chỉ cần kiểm tra truthy
                // để không hiện hộp thoại xác nhận lần thứ hai khi cả hai script cùng nạp.
                if (form.dataset.confirmed || !window.Swal) {
                    return;
                }
                event.preventDefault();
                askConfirm(form.dataset.confirm).then(function (result) {
                    if (result.isConfirmed) {
                        form.dataset.confirmed = 'true';
                        setFormLoading(form);
                        form.submit();
                    }
                });
            });
        });

        document.querySelectorAll('a[data-confirm]').forEach(function (link) {
            link.addEventListener('click', function (event) {
                if (!window.Swal) {
                    return;
                }
                event.preventDefault();
                askConfirm(link.dataset.confirm).then(function (result) {
                    if (result.isConfirmed) {
                        window.location.href = link.href;
                    }
                });
            });
        });
    }

    /** Nút +/- cạnh ô số lượng (giỏ hàng, trang chi tiết sản phẩm) – luôn kẹp trong khoảng min/max của ô. */
    function initQuantitySteppers() {
        document.querySelectorAll('.js-quantity').forEach(function (group) {
            var input = group.querySelector('input[type="number"]');
            if (!input) {
                return;
            }
            function step(delta) {
                var min = parseInt(input.getAttribute('min') || '1', 10);
                var max = parseInt(input.getAttribute('max') || '999', 10);
                var value = parseInt(input.value, 10);
                if (isNaN(value)) {
                    value = min;
                }
                value = value + delta;
                if (value < min) {
                    value = min;
                }
                if (value > max) {
                    value = max;
                }
                input.value = String(value);
            }
            var down = group.querySelector('.js-quantity-down');
            var up = group.querySelector('.js-quantity-up');
            if (down) {
                down.addEventListener('click', function () {
                    step(-1);
                });
            }
            if (up) {
                up.addEventListener('click', function () {
                    step(1);
                });
            }
        });
    }

    function initSwiper() {
        if (!window.Swiper) {
            return;
        }
        // Người bật "giảm chuyển động": tắt tự chạy, chỉ đổi slide khi người dùng bấm
        var reduceMotion = window.matchMedia
            && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        document.querySelectorAll('.js-swiper').forEach(function (element) {
            new Swiper(element, {
                loop: true,
                // Dừng tự chạy khi rê chuột / focus bàn phím vào slider
                autoplay: reduceMotion ? false : { delay: 4500, disableOnInteraction: false, pauseOnMouseEnter: true },
                keyboard: { enabled: true },
                a11y: {
                    enabled: true,
                    prevSlideMessage: 'Banner trước',
                    nextSlideMessage: 'Banner sau',
                    paginationBulletMessage: 'Tới banner {{index}}'
                },
                pagination: {
                    el: element.querySelector('.swiper-pagination'),
                    clickable: true
                }
            });
        });
    }

    /**
     * Form tạo đơn thủ công (admin): thêm/xoá dòng hàng và ĐÁNH LẠI chỉ số lines[i]
     * để Spring MVC bind đúng vào List<ManualOrderLineForm>.
     */
    function initManualOrderLines() {
        var container = document.querySelector('[data-manual-lines]');
        if (!container) {
            return;
        }
        var template = document.getElementById('manual-line-template');

        function rows() {
            return container.querySelectorAll('.manual-line');
        }

        function reindex() {
            rows().forEach(function (row, index) {
                row.querySelectorAll('[data-field]').forEach(function (field) {
                    field.name = 'lines[' + index + '].' + field.dataset.field;
                });
            });
            var onlyOneRow = rows().length === 1;
            rows().forEach(function (row) {
                var remove = row.querySelector('.js-remove-line');
                if (remove) {
                    remove.disabled = onlyOneRow;
                }
            });
        }

        container.addEventListener('click', function (event) {
            if (event.target.closest('[data-manual-line-add]')) {
                if (template) {
                    var row = template.content.firstElementChild.cloneNode(true);
                    container.appendChild(row);
                    reindex();
                    var skuInput = row.querySelector('input[data-field="sku"]');
                    if (skuInput) {
                        skuInput.focus();
                    }
                }
                return;
            }

            var removeButton = event.target.closest('.js-remove-line');
            if (removeButton && rows().length > 1) {
                removeButton.closest('.manual-line').remove();
                reindex();
            }
        });

        reindex();
    }

    /* Tich/bỏ tich tất cả cho bảng cập nhật giá hàng loạt ở trang quản trị biến thể. */
    function initBulkPriceTable() {
        var checkAll = document.getElementById('bulkCheckAll');
        if (!checkAll) {
            return;
        }
        var boxes = Array.prototype.slice.call(document.querySelectorAll('input[name="variantIds"]'));
        checkAll.addEventListener('change', function () {
            boxes.forEach(function (box) {
                box.checked = checkAll.checked;
            });
        });
        boxes.forEach(function (box) {
            box.addEventListener('change', function () {
                checkAll.checked = boxes.every(function (item) {
                    return item.checked;
                });
            });
        });
    }

    /** Định dạng tiền ở trình duyệt cho khớp với server (#,### ₫). */
    function formatMoney(value) {
        return new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫';
    }

    /** Gọi API JSON nội bộ, lỗi mạng thì ném ra để nơi gọi tự xử lý (giao diện không được vỡ). */
    function fetchJson(url) {
        return fetch(url, { headers: { Accept: 'application/json' } }).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        });
    }

    /**
     * Sổ địa chỉ: 3 ô Tỉnh → Huyện → Xã lấy trực tiếp từ API GHN.
     * Mỗi lần chọn, tên địa danh được điền vào ô ẩn đi kèm mã GHN; server vẫn kiểm tra lại
     * mã (tồn tại thật) và tên (khớp mã) trước khi lưu nên khách không sửa được để ăn phí ship rẻ.
     */
    function initGhnAddressPicker() {
        var picker = document.querySelector('[data-ghn-picker]');
        if (!picker) {
            return;
        }
        var provinceSelect = picker.querySelector('[data-ghn-province]');
        var districtSelect = picker.querySelector('[data-ghn-district]');
        var wardSelect = picker.querySelector('[data-ghn-ward]');
        var provinceName = picker.querySelector('[data-ghn-province-name]');
        var districtName = picker.querySelector('[data-ghn-district-name]');
        var wardName = picker.querySelector('[data-ghn-ward-name]');
        var feeLabel = picker.querySelector('[data-ghn-fee]');
        var wardHint = picker.querySelector('[data-ghn-ward-hint]');
        // Lựa chọn cũ do server render lại sau khi form báo lỗi (Quận/Xã nạp lại bằng AJAX nên phải khôi phục).
        var restoreDistrict = picker.getAttribute('data-ghn-district') || '';
        var restoreWard = picker.getAttribute('data-ghn-ward') || '';
        // Shop chưa khai báo kho lấy hàng ⇒ vẫn chọn được địa chỉ GHN, chỉ không tạm tính được phí.
        var shippingEnabled = picker.getAttribute('data-ghn-shipping') !== 'false';

        function setOptions(select, items, placeholder) {
            if (!select) {
                return;
            }
            var markup = '<option value="">' + placeholder + '</option>';
            items.forEach(function (item) {
                var name = String(item.name == null ? '' : item.name).replace(/&/g, '&amp;').replace(/</g, '&lt;');
                var attribute = String(item.name == null ? '' : item.name).replace(/"/g, '&quot;');
                markup += '<option value="' + item.id + '" data-name="' + attribute + '">' + name + '</option>';
            });
            select.innerHTML = markup;
            select.disabled = items.length === 0;
        }

        function selectedName(select) {
            if (!select) {
                return '';
            }
            var option = select.options[select.selectedIndex];
            return option ? (option.getAttribute('data-name') || '') : '';
        }

        /** Ghi tên địa danh của ô đang chọn vào ô ẩn để gửi kèm mã GHN. */
        function fillName(input, select) {
            if (input) {
                input.value = selectedName(select);
            }
        }

        function clearName(input) {
            if (input) {
                input.value = '';
            }
        }

        function setFee(text) {
            if (feeLabel) {
                feeLabel.textContent = text;
            }
        }

        /** Quận/huyện mà GHN không trả phường/xã nào (vài quận đặc biệt) – báo để khách đổi quận khác. */
        function setWardHint(visible) {
            if (wardHint) {
                wardHint.classList.toggle('d-none', !visible);
            }
        }

        function refreshFee() {
            if (!shippingEnabled) {
                return;
            }
            var districtId = districtSelect ? districtSelect.value : '';
            if (!districtId) {
                setFee('--');
                return;
            }
            var wardCode = wardSelect ? wardSelect.value : '';
            fetchJson('/api/ghn/phi-ship?districtId=' + encodeURIComponent(districtId)
                + '&wardCode=' + encodeURIComponent(wardCode) + '&quantity=1')
                .then(function (data) {
                    setFee(data.fee == null ? 'sẽ báo khi xác nhận' : formatMoney(data.fee));
                })
                .catch(function () {
                    setFee('chưa tính được');
                });
        }

        function resetWard() {
            setOptions(wardSelect, [], '-- Chọn phường/xã --');
            setWardHint(false);
            clearName(wardName);
            setFee('--');
        }

        function resetDistrictAndWard() {
            setOptions(districtSelect, [], '-- Chọn quận/huyện --');
            clearName(districtName);
            resetWard();
        }

        /** Tìm option theo giá trị (dùng khi khôi phục lựa chọn sau khi form báo lỗi). */
        function optionByValue(select, value) {
            if (!select || value == null) {
                return null;
            }
            return Array.prototype.find.call(select.options, function (option) {
                return option.value === String(value);
            }) || null;
        }

        /** Nạp quận/huyện của tỉnh đã chọn; trả về Promise để nơi gọi nạp tiếp phường/xã. */
        function loadDistricts(provinceId) {
            if (!districtSelect || !provinceId) {
                return Promise.resolve();
            }
            districtSelect.innerHTML = '<option value="">Đang tải…</option>';
            districtSelect.disabled = true;
            return fetchJson('/api/ghn/huyen?provinceId=' + encodeURIComponent(provinceId))
                .then(function (data) {
                    setOptions(districtSelect, data.items || [], '-- Chọn quận/huyện --');
                })
                .catch(function () {
                    setOptions(districtSelect, [], '-- Chọn quận/huyện --');
                });
        }

        /** Nạp phường/xã của quận đã chọn. */
        function loadWards(districtId) {
            if (!wardSelect || !districtId) {
                return Promise.resolve();
            }
            wardSelect.innerHTML = '<option value="">Đang tải…</option>';
            wardSelect.disabled = true;
            return fetchJson('/api/ghn/xa?districtId=' + encodeURIComponent(districtId))
                .then(function (data) {
                    var items = data.items || [];
                    setOptions(wardSelect, items, '-- Chọn phường/xã --');
                    setWardHint(items.length === 0);
                })
                .catch(function () {
                    setOptions(wardSelect, [], '-- Chọn phường/xã --');
                    setWardHint(false);
                });
        }

        /** Form hiển thị lại sau khi báo lỗi: khôi phục đúng Tỉnh → Huyện → Xã khách đã chọn. */
        function restoreSelection() {
            if (!restoreDistrict || !provinceSelect || !provinceSelect.value) {
                refreshFee();
                return;
            }
            loadDistricts(provinceSelect.value).then(function () {
                if (!optionByValue(districtSelect, restoreDistrict)) {
                    return;
                }
                districtSelect.value = restoreDistrict;
                fillName(districtName, districtSelect);
                refreshFee();
                if (!restoreWard) {
                    return;
                }
                return loadWards(restoreDistrict).then(function () {
                    if (optionByValue(wardSelect, restoreWard)) {
                        wardSelect.value = restoreWard;
                        fillName(wardName, wardSelect);
                    }
                    refreshFee();
                });
            });
        }

        if (provinceSelect) {
            provinceSelect.addEventListener('change', function () {
                fillName(provinceName, provinceSelect);
                resetDistrictAndWard();
                loadDistricts(provinceSelect.value);
            });
        }

        if (districtSelect) {
            districtSelect.addEventListener('change', function () {
                fillName(districtName, districtSelect);
                resetWard();
                loadWards(districtSelect.value);
                refreshFee();
            });
        }

        if (wardSelect) {
            wardSelect.addEventListener('change', function () {
                fillName(wardName, wardSelect);
                refreshFee();
            });
        }

        restoreSelection();
    }

    /**
     * Trang thanh toán: khách đổi địa chỉ nhận hàng thì xem trước phí giao hàng GHN.
     * Chỉ là con số THAM KHẢO trên giao diện – CheckoutService vẫn tính lại phí thật khi lưu đơn.
     */
    function initGhnFeePreview() {
        var panel = document.querySelector('[data-ghn-fee-preview]');
        if (!panel) {
            return;
        }
        var subtotal = Number(panel.dataset.subtotal || 0);
        var discount = Number(panel.dataset.discount || 0);
        var defaultFee = Number(panel.dataset.defaultFee || 0);
        var freeShipping = panel.dataset.freeShipping === 'true';
        var weight = Number(panel.dataset.weight || 0);
        var feeLabel = panel.querySelector('[data-shipping-fee]');
        var totalLabel = panel.querySelector('[data-order-total]');
        var sourceBadge = panel.querySelector('[data-shipping-source]');
        var shippingEnabled = panel.getAttribute('data-ghn-shipping') !== 'false';

        function applyFee(fee, fromGhn) {
            if (freeShipping) {
                return;
            }
            if (feeLabel) {
                feeLabel.textContent = formatMoney(fee);
            }
            if (sourceBadge) {
                sourceBadge.classList.toggle('d-none', !fromGhn);
            }
            if (totalLabel) {
                totalLabel.textContent = formatMoney(subtotal - discount + fee);
            }
        }

        function refresh() {
            if (!shippingEnabled) {
                applyFee(defaultFee, false);
                return;
            }
            var checked = document.querySelector('input[name="addressId"]:checked');
            var districtId = checked ? (checked.getAttribute('data-district-id') || '') : '';
            var wardCode = checked ? (checked.getAttribute('data-ward-code') || '') : '';
            if (!districtId) {
                applyFee(defaultFee, false);
                return;
            }
            fetchJson('/api/ghn/phi-ship?districtId=' + encodeURIComponent(districtId)
                + '&wardCode=' + encodeURIComponent(wardCode) + '&weight=' + weight)
                .then(function (data) {
                    applyFee(data.fee == null ? defaultFee : Number(data.fee), data.fee != null);
                })
                .catch(function () {
                    applyFee(defaultFee, false);
                });
        }

        document.querySelectorAll('input[name="addressId"]').forEach(function (radio) {
            radio.addEventListener('change', refresh);
        });
        refresh();
    }

    /**
     * Nút hiện/ẩn mật khẩu ở trang đăng nhập/đăng ký (chỉ đổi type của ô nhập,
     * giá trị gửi lên server không thay đổi).
     */
    function initPasswordToggles() {
        document.querySelectorAll('[data-toggle-password]').forEach(function (button) {
            var input = document.querySelector(button.dataset.togglePassword);
            if (!input) {
                return;
            }
            button.addEventListener('click', function () {
                var isHidden = input.type === 'password';
                input.type = isHidden ? 'text' : 'password';
                button.setAttribute('aria-pressed', isHidden ? 'true' : 'false');
                button.setAttribute('aria-label', isHidden ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
                var icon = button.querySelector('i');
                if (icon) {
                    icon.className = isHidden ? 'bi bi-eye-slash' : 'bi bi-eye';
                }
                input.focus();
            });
        });
    }

    /**
     * Trang danh mục sản phẩm: form có data-auto-submit (bộ lọc hãng/loại và ô "Sắp xếp") tự gửi
     * ngay khi khách đổi lựa chọn. Vẫn là GET nên URL copy được, F5 không hỏi lại.
     * Riêng ô nhập khoảng giá KHÔNG tự gửi để tránh lọc liên tục khi khách đang gõ số.
     */
    function initAutoSubmitForms() {
        document.querySelectorAll('form[data-auto-submit]').forEach(function (form) {
            form.querySelectorAll('select, input[type="radio"], input[type="checkbox"]').forEach(function (control) {
                control.addEventListener('change', function () {
                    if (typeof form.requestSubmit === 'function') {
                        form.requestSubmit();
                    } else {
                        form.submit();
                    }
                });
            });
        });
    }

    /**
     * Header khách: đánh dấu mục menu của trang hiện tại bằng aria-current="page"
     * (hỗ trợ trình đọc màn hình). Suy ra từ URL nên không cần controller gắn biến mới.
     */
    function initNavCurrent() {
        var currentPath = window.location.pathname;
        document.querySelectorAll('.navbar-bad .navbar-nav a.nav-link[href]').forEach(function (link) {
            var href = link.getAttribute('href');
            if (!href || href.charAt(0) === '#') {
                return;
            }
            var url;
            try {
                url = new URL(link.href, window.location.origin);
            } catch (error) {
                return;
            }
            if (url.origin !== window.location.origin) {
                return;
            }
            var isCurrent = url.pathname === '/'
                ? currentPath === '/'
                : (currentPath === url.pathname || currentPath.indexOf(url.pathname + '/') === 0);
            if (isCurrent) {
                link.setAttribute('aria-current', 'page');
            }
        });
    }

    /** Nút in (data-print): thay cho onclick inline, dùng chung cho mọi trang có nút in. */
    function initPrintButtons() {
        document.querySelectorAll('[data-print]').forEach(function (button) {
            button.addEventListener('click', function () {
                window.print();
            });
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (window.AOS) {
            AOS.init({ duration: 600, once: true, offset: 40 });
        }
        initSwiper();
        initFlashToasts();
        initConfirmations();
        initQuantitySteppers();
        initPasswordToggles();
        initManualOrderLines();
        initBulkPriceTable();
        initGhnAddressPicker();
        initGhnFeePreview();
        initAutoSubmitForms();
        initNavCurrent();
        initPrintButtons();
    });
})();
