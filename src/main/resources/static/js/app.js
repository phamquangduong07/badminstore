/*
 * BadStore – hành vi UX dùng chung (nạp bằng th:src="@{/js/app.js}" defer).
 *
 * Phân chia trách nhiệm với main.js (nạp TRƯỚC app.js trong cả 2 layout):
 *   - main.js : xác nhận trước khi ghi CSDL (data-confirm), toast flash, Swiper, AOS,
 *               bộ chọn số lượng .js-quantity (giỏ hàng, trang chi tiết), GHN, đơn thủ công…
 *   - app.js  : khoá nút khi submit, window.toast(), nút lên đầu trang, dự phòng khi AOS lỗi.
 * Hai script KHÔNG còn trùng việc xác nhận/AOS nên không sợ hộp thoại hiện 2 lần.
 */
(function () {
  'use strict';

  // 1) Nút loading khi submit (tránh bấm đúp). Bỏ qua bằng data-no-loading.
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

  // 2) Toast dùng chung:  toast('Đã thêm vào giỏ', 'success')
  window.toast = function (title, icon) {
    Swal.fire({ toast: true, position: 'top-end', icon: icon || 'success', title: title, showConfirmButton: false, timer: 2200, timerProgressBar: true });
  };

  // 3) Nút lên đầu trang:  <button id="backToTop" class="btn btn-primary rounded-circle d-none" aria-label="Lên đầu trang">
  var up = document.getElementById('backToTop');
  if (up) {
    window.addEventListener('scroll', function () { up.classList.toggle('d-none', window.scrollY < 400); }, { passive: true });
    up.addEventListener('click', function () { window.scrollTo({ top: 0, behavior: 'smooth' }); });
  }

  // 4) AOS lỗi/mất mạng: aos.css đặt opacity:0 cho [data-aos] nên trang sẽ trắng.
  //    Gắn .no-aos để theme.css hiện nội dung ngay. AOS.init do main.js gọi.
  if (!window.AOS) {
    document.documentElement.classList.add('no-aos');
  }
})();
