package com.example.demo.entity;

import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thanh toán của đơn (bảng payments) – mỗi đơn đúng 1 bản ghi (order_id UNIQUE).
 * COD: PAID khi đơn DELIVERED. Chuyển khoản: admin xác nhận tay (confirmed_by + paid_at).
 * Hoàn trả 100%: payment đã PAID chuyển thành REFUNDED.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** Luôn bằng orders.total_amount – tầng ứng dụng kiểm tra, không nhận từ client. */
    @Column(name = "amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Mã giao dịch ngân hàng (nếu có) – UNIQUE có điều kiện trong DB. */
    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    /** Admin xác nhận đã nhận tiền. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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

    /** Đánh dấu đã nhận tiền – luôn set paid_at để khớp CK_payments_paid. */
    public void markPaid(User confirmer) {
        paymentStatus = PaymentStatus.PAID;
        paidAt = LocalDateTime.now();
        confirmedBy = confirmer;
    }
}
