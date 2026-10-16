package com.example.demo.enums;

import lombok.Getter;
import java.util.Locale;

/**
 * Trạng thái vận đơn của GHN (API v2 /shipping-order/detail) và cách quy đổi về trạng thái đơn của shop.
 *
 * Chỉ những trạng thái làm đổi vòng đời đơn mới có `targetStatus`, còn lại là null
 * (ví dụ "đang lấy hàng" thì đơn vẫn ở CONFIRMED, chưa cần đổi gì).
 */
@Getter
public enum GhnStatus {

    READY_TO_PICK("Chờ lấy hàng", null),
    PICKING("Đang lấy hàng", null),
    MONEY_COLLECT_PICKING("Đang lấy hàng (thu tiền)", null),
    PICKED("Đã lấy hàng", OrderStatus.SHIPPING),
    STORING("Đã nhập kho GHN", OrderStatus.SHIPPING),
    TRANSPORTING("Đang luân chuyển", OrderStatus.SHIPPING),
    SORTING("Đang phân loại", OrderStatus.SHIPPING),
    DELIVERING("Đang giao hàng", OrderStatus.SHIPPING),
    MONEY_COLLECT_DELIVERING("Đang giao hàng (thu tiền)", OrderStatus.SHIPPING),
    DELIVERED("Đã giao hàng", OrderStatus.DELIVERED),
    DELIVERY_FAIL("Giao hàng thất bại", OrderStatus.SHIPPING),
    WAITING_TO_RETURN("Chờ hoàn hàng", OrderStatus.RETURNED),
    RETURN_TRANSPORTING("Đang luân chuyển hoàn", OrderStatus.RETURNED),
    RETURN_SORTING("Đang phân loại hoàn", OrderStatus.RETURNED),
    RETURNING("Đang trả hàng", OrderStatus.RETURNED),
    RETURNED("Đã hoàn hàng", OrderStatus.RETURNED),
    RETURN_FAIL("Hoàn hàng thất bại", OrderStatus.RETURNED),
    RETURN("Hoàn hàng", OrderStatus.RETURNED),
    CANCEL("Đã huỷ vận đơn", OrderStatus.CANCELLED),
    EXCEPTION("Vận đơn có ngoại lệ", OrderStatus.CANCELLED),
    DAMAGE("Hàng bị hư hỏng", OrderStatus.CANCELLED),
    LOST("Hàng bị mất", OrderStatus.CANCELLED),
    UNKNOWN("Không xác định", null);

    private final String label;
    /** Trạng thái đơn tương ứng của shop (null = không cần đổi). */
    private final OrderStatus targetStatus;

    GhnStatus(String label, OrderStatus targetStatus) {
        this.label = label;
        this.targetStatus = targetStatus;
    }

    /** Ghép chuỗi trạng thái GHN (ví dụ "money_collect_picking") sang enum, lạ thì trả UNKNOWN. */
    public static GhnStatus fromApi(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN;
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        for (GhnStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
