package com.example.demo.config;

import com.example.demo.dto.admin.GhnSyncResult;
import com.example.demo.entity.Order;
import com.example.demo.enums.OrderStatus;
import com.example.demo.repository.OrderRepository;
import com.example.demo.service.GhnShippingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự động đồng bộ trạng thái vận đơn GHN cho các đơn đang giao.
 *
 * MẶC ĐỊNH TẮT (app.ghn.sync-enabled = false) để buổi demo không phát sinh lời gọi API ngoài ý muốn:
 * admin vẫn đồng bộ thủ công bằng nút "Đồng bộ trạng thái GHN" ở trang chi tiết đơn.
 * Muốn bật: đặt GHN_SYNC_ENABLED=true (chu kỳ chỉnh bằng app.ghn.sync-delay-ms).
 */
@Component
@RequiredArgsConstructor
public class GhnSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GhnSyncScheduler.class);

    private final GhnProperties ghnProperties;
    private final GhnShippingService ghnShippingService;
    private final OrderRepository orderRepository;

    @Scheduled(fixedDelayString = "${app.ghn.sync-delay-ms:600000}", initialDelayString = "${app.ghn.sync-initial-delay-ms:60000}")
    public void syncDeliveringOrders() {
        if (!ghnProperties.isSyncEnabled() || !ghnProperties.isEnabled()) {
            return;
        }
        List<Order> orders = orderRepository.findByGhnOrderCodeNotNullAndOrderStatusIn(
                List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING));
        if (orders.isEmpty()) {
            return;
        }
        log.debug("Đồng bộ trạng thái GHN cho {} đơn đang giao…", orders.size());
        for (Order order : orders) {
            try {
                GhnSyncResult result = ghnShippingService.syncStatus(order.getId(), null);
                if (result.changed()) {
                    log.info("Đơn {} → {} (theo GHN: {})", order.getOrderCode(),
                            result.orderStatus().getLabel(), result.ghnStatusLabel());
                }
            } catch (RuntimeException ex) {
                // Một đơn lỗi không được làm dừng việc đồng bộ các đơn còn lại.
                log.warn("Đồng bộ GHN cho đơn {} thất bại: {}", order.getOrderCode(), ex.getMessage());
            }
        }
    }
}
