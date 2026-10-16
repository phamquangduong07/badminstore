package com.example.demo.dto.admin;

import com.example.demo.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng đơn hàng thô phục vụ thống kê dashboard (created_at, tiền, trạng thái).
 * Service tự gom theo ngày nên không phụ thuộc hàm CAST ngày của từng hệ CSDL.
 */
public record RevenueRow(LocalDateTime createdAt, BigDecimal totalAmount, OrderStatus orderStatus) {
}
