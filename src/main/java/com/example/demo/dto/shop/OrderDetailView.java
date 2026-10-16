package com.example.demo.dto.shop;

import com.example.demo.entity.Order;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Payment;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import java.util.List;
import lombok.Getter;

/**
 * Trang chi tiết đơn hàng (khách xem đơn của mình, admin xem mọi đơn).
 *
 * Order ở đây đã được fetch join đủ: items + productVariant + product + payment + user + voucher
 * (xem OrderRepository#findDetailById) nên template render được khi open-in-view = false.
 * Các trường của payment được "chụp" sẵn vào DTO để template không phải chạm vào entity LAZY.
 */
@Getter
public class OrderDetailView {

    private final Order order;
    private final List<OrderStatusHistory> timeline;

    /** "BADSTORE10 – Giảm 10% (tối đa 200.000 ₫)" hoặc null nếu đơn không dùng voucher. */
    private final String voucherLabel;

    private final PaymentMethod paymentMethod;
    private final PaymentStatus paymentStatus;
    private final String paymentMethodLabel;
    private final String paymentStatusLabel;
    private final String paymentBadgeClass;
    private final String transactionCode;

    public OrderDetailView(Order order, List<OrderStatusHistory> timeline, String voucherLabel) {
        this.order = order;
        this.timeline = List.copyOf(timeline);
        this.voucherLabel = voucherLabel;

        Payment payment = order.getPayment();
        this.paymentMethod = payment == null ? null : payment.getPaymentMethod();
        this.paymentStatus = payment == null ? null : payment.getPaymentStatus();
        this.paymentMethodLabel = paymentMethod == null ? "Chưa có thông tin" : paymentMethod.getLabel();
        this.paymentStatusLabel = paymentStatus == null ? "" : paymentStatus.getLabel();
        this.transactionCode = payment == null ? null : payment.getTransactionCode();
        this.paymentBadgeClass = switch (paymentStatus) {
            case null -> "text-bg-light";
            case PENDING -> "text-bg-warning";
            case PAID -> "text-bg-success";
            case FAILED -> "text-bg-danger";
            case REFUNDED -> "text-bg-secondary";
        };
    }

    public boolean isWalkIn() {
        return order.getUser() == null;
    }

    public String getCustomerLabel() {
        return order.getUser() != null ? order.getUser().getEmail() : "Khách mua tại quầy";
    }

    public String getStatusBadgeClass() {
        return switch (order.getOrderStatus()) {
            case PENDING -> "text-bg-warning";
            case CONFIRMED -> "text-bg-info";
            case SHIPPING -> "text-bg-primary";
            case DELIVERED -> "text-bg-success";
            case CANCELLED -> "text-bg-secondary";
            case RETURNED -> "text-bg-dark";
        };
    }

    /** Khách đang xem có được huỷ đơn không (PENDING/CONFIRMED). */
    public boolean isCancellableByCustomer() {
        return order.getOrderStatus() == OrderStatus.PENDING || order.getOrderStatus() == OrderStatus.CONFIRMED;
    }

    /** Khách đang xem có được gửi yêu cầu hoàn trả (đơn đã giao) không. */
    public boolean isReturnableByCustomer() {
        return order.getOrderStatus() == OrderStatus.DELIVERED;
    }

    /** Đơn đã kết thúc (huỷ/hoàn trả) – dùng để ẩn các nút thao tác. */
    public boolean isFinished() {
        return order.getOrderStatus().isFinal();
    }
}
