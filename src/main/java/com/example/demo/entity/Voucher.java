package com.example.demo.entity;

import com.example.demo.enums.DiscountType;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.util.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Voucher giảm giá cho TỔNG hoá đơn (bảng vouchers) – dự án không có bảng promotion,
 * giảm giá sản phẩm chỉ thể hiện bằng giá gạch ngang ở {@link ProductVariant#getOriginalPrice()}.
 * Lượt dùng tăng/giảm bằng UPDATE atomic ở tầng repository (used_quantity).
 */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "code", nullable = false, length = 50, unique = true)
    private String code;

    @Nationalized
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Nationalized
    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** PERCENT: 0–100 (%); FIXED: số tiền VND. */
    @Column(name = "discount_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_value", nullable = false, precision = 18, scale = 0)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    /** Giảm tối đa (chủ yếu dùng cho loại PERCENT), NULL = không giới hạn. */
    @Column(name = "max_discount_amount", precision = 18, scale = 0)
    private BigDecimal maxDiscountAmount;

    /** Tổng số lượt phát hành. */
    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "used_quantity", nullable = false)
    private int usedQuantity = 0;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VoucherStatus status = VoucherStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Còn lượt và đang trong thời gian áp dụng? (kiểm tra min/max đơn ở Service) */
    public boolean isUsableAt(LocalDateTime now) {
        return status == VoucherStatus.ACTIVE
                && usedQuantity < quantity
                && !now.isBefore(startAt)
                && !now.isAfter(endAt);
    }

    /**
     * Mã trạng thái để trang admin tô màu badge, tính cả các trường hợp "đã hết" mà cột status không thể hiện:
     * ACTIVE (đang chạy) | UPCOMING (chưa tới ngày) | EXPIRED (hết hạn) | USED_UP (hết lượt) | INACTIVE (đã khoá).
     */
    public String displayStatusCode(LocalDateTime now) {
        if (status != VoucherStatus.ACTIVE) {
            return "INACTIVE";
        }
        if (now.isBefore(startAt)) {
            return "UPCOMING";
        }
        if (now.isAfter(endAt)) {
            return "EXPIRED";
        }
        return usedQuantity >= quantity ? "USED_UP" : "ACTIVE";
    }

    /** Nhãn tiếng Việt tương ứng với {@link #displayStatusCode(LocalDateTime)}. */
    public String displayStatusLabel(LocalDateTime now) {
        return switch (displayStatusCode(now)) {
            case "INACTIVE" -> "Ngừng áp dụng";
            case "UPCOMING" -> "Chưa tới ngày";
            case "EXPIRED" -> "Hết hạn";
            case "USED_UP" -> "Hết lượt";
            default -> "Đang áp dụng";
        };
    }

    /**
     * Mô tả ngắn để hiển thị ở trang admin và ở bước thanh toán:
     * "Giảm 10% (tối đa 200.000 ₫)" hoặc "Giảm 100.000 ₫".
     */
    public String describe() {
        String value = discountType == DiscountType.PERCENT
                ? ("Giảm " + discountValue.stripTrailingZeros().toPlainString() + "%")
                : ("Giảm " + Money.format(discountValue));
        if (discountType == DiscountType.PERCENT && maxDiscountAmount != null) {
            return value + " (tối đa " + Money.format(maxDiscountAmount) + ")";
        }
        return value;
    }
}
