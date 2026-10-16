package com.example.demo.dto.admin;

import com.example.demo.enums.DiscountType;
import com.example.demo.enums.VoucherStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Form admin tạo/sửa voucher. Validate 2 lớp: HTML (required/min/step) + Bean Validation ở đây,
 * lỗi hiện cạnh từng ô; các luật nghiệp vụ chéo (PERCENT ≤ 100, end > start, số lượt ≥ đã dùng…)
 * do VoucherService kiểm tra và báo bằng thông báo tiếng Việt ở đầu form.
 */
@Getter
@Setter
public class VoucherForm {

    @NotBlank(message = "Vui lòng nhập mã giảm giá.")
    @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã chỉ gồm chữ, số, gạch ngang hoặc gạch dưới (không dấu cách).")
    private String code;

    @NotBlank(message = "Vui lòng nhập tên chương trình giảm giá.")
    @Size(max = 100, message = "Tên chương trình tối đa 100 ký tự.")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
    private String description;

    @NotNull(message = "Vui lòng chọn kiểu giảm giá.")
    private DiscountType discountType = DiscountType.PERCENT;

    @NotNull(message = "Vui lòng nhập giá trị giảm.")
    @DecimalMin(value = "0", inclusive = false, message = "Giá trị giảm phải lớn hơn 0.")
    private BigDecimal discountValue;

    @NotNull(message = "Vui lòng nhập giá trị đơn hàng tối thiểu.")
    @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không được âm.")
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    /** Chỉ dùng cho kiểu PERCENT; để trống = không giới hạn mức giảm. */
    @DecimalMin(value = "0", message = "Mức giảm tối đa không được âm.")
    private BigDecimal maxDiscountAmount;

    @Min(value = 1, message = "Số lượt phát hành phải từ 1 trở lên.")
    private int quantity = 10;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @NotNull(message = "Vui lòng chọn trạng thái.")
    private VoucherStatus status = VoucherStatus.ACTIVE;
}
