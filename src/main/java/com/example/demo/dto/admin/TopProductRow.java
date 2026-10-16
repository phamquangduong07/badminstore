package com.example.demo.dto.admin;

import java.math.BigDecimal;

/** Sản phẩm bán chạy: gom từ order_items (không tính đơn huỷ/hoàn trả). */
public record TopProductRow(String productName, Long quantity, BigDecimal revenue) {
}
