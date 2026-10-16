package com.example.demo.service;

import com.example.demo.dto.admin.VoucherForm;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.VoucherRepository;
import com.example.demo.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ voucher giảm giá cho TỔNG hoá đơn.
 *
 * Quy tắc (theo AGENTS.md mục 4.7): kiểm tra mã tồn tại, đang ACTIVE, trong thời gian áp dụng,
 * còn lượt và đơn đạt giá trị tối thiểu; số tiền giảm tính ở server và không vượt quá tạm tính;
 * khi dùng thật thì tăng used_quantity bằng UPDATE atomic để hai khách không dùng quá số lượt.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;

    /** Chuẩn hoá mã khách nhập: bỏ khoảng trắng, viết hoa; trả null nếu rỗng/quá dài. */
    public String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > 50) {
            return null;
        }
        return code;
    }

    /**
     * Lấy voucher và kiểm tra dùng được cho đơn có tạm tính {@code subtotal}.
     * Sai bất kỳ điều kiện nào ⇒ BusinessException với thông báo tiếng Việt cụ thể.
     */
    @Transactional(readOnly = true)
    public Voucher requireUsable(String rawCode, BigDecimal subtotal) {
        String code = normalizeCode(rawCode);
        if (code == null) {
            throw new BusinessException("Vui lòng nhập mã giảm giá.");
        }

        Voucher voucher = voucherRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(
                        "Mã giảm giá \"" + code + "\" không tồn tại. Bạn kiểm tra lại giúp mình nhé."));

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new BusinessException("Mã giảm giá \"" + code + "\" đã bị khoá.");
        }
        if (now.isBefore(voucher.getStartAt())) {
            throw new BusinessException("Mã giảm giá \"" + code + "\" chưa tới thời gian sử dụng.");
        }
        if (now.isAfter(voucher.getEndAt())) {
            throw new BusinessException("Mã giảm giá \"" + code + "\" đã hết hạn sử dụng.");
        }
        if (voucher.getUsedQuantity() >= voucher.getQuantity()) {
            throw new BusinessException("Mã giảm giá \"" + code + "\" đã hết lượt sử dụng.");
        }
        if (subtotal.compareTo(voucher.getMinOrderValue()) < 0) {
            throw new BusinessException("Mã \"" + code + "\" chỉ áp dụng cho đơn hàng từ "
                    + Money.format(voucher.getMinOrderValue()) + " trở lên.");
        }
        return voucher;
    }

    /** Số tiền được giảm: PERCENT cắt theo max_discount_amount, luôn ≤ tạm tính và ≥ 0. */
    public BigDecimal discountOf(Voucher voucher, BigDecimal subtotal) {
        BigDecimal discount = voucher.getDiscountType() == DiscountType.PERCENT
                ? subtotal.multiply(voucher.getDiscountValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                : voucher.getDiscountValue();
        if (voucher.getMaxDiscountAmount() != null) {
            discount = discount.min(voucher.getMaxDiscountAmount());
        }
        return discount.min(subtotal).max(BigDecimal.ZERO);
    }

    /** Mô tả ngắn để hiển thị: "Giảm 10% (tối đa 200.000 ₫)" / "Giảm 100.000 ₫". */
    public String describe(Voucher voucher) {
        return voucher.describe();
    }

    /** Dùng 1 lượt theo kiểu atomic – 0 dòng cập nhật nghĩa là vừa hết lượt ⇒ rollback cả đơn. */
    public void consume(Voucher voucher) {
        int updated = voucherRepository.increaseUsedQuantity(voucher.getId(), LocalDateTime.now());
        if (updated == 0) {
            throw new BusinessException("Mã giảm giá \"" + voucher.getCode()
                    + "\" vừa hết lượt sử dụng. Bạn bỏ mã giúp mình rồi đặt lại nhé.");
        }
    }

    /** Trả lại 1 lượt khi huỷ đơn / hoàn trả (gọi trong transaction của OrderService). */
    public void release(Integer voucherId) {
        if (voucherId != null) {
            voucherRepository.decreaseUsedQuantity(voucherId);
        }
    }

    // ==================== QUẢN TRỊ VOUCHER (ADMIN) ====================

    /** Số voucher mỗi trang ở khu quản trị. */
    public static final int PAGE_SIZE = 10;

    /** Danh sách voucher cho admin: lọc theo trạng thái + tìm theo mã/tên. */
    @Transactional(readOnly = true)
    public Page<Voucher> search(String keyword, VoucherStatus status, int pageNumber) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        int pageIndex = Math.max(0, pageNumber - 1);
        return voucherRepository.search(status, normalizedKeyword, PageRequest.of(pageIndex, PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public Voucher requireVoucher(Integer id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy voucher cần xử lý."));
    }

    /** Tạo voucher mới: mã được chuẩn hoá viết hoa/bỏ khoảng trắng và không được trùng. */
    @Transactional
    public Voucher create(VoucherForm form) {
        String code = requireValidCode(form.getCode());
        if (voucherRepository.findByCode(code).isPresent()) {
            throw new BusinessException("Mã giảm giá \"" + code + "\" đã tồn tại, bạn đặt mã khác nhé.");
        }

        Voucher voucher = new Voucher();
        voucher.setCode(code);
        voucher.setUsedQuantity(0);
        applyForm(voucher, form, 0);
        return voucherRepository.save(voucher);
    }

    /** Cập nhật voucher: giữ nguyên lượt đã dùng, không cho giảm số lượt phát hành xuống dưới mức đã dùng. */
    @Transactional
    public Voucher update(Integer id, VoucherForm form) {
        Voucher voucher = requireVoucher(id);
        String code = requireValidCode(form.getCode());
        if (!code.equals(voucher.getCode())) {
            if (voucherRepository.findByCode(code).isPresent()) {
                throw new BusinessException("Mã giảm giá \"" + code + "\" đã bị dùng bởi voucher khác.");
            }
            voucher.setCode(code);
        }
        applyForm(voucher, form, voucher.getUsedQuantity());
        return voucherRepository.save(voucher);
    }

    /** Bật/ngừng áp dụng voucher (không xoá để giữ liên kết với các đơn đã dùng mã). */
    @Transactional
    public Voucher toggleStatus(Integer id) {
        Voucher voucher = requireVoucher(id);
        voucher.setStatus(voucher.getStatus() == VoucherStatus.ACTIVE
                ? VoucherStatus.INACTIVE : VoucherStatus.ACTIVE);
        return voucherRepository.save(voucher);
    }

    /** Mã voucher: bắt buộc 1–50 ký tự sau khi chuẩn hoá. */
    private String requireValidCode(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code == null) {
            throw new BusinessException("Mã giảm giá phải có từ 1 đến 50 ký tự.");
        }
        return code;
    }

    /**
     * Kiểm tra các luật nghiệp vụ dùng chung cho tạo/sửa rồi gán vào entity.
     *
     * @param usedQuantity số lượt đã dùng thực tế (0 khi tạo mới) – không cho đặt số lượt phát hành nhỏ hơn.
     */
    private void applyForm(Voucher voucher, VoucherForm form, int usedQuantity) {
        if (form.getDiscountType() == null) {
            throw new BusinessException("Vui lòng chọn kiểu giảm giá.");
        }
        BigDecimal value = form.getDiscountValue();
        if (value == null || value.signum() <= 0) {
            throw new BusinessException("Giá trị giảm phải lớn hơn 0.");
        }
        if (form.getDiscountType() == DiscountType.PERCENT && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("Giảm theo phần trăm chỉ nhận giá trị từ 0 đến 100.");
        }
        BigDecimal minOrderValue = form.getMinOrderValue() == null ? BigDecimal.ZERO : form.getMinOrderValue();
        if (minOrderValue.signum() < 0) {
            throw new BusinessException("Giá trị đơn hàng tối thiểu không được âm.");
        }
        BigDecimal maxDiscount = form.getMaxDiscountAmount();
        boolean unlimitedMaxDiscount = (maxDiscount == null || maxDiscount.signum() == 0);
        if (!unlimitedMaxDiscount && maxDiscount.signum() < 0) {
            throw new BusinessException("Mức giảm tối đa không được âm.");
        }
        if (form.getQuantity() < 1) {
            throw new BusinessException("Số lượt phát hành phải từ 1 trở lên.");
        }
        if (form.getQuantity() < usedQuantity) {
            throw new BusinessException("Số lượt phát hành không được nhỏ hơn số lượt đã dùng (" + usedQuantity + ").");
        }
        if (form.getStartAt() == null || form.getEndAt() == null) {
            throw new BusinessException("Vui lòng chọn thời gian bắt đầu và kết thúc của voucher.");
        }
        if (!form.getEndAt().isAfter(form.getStartAt())) {
            throw new BusinessException("Thời gian kết thúc phải sau thời gian bắt đầu.");
        }

        voucher.setName(form.getName() == null ? "" : form.getName().trim());
        voucher.setDescription(form.getDescription() == null || form.getDescription().isBlank()
                ? null : form.getDescription().trim());
        voucher.setDiscountType(form.getDiscountType());
        voucher.setDiscountValue(value);
        voucher.setMinOrderValue(minOrderValue);
        voucher.setMaxDiscountAmount(unlimitedMaxDiscount ? null : maxDiscount);
        voucher.setQuantity(form.getQuantity());
        voucher.setStartAt(form.getStartAt());
        voucher.setEndAt(form.getEndAt());
        voucher.setStatus(form.getStatus() == null ? VoucherStatus.ACTIVE : form.getStatus());
    }
}
