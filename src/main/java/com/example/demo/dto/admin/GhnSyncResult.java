package com.example.demo.dto.admin;

import com.example.demo.enums.OrderStatus;

/**
 * Kết quả đồng bộ trạng thái vận đơn GHN về đơn hàng của shop.
 *
 * @param ghnOrderCode  mã vận đơn GHN
 * @param ghnStatus     mã trạng thái thô của GHN (ví dụ "delivering")
 * @param ghnStatusLabel nhãn tiếng Việt của trạng thái GHN
 * @param orderStatus   trạng thái đơn sau khi đồng bộ
 * @param changed       có đổi trạng thái đơn không
 * @param message       thông báo hiển thị cho admin (flash message)
 */
public record GhnSyncResult(String ghnOrderCode,
                            String ghnStatus,
                            String ghnStatusLabel,
                            OrderStatus orderStatus,
                            boolean changed,
                            String message) {
}
