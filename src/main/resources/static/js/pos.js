/* BadStore – màn hình BÁN HÀNG TẠI QUẦY (POS)
   Nguyên tắc: trình duyệt chỉ giữ danh sách SKU + số lượng, còn GIÁ – GIẢM GIÁ – TỒN KHO
   luôn do server trả về qua /admin/ban-hang/tinh-tien (không tin số tiền từ client). */
(function () {
    'use strict';

    var form = document.getElementById('posForm');
    if (!form) {
        return;
    }

    var URL_SEARCH = '/admin/ban-hang/tim';
    var URL_CUSTOMER = '/admin/ban-hang/khach';
    var URL_PRICE = '/admin/ban-hang/tinh-tien';
    var CONFIRM_TEXT = 'Thu tiền và trừ kho cho hoá đơn này?';

    var refs = {
        search: document.getElementById('posSearch'),
        searchButton: document.getElementById('posSearchButton'),
        results: document.getElementById('posResults'),
        noResult: document.getElementById('posNoResult'),
        lines: document.getElementById('posLines'),
        empty: document.getElementById('posEmpty'),
        totals: document.getElementById('posTotals'),
        subtotal: document.querySelector('[data-pos-subtotal]'),
        discount: document.querySelector('[data-pos-discount]'),
        total: document.querySelector('[data-pos-total]'),
        clear: document.getElementById('posClear'),
        voucher: document.getElementById('posVoucherCode'),
        voucherOk: document.getElementById('posVoucherOk'),
        voucherError: document.getElementById('posVoucherError'),
        customerPhone: document.getElementById('posCustomerPhone'),
        lookupCustomer: document.getElementById('posLookupCustomer'),
        customerFound: document.getElementById('posCustomerFound'),
        customerEmail: document.getElementById('customerEmail'),
        receiverName: document.getElementById('posReceiverName'),
        receiverPhone: document.getElementById('posReceiverPhone'),
        cashPanel: document.querySelector('[data-pos-cash-panel]'),
        transferPanel: document.querySelector('[data-pos-transfer-panel]'),
        cashReceived: document.getElementById('posCashReceived'),
        orderCode: document.getElementById('posOrderCode'),
        change: document.querySelector('[data-pos-change] strong')
    };

    var state = {
        lines: [],       // { sku, quantity, productName, variantLabel, unitPrice, lineTotal, stock, error }
        subtotal: 0,
        discount: 0,
        total: 0
    };
    var priceTimer = null;

    // ------------------------------------------------------------------ tiện ích

    function meta(name) {
        var element = document.querySelector('meta[name="' + name + '"]');
        return element ? element.getAttribute('content') : '';
    }

    var csrfToken = meta('_csrf');
    var csrfHeader = meta('_csrf_header');

    function money(value) {
        return new Intl.NumberFormat('vi-VN').format(Number(value || 0)) + ' ₫';
    }

    function escapeHtml(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function el(tag, className, text) {
        var element = document.createElement(tag);
        if (className) {
            element.className = className;
        }
        if (text != null) {
            element.textContent = text;
        }
        return element;
    }

    /** Dự phòng khi SweetAlert2 không tải được: hiện danh sách lỗi bằng hộp Bootstrap
        ngay trên form (không dùng window.alert để trải nghiệm thống nhất toàn hệ thống). */
    function showClientProblems(problems) {
        var box = document.getElementById('posClientProblems');
        if (!box) {
            box = document.createElement('div');
            box.id = 'posClientProblems';
            box.className = 'alert alert-danger d-flex gap-2';
            box.setAttribute('role', 'alert');
            form.parentNode.insertBefore(box, form);
        }
        box.innerHTML = '<i class="bi bi-exclamation-octagon-fill" aria-hidden="true"></i>'
            + '<div><strong>Chưa thể thu tiền:</strong>'
            + '<ul class="mb-0 small">' + problems.map(function (item) {
                return '<li>' + escapeHtml(item) + '</li>';
            }).join('') + '</ul></div>';
        box.scrollIntoView({ block: 'center' });
    }

    function request(url, options) {
        return fetch(url, options).then(function (response) {
            if (!response.ok) {
                throw new Error('HTTP ' + response.status);
            }
            return response.json();
        });
    }

    function postJson(url, body) {
        var headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }
        return request(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
    }

    function isCash() {
        var checked = form.querySelector('input[name="paymentMethod"]:checked');
        return !checked || checked.value === 'CASH';
    }

    function cashReceived() {
        var value = refs.cashReceived ? parseInt(refs.cashReceived.value, 10) : NaN;
        return isNaN(value) ? null : value;
    }

    // ------------------------------------------------------------------ tìm & thêm sản phẩm

    function search() {
        var keyword = refs.search ? refs.search.value.trim() : '';
        request(URL_SEARCH + '?keyword=' + encodeURIComponent(keyword))
            .then(function (products) {
                renderResults(products);
                // Quét mã vạch: chỉ có đúng 1 kết quả ⇒ thêm thẳng vào hoá đơn.
                if (products.length === 1 && keyword.length > 0) {
                    addProduct(products[0]);
                    if (refs.search) {
                        refs.search.value = '';
                    }
                    renderResults([]);
                } else if (products.length === 0 && refs.noResult) {
                    refs.noResult.hidden = false;
                }
            })
            .catch(function () {
                renderResults([]);
                if (refs.noResult) {
                    refs.noResult.hidden = false;
                }
            });
    }

    function renderResults(products) {
        if (!refs.results) {
            return;
        }
        refs.results.innerHTML = '';
        if (refs.noResult) {
            refs.noResult.hidden = products.length > 0;
        }
        products.forEach(function (product) {
            var card = el('button', 'pos-result' + (product.stock > 0 ? '' : ' pos-result-out'));
            card.type = 'button';
            card.setAttribute('data-sku', product.sku);

            if (product.imageUrl) {
                var image = el('img', 'pos-result-thumb');
                image.src = product.imageUrl;
                image.alt = 'Ảnh ' + product.productName;
                image.loading = 'lazy';
                card.appendChild(image);
            }
            var body = el('div', 'pos-result-body');
            body.appendChild(el('div', 'pos-result-name', product.productName));
            body.appendChild(el('div', 'pos-result-meta small text-muted', product.variantLabel));
            body.appendChild(el('div', 'pos-result-meta small text-muted', 'SKU ' + product.sku));
            card.appendChild(body);

            var side = el('div', 'pos-result-side');
            side.appendChild(el('div', 'fw-semibold', money(product.price)));
            side.appendChild(el('div', 'small ' + (product.stock > 0 ? 'text-success' : 'text-danger'),
                'Tồn ' + product.stock));
            card.appendChild(side);

            card.addEventListener('click', function () {
                addProduct(product);
            });
            refs.results.appendChild(card);
        });
    }

    function addProduct(product) {
        if (product.stock <= 0) {
            notify('error', product.productName + ' đã hết hàng trong kho.');
            return;
        }
        var existing = state.lines.filter(function (line) {
            return line.sku === product.sku;
        })[0];
        if (existing) {
            existing.quantity = existing.quantity + 1;
        } else {
            state.lines.push({
                sku: product.sku,
                quantity: 1,
                productName: product.productName,
                variantLabel: product.variantLabel,
                unitPrice: product.price,
                lineTotal: product.price,
                stock: product.stock,
                error: null
            });
        }
        renderLines();
        refreshPrice();
    }

    // ------------------------------------------------------------------ hoá đơn

    function renderLines() {
        if (!refs.lines) {
            return;
        }
        refs.lines.innerHTML = '';
        var hasLines = state.lines.length > 0;
        if (refs.empty) {
            refs.empty.hidden = hasLines;
        }
        if (refs.totals) {
            refs.totals.hidden = !hasLines;
        }

        state.lines.forEach(function (line, index) {
            var row = el('div', 'pos-line' + (line.error ? ' pos-line-error' : ''));

            var main = el('div', 'pos-line-main');
            main.appendChild(el('div', 'pos-line-name fw-semibold', line.productName || line.sku));
            main.appendChild(el('div', 'small text-muted', (line.variantLabel || '') + ' · SKU ' + line.sku));
            if (line.error) {
                main.appendChild(el('div', 'small text-danger', line.error));
            } else if (line.stock != null) {
                main.appendChild(el('div', 'small text-muted', 'Tồn kho: ' + line.stock));
            }
            row.appendChild(main);

            var quantityBox = el('div', 'pos-line-qty');
            var minus = el('button', 'btn btn-sm btn-outline-secondary');
            minus.type = 'button';
            minus.setAttribute('aria-label', 'Giảm số lượng ' + line.sku);
            minus.appendChild(el('i', 'bi bi-dash'));
            var quantity = el('input', 'form-control form-control-sm text-center');
            quantity.type = 'number';
            quantity.min = '1';
            quantity.value = String(line.quantity);
            quantity.setAttribute('aria-label', 'Số lượng ' + line.sku);
            var plus = el('button', 'btn btn-sm btn-outline-secondary');
            plus.type = 'button';
            plus.setAttribute('aria-label', 'Tăng số lượng ' + line.sku);
            plus.appendChild(el('i', 'bi bi-plus'));

            minus.addEventListener('click', function () {
                changeQuantity(index, line.quantity - 1);
            });
            plus.addEventListener('click', function () {
                changeQuantity(index, line.quantity + 1);
            });
            quantity.addEventListener('input', function () {
                var value = parseInt(quantity.value, 10);
                if (isNaN(value) || value < 1) {
                    return;
                }
                state.lines[index].quantity = value;
                if (state.lines[index].unitPrice != null) {
                    state.lines[index].lineTotal = Number(state.lines[index].unitPrice) * value;
                    renderTotals();
                }
            });
            quantity.addEventListener('change', function () {
                var value = parseInt(quantity.value, 10);
                changeQuantity(index, isNaN(value) ? 1 : value);
            });
            quantityBox.appendChild(minus);
            quantityBox.appendChild(quantity);
            quantityBox.appendChild(plus);
            row.appendChild(quantityBox);

            row.appendChild(el('div', 'pos-line-total fw-semibold text-end',
                line.lineTotal == null ? '—' : money(line.lineTotal)));

            var remove = el('button', 'btn btn-sm btn-link text-danger');
            remove.type = 'button';
            remove.setAttribute('aria-label', 'Xoá dòng ' + line.sku);
            remove.appendChild(el('i', 'bi bi-x-lg'));
            remove.addEventListener('click', function () {
                state.lines.splice(index, 1);
                renderLines();
                refreshPrice();
            });
            row.appendChild(remove);

            // Hai ô ẩn này là dữ liệu thật gửi lên server (lines[i].sku / lines[i].quantity)
            var skuInput = document.createElement('input');
            skuInput.type = 'hidden';
            skuInput.name = 'lines[' + index + '].sku';
            skuInput.value = line.sku;
            skuInput.setAttribute('data-pos-hidden', 'sku');
            skuInput.value = line.sku;
            row.appendChild(skuInput);

            var hiddenQuantity = document.createElement('input');
            hiddenQuantity.type = 'hidden';
            hiddenQuantity.name = 'lines[' + index + '].quantity';
            hiddenQuantity.value = String(line.quantity);
            hiddenQuantity.setAttribute('data-pos-hidden', 'quantity');
            row.appendChild(hiddenQuantity);

            refs.lines.appendChild(row);
        });

        renderTotals();
    }

    function changeQuantity(index, value) {
        if (isNaN(value) || value < 1) {
            value = 1;
        }
        state.lines[index].quantity = value;
        if (state.lines[index].unitPrice != null) {
            state.lines[index].lineTotal = Number(state.lines[index].unitPrice) * value;
        }
        renderLines();
        refreshPrice();
    }

    function renderTotals() {
        if (refs.subtotal) {
            refs.subtotal.textContent = money(state.subtotal);
        }
        if (refs.discount) {
            refs.discount.textContent = money(state.discount);
        }
        if (refs.total) {
            refs.total.textContent = money(state.total);
        }
        updateChange();
    }

    /** Gọi server tính lại tiền + tra giá/tồn kho cho từng dòng (có chờ 200ms cho đỡ nặng). */
    function refreshPrice() {
        if (priceTimer) {
            clearTimeout(priceTimer);
        }
        if (!state.lines.length) {
            state.subtotal = 0;
            state.discount = 0;
            state.total = 0;
            renderTotals();
            renderVoucher(null, null);
            refreshQr();
            return;
        }
        priceTimer = setTimeout(function () {
            postJson(URL_PRICE, {
                voucherCode: refs.voucher ? refs.voucher.value.trim() : '',
                lines: state.lines.map(function (line) {
                    return { sku: line.sku, quantity: line.quantity };
                })
            }).then(function (result) {
                applyPrice(result);
            }).catch(function () {
                notify('error', 'Không kết nối được máy chủ để tính tiền. Vui lòng thử lại.');
            });
        }, 200);
    }

    function applyPrice(result) {
        (result.lines || []).forEach(function (view) {
            state.lines.forEach(function (line) {
                if (line.sku !== view.sku) {
                    return;
                }
                line.quantity = view.quantity;
                line.productName = view.productName || line.productName;
                line.variantLabel = view.variantLabel || line.variantLabel;
                line.unitPrice = view.unitPrice;
                line.lineTotal = view.lineTotal;
                line.stock = view.stock;
                line.error = view.error;
            });
        });
        state.subtotal = Number(result.subtotal || 0);
        state.discount = Number(result.discount || 0);
        state.total = Number(result.total || 0);
        renderLines();
        renderVoucher(result.voucherDescription, result.voucherError);
        refreshQr();
    }

    function renderVoucher(description, error) {
        if (refs.voucherOk) {
            refs.voucherOk.hidden = !description;
            refs.voucherOk.textContent = description ? 'Đang áp dụng: ' + description : '';
        }
        if (refs.voucherError) {
            refs.voucherError.hidden = !error;
            refs.voucherError.textContent = error ? error : '';
        }
    }

    // ------------------------------------------------------------------ tiền thừa & thanh toán

    function updateChange() {
        if (!refs.change) {
            return;
        }
        if (!isCash()) {
            refs.change.textContent = '—';
            return;
        }
        var received = cashReceived();
        if (received == null || received <= 0) {
            refs.change.textContent = '0 ₫';
            return;
        }
        var change = received - state.total;
        refs.change.textContent = change >= 0
            ? money(change)
            : 'còn thiếu ' + money(-change);
    }

    function syncPaymentPanels() {
        var cash = isCash();
        if (refs.cashPanel) {
            refs.cashPanel.hidden = !cash;
        }
        if (refs.transferPanel) {
            refs.transferPanel.hidden = cash;
        }
        updateChange();
        refreshQr();
    }

    // ------------------------------------------------------------------ mã QR chuyển khoản (VietQR)
    // Server dựng QR từ giá DB + voucher và cấp sẵn mã đơn (nội dung chuyển khoản); khi bán, server
    // kiểm tra lại mã đơn nên trình duyệt không thể ép số tiền hay mã đơn tuỳ ý.

    var URL_QR = '/admin/ban-hang/ma-qr';
    var qrTimer = null;
    var qrSeq = 0;
    var qrState = { ready: false, amount: null, qr: null };

    function isTransfer() {
        var checked = form.querySelector('input[name="paymentMethod"]:checked');
        return !!checked && checked.value === 'BANK_TRANSFER';
    }

    function qrRef(name) {
        return document.querySelector('[data-pos-qr-' + name + ']');
    }

    function showQrMessage(text) {
        qrState = { ready: false, amount: null, qr: null };
        var message = qrRef('message');
        var content = qrRef('content');
        if (message) {
            message.hidden = false;
            message.textContent = text;
        }
        if (content) {
            content.hidden = true;
        }
    }

    function applyQr(result) {
        if (!result || !result.available || !result.qr) {
            showQrMessage((result && result.message) || 'Chưa tạo được mã QR.');
            return;
        }
        var qr = result.qr;
        qrState = { ready: true, amount: Number(result.amount), qr: qr };
        if (refs.orderCode) {
            refs.orderCode.value = result.orderCode || '';
        }
        var img = qrRef('img');
        if (img) {
            img.dataset.fallback = '';
            img.src = qr.qrImageUrl;
        }
        var amount = qrRef('amount');
        if (amount) {
            amount.textContent = money(Number(result.amount));
        }
        var code = qrRef('code');
        if (code) {
            code.textContent = qr.transferContent;
        }
        var bank = qrRef('bank');
        if (bank) {
            bank.textContent = qr.bankName + ' · ' + qr.accountNumber + ' · ' + qr.accountName;
        }
        var message = qrRef('message');
        var content = qrRef('content');
        if (message) {
            message.hidden = true;
        }
        if (content) {
            content.hidden = false;
        }
    }

    /** Dựng lại QR theo hoá đơn hiện tại (chờ 250ms; bỏ qua kết quả cũ nếu đã có lần gọi mới hơn). */
    function refreshQr() {
        if (qrTimer) {
            clearTimeout(qrTimer);
        }
        if (!isTransfer()) {
            return;
        }
        if (!state.lines.length) {
            showQrMessage('Hãy thêm sản phẩm để tạo mã QR chuyển khoản.');
            return;
        }
        var seq = ++qrSeq;
        qrTimer = setTimeout(function () {
            postJson(URL_QR, {
                voucherCode: refs.voucher ? refs.voucher.value.trim() : '',
                orderCode: refs.orderCode ? refs.orderCode.value : '',
                lines: state.lines.map(function (line) {
                    return { sku: line.sku, quantity: line.quantity };
                })
            }).then(function (result) {
                if (seq === qrSeq) {
                    applyQr(result);
                }
            }).catch(function () {
                if (seq === qrSeq) {
                    showQrMessage('Không kết nối được máy chủ để tạo mã QR.');
                }
            });
        }, 250);
    }

    /** Phóng to QR để khách quét bằng điện thoại. */
    function zoomQr() {
        if (!qrState.ready || !window.Swal) {
            return;
        }
        Swal.fire({
            title: 'Quét mã để chuyển khoản',
            html: '<div class="fw-bold text-danger fs-4">' + escapeHtml(money(qrState.amount)) + '</div>'
                + '<div class="small text-muted">Nội dung: ' + escapeHtml(qrState.qr.transferContent) + '</div>',
            imageUrl: qrState.qr.qrImageUrl,
            imageWidth: 340,
            imageAlt: 'Mã QR chuyển khoản',
            confirmButtonText: 'Đóng'
        });
    }

    var zoomButton = qrRef('zoom');
    if (zoomButton) {
        zoomButton.addEventListener('click', zoomQr);
    }

    // ------------------------------------------------------------------ khách hàng

    function lookupCustomer() {
        var phone = refs.customerPhone ? refs.customerPhone.value.trim() : '';
        if (!phone) {
            notify('info', 'Nhập số điện thoại khách rồi bấm “Tra khách”.');
            return;
        }
        request(URL_CUSTOMER + '?phone=' + encodeURIComponent(phone))
            .then(function (customer) {
                if (!refs.customerFound) {
                    return;
                }
                refs.customerFound.hidden = false;
                if (customer.found) {
                    refs.customerFound.className = 'alert alert-success py-2 small mb-2';
                    refs.customerFound.textContent = 'Khách quen: ' + customer.fullName
                        + ' (' + customer.email + ') – hoá đơn sẽ gắn vào tài khoản này.';
                    if (refs.customerEmail) {
                        refs.customerEmail.value = customer.email || '';
                    }
                    if (refs.receiverName && (!refs.receiverName.value || refs.receiverName.value === 'Khách lẻ tại quầy')) {
                        refs.receiverName.value = customer.fullName || '';
                    }
                    if (refs.receiverPhone && !refs.receiverPhone.value) {
                        refs.receiverPhone.value = customer.phone || phone;
                    }
                } else {
                    refs.customerFound.className = 'alert alert-info py-2 small mb-2';
                    refs.customerFound.textContent = 'Chưa có tài khoản với SĐT này – hoá đơn sẽ ghi là khách lẻ.';
                    if (refs.customerEmail) {
                        refs.customerEmail.value = '';
                    }
                }
            })
            .catch(function () {
                notify('error', 'Không tra được khách hàng. Vui lòng thử lại.');
            });
    }

    function notify(icon, title) {
        if (window.Swal) {
            Swal.fire({ toast: true, position: 'top-end', icon: icon, title: title, showConfirmButton: false, timer: 3500 });
        }
    }

    // ------------------------------------------------------------------ gắn sự kiện

    if (refs.searchButton) {
        refs.searchButton.addEventListener('click', search);
    }
    if (refs.search) {
        refs.search.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                search();
            }
        });
        refs.search.addEventListener('input', function () {
            if (!refs.search.value.trim()) {
                restoreDefaultList();
            }
        });
    }
    if (refs.clear) {
        refs.clear.addEventListener('click', function () {
            state.lines = [];
            renderLines();
            refreshPrice();
        });
    }
    if (refs.voucher) {
        refs.voucher.addEventListener('change', refreshPrice);
        refs.voucher.addEventListener('blur', refreshPrice);
    }
    if (refs.cashReceived) {
        refs.cashReceived.addEventListener('input', updateChange);
    }
    form.querySelectorAll('input[name="paymentMethod"]').forEach(function (radio) {
        radio.addEventListener('change', syncPaymentPanels);
    });
    document.querySelectorAll('[data-pos-cash]').forEach(function (button) {
        button.addEventListener('click', function () {
            if (!refs.cashReceived) {
                return;
            }
            var value = button.getAttribute('data-pos-cash');
            refs.cashReceived.value = value === 'exact' ? String(state.total) : value;
            updateChange();
        });
    });
    if (refs.lookupCustomer) {
        refs.lookupCustomer.addEventListener('click', lookupCustomer);
    }
    if (refs.customerPhone) {
        refs.customerPhone.addEventListener('keydown', function (event) {
            if (event.key === 'Enter') {
                event.preventDefault();
                lookupCustomer();
            }
        });
    }

    /** Chặn submit khi hoá đơn còn lỗi, rồi mới hỏi xác nhận (SweetAlert2) và gửi lên server. */
    form.addEventListener('submit', function (event) {
        if (form.dataset.confirmed === 'true') {
            return;
        }
        event.preventDefault();

        var problems = [];
        if (!state.lines.length) {
            problems.push('Hoá đơn chưa có sản phẩm nào.');
        }
        state.lines.forEach(function (line) {
            if (line.error) {
                problems.push('SKU ' + line.sku + ': ' + line.error);
            }
        });
        if (isCash()) {
            var received = cashReceived();
            if (received != null && received > 0 && received < state.total) {
                problems.push('Tiền khách đưa (' + money(received) + ') chưa đủ ' + money(state.total) + '.');
            }
        }
        if (isTransfer()) {
            var confirmBox = document.getElementById('posTransferConfirmed');
            if (qrState.ready && qrState.amount !== state.total) {
                problems.push('Mã QR chưa cập nhật theo hoá đơn mới – vui lòng chờ mã QR làm mới rồi cho khách quét lại.');
            }
            if (!confirmBox || !confirmBox.checked) {
                problems.push('Hãy chờ khách chuyển khoản xong và tích "Đã nhận được tiền chuyển khoản" trước khi thu tiền.');
            }
        }
        if (problems.length) {
            var html = '<ul class="text-start small mb-0">' + problems.map(function (item) {
                return '<li>' + escapeHtml(item) + '</li>';
            }).join('') + '</ul>';
            if (window.Swal) {
                Swal.fire({ icon: 'error', title: 'Chưa thể thu tiền', html: html, confirmButtonText: 'Đã hiểu' });
            } else {
                showClientProblems(problems);
            }
            return;
        }

        // Hết lỗi: gỡ hộp cảnh báo dự phòng (chỉ xuất hiện khi SweetAlert2 không tải được)
        var staleProblems = document.getElementById('posClientProblems');
        if (staleProblems) {
            staleProblems.remove();
        }

        var detail = '<div>Tổng thu: <strong>' + money(state.total) + '</strong></div>';
        var received2 = cashReceived();
        if (isCash() && received2 != null && received2 > 0) {
            detail += '<div class="small">Khách đưa ' + money(received2)
                + ' – thừa <strong>' + money(received2 - state.total) + '</strong></div>';
        }
        if (!window.Swal) {
            form.dataset.confirmed = 'true';
            form.submit();
            return;
        }
        Swal.fire({
            icon: 'question',
            title: 'Xác nhận bán hàng',
            html: detail + '<div class="small text-muted mt-2">' + CONFIRM_TEXT + '</div>',
            showCancelButton: true,
            confirmButtonText: 'Thu tiền & trừ kho',
            cancelButtonText: 'Xem lại',
            confirmButtonColor: '#0f8a5f', // xanh brand (đồng bộ --bs-primary trong theme.css)
            reverseButtons: true
        }).then(function (result) {
            if (result.isConfirmed) {
                form.dataset.confirmed = 'true';
                form.submit();
            }
        });
    });

    // ------------------------------------------------------------------ khởi động

    function restoreDefaultList() {
        request(URL_SEARCH + '?keyword=')
            .then(renderResults)
            .catch(function () {
                renderResults([]);
            });
    }

    /** Sau khi bán lỗi, trang được render lại kèm hoá đơn cũ ⇒ dựng lại đúng các dòng đó. */
    function loadInitialLines() {
        var holders = document.querySelectorAll('[data-pos-initial]');
        if (!holders.length) {
            restoreDefaultList();
            syncPaymentPanels();
            renderTotals();
            return;
        }
        holders.forEach(function (holder) {
            state.lines.push({
                sku: holder.getAttribute('data-sku'),
                quantity: parseInt(holder.getAttribute('data-quantity') || '1', 10),
                productName: holder.getAttribute('data-sku'),
                variantLabel: '',
                unitPrice: null,
                lineTotal: null,
                stock: null,
                error: null
            });
        });
        renderLines();
        refreshPrice();
        restoreDefaultList();
        syncPaymentPanels();
    }

    loadInitialLines();
})();
