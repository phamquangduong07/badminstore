package com.example.demo.dto.admin;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả tính tiền tạm thời ở màn bán hàng tại quầy: chi tiết từng dòng (tên, giá, tồn kho)
 * + tạm tính/giảm giá/tổng. Mọi con số đều lấy từ DB nên quầy hiển thị đúng như khi lưu hoá đơn.
 */
public record PosPriceResult(BigDecimal subtotal,
                             BigDecimal discount,
                             BigDecimal total,
                             String voucherCode,
                             String voucherDescription,
                             String voucherError,
                             List<LineView> lines) {

    /**
     * Một dòng hàng sau khi server tra giá.
     * {@code error} khác null nghĩa là dòng này chưa bán được (sai SKU, hết hàng, vượt tồn kho).
     */
    public record LineView(String sku,
                           int quantity,
                           String productName,
                           String variantLabel,
                           BigDecimal unitPrice,
                           BigDecimal lineTotal,
                           int stock,
                           String imageUrl,
                           String error) {
    }
}
