package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Nguồn tạo đơn – khớp CK_orders_source.
 * ONLINE: khách tự đặt trên web. MANUAL: admin tạo đơn thủ công (đặt qua điện thoại/quầy).
 */
@Getter
@RequiredArgsConstructor
public enum OrderSource {

    ONLINE("Khách đặt trên web"),
    MANUAL("Admin tạo thủ công");

    private final String label;
}
