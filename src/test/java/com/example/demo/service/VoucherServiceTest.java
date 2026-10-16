package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.admin.VoucherForm;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử phần QUẢN TRỊ voucher: mã được chuẩn hoá + chống trùng, luật PERCENT ≤ 100,
 * end > start, không cho giảm số lượt phát hành dưới số đã dùng, và bật/ngừng áp dụng.
 */
@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    private static final Integer VOUCHER_ID = 5;

    @Mock
    private VoucherRepository voucherRepository;

    @InjectMocks
    private VoucherService voucherService;

    private LocalDateTime start;
    private LocalDateTime end;

    @BeforeEach
    void setUp() {
        start = LocalDateTime.of(2026, 10, 1, 0, 0);
        end = LocalDateTime.of(2026, 12, 31, 23, 59);
    }

    @Test
    @DisplayName("Tạo voucher: mã được viết hoa, bỏ khoảng trắng; lưu đúng các trường")
    void create_normalizesCodeAndSavesFields() {
        VoucherForm form = form("  giam10  ", DiscountType.PERCENT, "10");
        when(voucherRepository.findByCode("GIAM10")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Voucher created = voucherService.create(form);

        assertThat(created.getCode()).isEqualTo("GIAM10");
        assertThat(created.getUsedQuantity()).isZero();
        assertThat(created.getDiscountType()).isEqualTo(DiscountType.PERCENT);
        assertThat(created.getDiscountValue()).isEqualByComparingTo("10");
        assertThat(created.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
        assertThat(created.getStartAt()).isEqualTo(start);
    }

    @Test
    @DisplayName("Tạo voucher: mã đã tồn tại ⇒ báo lỗi, không ghi thêm")
    void create_rejectsDuplicateCode() {
        VoucherForm form = form("GIAM10", DiscountType.PERCENT, "10");
        when(voucherRepository.findByCode("GIAM10")).thenReturn(Optional.of(new Voucher()));

        assertThatThrownBy(() -> voucherService.create(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");

        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("Luật nghiệp vụ: phần trăm ≤ 100, thời gian kết thúc phải sau bắt đầu")
    void create_validatesBusinessRules() {
        when(voucherRepository.findByCode("GIAM200")).thenReturn(Optional.empty());
        VoucherForm tooMuch = form("GIAM200", DiscountType.PERCENT, "200");
        assertThatThrownBy(() -> voucherService.create(tooMuch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ nhận giá trị từ 0 đến 100");

        when(voucherRepository.findByCode("GIAM50K")).thenReturn(Optional.empty());
        VoucherForm badTime = form("GIAM50K", DiscountType.FIXED, "50000");
        badTime.setEndAt(start.minusDays(1));
        assertThatThrownBy(() -> voucherService.create(badTime))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Thời gian kết thúc phải sau thời gian bắt đầu");

        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa voucher: không cho đặt số lượt phát hành nhỏ hơn số lượt đã dùng")
    void update_rejectsQuantityBelowUsed() {
        Voucher existing = existingVoucher();
        existing.setQuantity(50);
        existing.setUsedQuantity(20);
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.of(existing));

        VoucherForm form = form("GIAM10", DiscountType.PERCENT, "10");
        form.setQuantity(10);

        assertThatThrownBy(() -> voucherService.update(VOUCHER_ID, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không được nhỏ hơn số lượt đã dùng (20)");

        verify(voucherRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa voucher: đổi mã sang mã của voucher khác bị chặn")
    void update_rejectsCodeTakenByAnotherVoucher() {
        Voucher existing = existingVoucher();
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.of(existing));
        when(voucherRepository.findByCode("GIAM20")).thenReturn(Optional.of(new Voucher()));

        VoucherForm form = form("GIAM20", DiscountType.PERCENT, "20");

        assertThatThrownBy(() -> voucherService.update(VOUCHER_ID, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã bị dùng bởi voucher khác");
    }

    @Test
    @DisplayName("Sửa voucher: giữ nguyên lượt đã dùng và cập nhật các trường khác")
    void update_keepsUsedQuantity() {
        Voucher existing = existingVoucher();
        existing.setUsedQuantity(3);
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.of(existing));
        when(voucherRepository.findByCode("GIAM15")).thenReturn(Optional.empty());
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VoucherForm form = form("GIAM15", DiscountType.FIXED, "150000");
        form.setMinOrderValue(new BigDecimal("2000000"));
        form.setMaxDiscountAmount(new BigDecimal("300000"));
        form.setQuantity(30);

        Voucher updated = voucherService.update(VOUCHER_ID, form);

        assertThat(updated.getCode()).isEqualTo("GIAM15");
        assertThat(updated.getUsedQuantity()).isEqualTo(3);
        assertThat(updated.getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(updated.getDiscountValue()).isEqualByComparingTo("150000");
        assertThat(updated.getQuantity()).isEqualTo(30);
        assertThat(updated.getMaxDiscountAmount()).isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("Bật/ngừng áp dụng voucher: đổi qua lại giữa ACTIVE và INACTIVE")
    void toggleStatus_switchesBothWays() {
        Voucher existing = existingVoucher();
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.of(existing));
        when(voucherRepository.save(any(Voucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(voucherService.toggleStatus(VOUCHER_ID).getStatus()).isEqualTo(VoucherStatus.INACTIVE);
        assertThat(voucherService.toggleStatus(VOUCHER_ID).getStatus()).isEqualTo(VoucherStatus.ACTIVE);
    }

    @Test
    @DisplayName("Voucher không tồn tại ⇒ 404")
    void requireVoucher_throwsWhenMissing() {
        when(voucherRepository.findById(VOUCHER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> voucherService.requireVoucher(VOUCHER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy voucher");
    }

    @Test
    @DisplayName("Nhãn trạng thái hiển thị ở admin: chưa tới ngày / hết hạn / hết lượt / đã khoá")
    void displayStatus_coversAllCases() {
        Voucher voucher = existingVoucher();
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);
        assertThat(voucher.displayStatusLabel(now)).isEqualTo("Đang áp dụng");

        voucher.setStartAt(now.plusDays(1));
        assertThat(voucher.displayStatusLabel(now)).isEqualTo("Chưa tới ngày");

        voucher.setStartAt(start);
        voucher.setEndAt(now.minusDays(1));
        assertThat(voucher.displayStatusLabel(now)).isEqualTo("Hết hạn");

        voucher.setEndAt(end);
        voucher.setQuantity(4);
        voucher.setUsedQuantity(4);
        assertThat(voucher.displayStatusLabel(now)).isEqualTo("Hết lượt");

        voucher.setUsedQuantity(0);
        voucher.setStatus(VoucherStatus.INACTIVE);
        assertThat(voucher.displayStatusLabel(now)).isEqualTo("Ngừng áp dụng");
    }

    @Test
    @DisplayName("Khách áp mã: kiểm tra đủ điều kiện khoá / chưa tới ngày / hết hạn / hết lượt / chưa đủ đơn tối thiểu")
    void requireUsable_validatesAllRules() {
        Voucher voucher = existingVoucher();
        when(voucherRepository.findByCode("GIAM10")).thenReturn(Optional.of(voucher));
        BigDecimal enough = new BigDecimal("5000000");

        voucher.setStatus(VoucherStatus.INACTIVE);
        assertThatThrownBy(() -> voucherService.requireUsable("GIAM10", enough))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã bị khoá");

        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setStartAt(LocalDateTime.now().plusDays(2));
        assertThatThrownBy(() -> voucherService.requireUsable(" giam10 ", enough))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa tới thời gian sử dụng");

        voucher.setStartAt(LocalDateTime.now().minusDays(2));
        voucher.setEndAt(LocalDateTime.now().minusDays(1));
        assertThatThrownBy(() -> voucherService.requireUsable("GIAM10", enough))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hạn sử dụng");

        voucher.setEndAt(LocalDateTime.now().plusDays(2));
        voucher.setUsedQuantity(50);
        assertThatThrownBy(() -> voucherService.requireUsable("GIAM10", enough))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết lượt sử dụng");

        voucher.setUsedQuantity(0);
        voucher.setMinOrderValue(new BigDecimal("10000000"));
        assertThatThrownBy(() -> voucherService.requireUsable("GIAM10", enough))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ áp dụng cho đơn hàng từ");

        voucher.setMinOrderValue(BigDecimal.ZERO);
        assertThat(voucherService.requireUsable("GIAM10", enough)).isSameAs(voucher);
    }

    private VoucherForm form(String code, DiscountType type, String value) {
        VoucherForm form = new VoucherForm();
        form.setCode(code);
        form.setName("Giảm giá thử nghiệm");
        form.setDescription("Mô tả thử nghiệm");
        form.setDiscountType(type);
        form.setDiscountValue(new BigDecimal(value));
        form.setMinOrderValue(BigDecimal.ZERO);
        form.setQuantity(50);
        form.setStartAt(start);
        form.setEndAt(end);
        form.setStatus(VoucherStatus.ACTIVE);
        return form;
    }

    private Voucher existingVoucher() {
        Voucher voucher = new Voucher();
        voucher.setId(VOUCHER_ID);
        voucher.setCode("GIAM10");
        voucher.setName("Giảm 10%");
        voucher.setDiscountType(DiscountType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10"));
        voucher.setMinOrderValue(BigDecimal.ZERO);
        voucher.setQuantity(50);
        voucher.setUsedQuantity(0);
        voucher.setStartAt(start);
        voucher.setEndAt(end);
        voucher.setStatus(VoucherStatus.ACTIVE);
        return voucher;
    }
}
