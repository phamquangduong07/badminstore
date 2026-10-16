package com.example.demo.entity;

import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Đơn hàng (bảng orders).
 * Ràng buộc quan trọng của DB: CK_orders_stock – CONFIRMED/SHIPPING/DELIVERED thì stock_deducted = 1,
 * còn PENDING/CANCELLED/RETURNED thì stock_deducted = 0 (xem {@link OrderStatus#isStockDeducted()}).
 * CK_orders_total – total_amount = subtotal + shipping_fee - discount_amount.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "order_code", nullable = false, length = 30, unique = true)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source", nullable = false, length = 20)
    private OrderSource orderSource = OrderSource.ONLINE;

    /** NULL với đơn MANUAL của khách vãng lai. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Admin tạo đơn thủ công (bắt buộc khi order_source = MANUAL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private UserAddress address;

    @Nationalized
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Nationalized
    @Column(name = "shipping_address", length = 500)
    private String shippingAddress;

    @Column(name = "shipping_province_id")
    private Integer shippingProvinceId;

    @Column(name = "shipping_district_id")
    private Integer shippingDistrictId;

    @Column(name = "shipping_ward_code", length = 20)
    private String shippingWardCode;

    @Column(name = "subtotal", nullable = false, precision = 18, scale = 0)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 18, scale = 0)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus orderStatus = OrderStatus.PENDING;

    /** 1 = kho đã bị trừ cho đơn này. Luôn set cùng lúc với order_status. */
    @Column(name = "stock_deducted", nullable = false)
    private boolean stockDeducted = false;

    @Nationalized
    @Column(name = "note", length = 500)
    private String note;

    /** Lý do huỷ hoặc lý do hoàn trả. */
    @Nationalized
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** Mã vận đơn GHN (tính năng tuỳ chọn, chưa dùng ở giai đoạn đầu). */
    @Column(name = "ghn_order_code", length = 30)
    private String ghnOrderCode;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Payment payment;

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

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    /** Tính lại subtotal từ các dòng hàng rồi suy ra total_amount (khớp CK_orders_total). */
    public void recalculateTotals() {
        subtotal = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = subtotal.add(shippingFee).subtract(discountAmount);
    }

    /** Đang ở trạng thái được phép huỷ (PENDING/CONFIRMED/SHIPPING)? */
    public boolean isCancellable() {
        return orderStatus.canTransitionTo(OrderStatus.CANCELLED);
    }
}
