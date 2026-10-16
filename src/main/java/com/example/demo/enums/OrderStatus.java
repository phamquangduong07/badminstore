package com.example.demo.enums;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái đơn hàng + MÁY TRẠNG THÁI (xem AGENTS.md mục 4 và BadStore.sql mục A).
 *
 * <pre>
 *   PENDING   -> CONFIRMED (TRỪ KHO) | CANCELLED
 *   CONFIRMED -> SHIPPING            | CANCELLED   (huỷ: cộng lại kho)
 *   SHIPPING  -> DELIVERED           | CANCELLED   (huỷ: cộng lại kho)
 *   DELIVERED -> RETURNED (hoàn trả 100%: cộng lại kho, payment -> REFUNDED nếu đã PAID)
 * </pre>
 *
 * Enum chỉ khai báo luật chuyển trạng thái; Service gọi {@link #canTransitionTo(OrderStatus)}
 * rồi ném {@code BusinessException} nếu không hợp lệ.
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("Chờ xác nhận"),
    CONFIRMED("Đã xác nhận"),
    SHIPPING("Đang giao hàng"),
    DELIVERED("Giao thành công"),
    CANCELLED("Đã huỷ"),
    RETURNED("Đã hoàn trả");

    private final String label;

    /** Danh sách trạng thái được phép chuyển tới từ trạng thái hiện tại (giữ thứ tự để hiển thị). */
    public List<OrderStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> List.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> List.of(SHIPPING, CANCELLED);
            case SHIPPING -> List.of(DELIVERED, CANCELLED);
            case DELIVERED -> List.of(RETURNED);
            case CANCELLED, RETURNED -> List.of();
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return next != null && nextStatuses().contains(next);
    }

    /**
     * Trạng thái này có bắt buộc đã trừ kho hay không – dùng để set {@code orders.stock_deducted}
     * cho khớp ràng buộc CK_orders_stock:
     * CONFIRMED/SHIPPING/DELIVERED &lt;=&gt; stock_deducted = 1;
     * PENDING/CANCELLED/RETURNED &lt;=&gt; stock_deducted = 0.
     */
    public boolean isStockDeducted() {
        return this == CONFIRMED || this == SHIPPING || this == DELIVERED;
    }

    /** Đơn đã kết thúc (không thể đổi trạng thái tiếp). */
    public boolean isFinal() {
        return this == CANCELLED || this == RETURNED;
    }
}
