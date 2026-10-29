package com.example.demo.dto.admin;

import java.math.BigDecimal;

/**
 * Thống kê đơn hàng theo từng khách, gộp cả trang trong 1 truy vấn (tránh N+1).
 * {@code totalSpent} chỉ tính các đơn đã giao thành công (DELIVERED),
 * nên đơn huỷ/hoàn trả không bị tính vào "đã mua".
 */
public record UserOrderStatRow(Integer userId, Long orderCount, BigDecimal totalSpent) {
}
