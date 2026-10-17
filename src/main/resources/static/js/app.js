/* BadStore – hành vi UX dùng chung. Nạp trong layout/base.html SAU SweetAlert2, dùng th:src="@{/js/app.js}" defer */
(function () {
  'use strict';

  // 1) Xác nhận trước khi submit:  <form data-confirm="Xoá sản phẩm này?"> ...
  //    Cờ dùng chung với main.js: cả hai script đều bỏ qua khi form đã được xác nhận
  //    (giá trị 'true') nên hộp thoại chỉ hiện MỘT lần cho mỗi lần gửi.
  document.addEventListener('submit', function (e) {
    var f = e.target, msg = f.getAttribute && f.getAttribute('data-confirm');
    if (!msg || f.dataset.confirmed === 'true') return;
    e.preventDefault();
    e.stopImmediatePropagation();
    Swal.fire({
      title: msg, icon: 'question', showCancelButton: true,
      confirmButtonText: 'Đồng ý', cancelButtonText: 'Huỷ', confirmButtonColor: '#0f8a5f', reverseButtons: true
    }).then(function (r) {
      if (r.isConfirmed) { f.dataset.confirmed = 'true'; f.requestSubmit ? f.requestSubmit() : f.submit(); }
    });
  }, true);

  // 2) Nút loading khi submit (tránh bấm đúp). Bỏ qua bằng data-no-loading
  //    Nút có thuộc tính name không bị disable để không mất dữ liệu gửi lên.
  document.addEventListener('submit', function (e) {
    if (e.defaultPrevented) return;
    var b = e.target.querySelector('[type=submit]');
    if (!b || b.hasAttribute('data-no-loading')) return;
    setTimeout(function () {
      b.dataset.old = b.innerHTML;
      if (!b.name) { b.disabled = true; }
      b.innerHTML = '<span class="spinner-border spinner-border-sm me-2" aria-hidden="true"></span>Đang xử lý...';
    }, 0);
  });
  window.addEventListener('pageshow', function (ev) {   // quay lại bằng nút Back
    if (!ev.persisted) return;
    document.querySelectorAll('[type=submit][data-old]').forEach(function (b) { b.disabled = false; b.innerHTML = b.dataset.old; });
    document.querySelectorAll('form[data-confirmed]').forEach(function (f) { delete f.dataset.confirmed; });
  });

  // 3) Bộ chọn số lượng:
  //   <div data-qty class="input-group"><button type="button" data-qty-minus>−</button>
  //   <input type="number" min="1" max="${stock}" value="1"><button type="button" data-qty-plus>+</button></div>
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-qty-minus],[data-qty-plus]'); if (!btn) return;
    var inp = btn.closest('[data-qty]').querySelector('input');
    var min = parseInt(inp.min || '1', 10), max = inp.max ? parseInt(inp.max, 10) : Infinity;
    var v = (parseInt(inp.value, 10) || min) + (btn.hasAttribute('data-qty-plus') ? 1 : -1);
    inp.value = Math.max(min, Math.min(max, v));
    inp.dispatchEvent(new Event('change', { bubbles: true }));
    if (v > max) window.toast('Chỉ còn ' + max + ' sản phẩm trong kho', 'warning');
  });

  // 4) Toast dùng chung:  toast('Đã thêm vào giỏ', 'success')
  window.toast = function (title, icon) {
    Swal.fire({ toast: true, position: 'top-end', icon: icon || 'success', title: title, showConfirmButton: false, timer: 2200, timerProgressBar: true });
  };

  // 5) Nút lên đầu trang:  <button id="backToTop" class="btn btn-primary rounded-circle d-none" aria-label="Lên đầu trang">
  var up = document.getElementById('backToTop');
  if (up) {
    window.addEventListener('scroll', function () { up.classList.toggle('d-none', window.scrollY < 400); }, { passive: true });
    up.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: 'smooth' }); });
  }

  // 6) AOS
  if (window.AOS) {
    AOS.init({ once: true, duration: 600 });
  } else {
    // AOS không tải được (CDN lỗi/mất mạng): gỡ trạng thái ẩn của [data-aos] để trang không trắng
    document.documentElement.classList.add('no-aos');
  }
})();
