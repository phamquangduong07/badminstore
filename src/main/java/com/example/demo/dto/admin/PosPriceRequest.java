package com.example.demo.dto.admin;

import java.util.List;

/**
 * Yêu cầu tính tiền tạm thời của màn bán hàng tại quầy (JS gửi lên khi hoá đơn thay đổi).
 * Chỉ chứa SKU + số lượng; giá và giảm giá luôn do server tính từ DB.
 */
public record PosPriceRequest(String voucherCode, List<Line> lines) {

    /** Một dòng hàng quầy đang bán. */
    public record Line(String sku, int quantity) {
    }
}
