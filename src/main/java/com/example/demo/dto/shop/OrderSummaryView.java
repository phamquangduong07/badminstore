package com.example.demo.dto.shop;

import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * Dữ liệu 1 dòng trong danh sách đơn hàng (khách: "Đơn hàng của tôi"; admin: quản lý đơn).
 * Được dựng bằng JPQL "select new …" (kèm 2 truy vấn con đếm số dòng/số lượng) nên không N+1.
 */
@Getter
public class OrderSummaryView {

    private final Integer id;
    private final String orderCode;
    private final OrderSource orderSource;
    private final OrderStatus orderStatus;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;
    private final String receiverName;

    /** Email khách đặt đơn – null với đơn MANUAL của khách vãng lai. */
    private final String userEmail;
    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;

    /** Số dòng hàng và tổng số sản phẩm của đơn. */
    private final Long lineCount;
    private final Long itemQuantity;

    public OrderSummaryView(Integer id, String orderCode, OrderSource orderSource, OrderStatus orderStatus,
                            BigDecimal totalAmount, LocalDateTime createdAt, String receiverName, String userEmail,
                            PaymentMethod paymentMethod, PaymentStatus paymentStatus,
                            Long lineCount, Long itemQuantity) {
        this.id = id;
        this.orderCode = orderCode;
        this.orderSource = orderSource;
        this.orderStatus = orderStatus;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.receiverName = receiverName;
        this.userEmail = userEmail;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.lineCount = lineCount;
        this.itemQuantity = itemQuantity;
    }

    public boolean isWalkIn() {
        return userEmail == null;
    }

    public String getCustomerLabel() {
        return userEmail != null ? userEmail : "Khách mua tại quầy";
    }

    public String getPaymentMethodLabel() {
        return paymentMethod != null ? paymentMethod.getLabel() : "Chưa có thông tin";
    }

    public String getPaymentStatusLabel() {
        return paymentStatus != null ? paymentStatus.getLabel() : "";
    }

    /** Màu badge Bootstrap theo trạng thái đơn. */
    public String getStatusBadgeClass() {
        return switch (orderStatus) {
            case PENDING -> "text-bg-warning";
            case CONFIRMED -> "text-bg-info";
            case SHIPPING -> "text-bg-primary";
            case DELIVERED -> "text-bg-success";
            case CANCELLED -> "text-bg-secondary";
            case RETURNED -> "text-bg-dark";
        };
    }

    /** Màu badge Bootstrap theo trạng thái thanh toán. */
    public String getPaymentBadgeClass() {
        if (paymentStatus == null) {
            return "text-bg-light";
        }
        return switch (paymentStatus) {
            case PENDING -> "text-bg-warning";
            case PAID -> "text-bg-success";
            case FAILED -> "text-bg-danger";
            case REFUNDED -> "text-bg-secondary";
        };
    }
}
