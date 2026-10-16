package com.example.demo.dto.shop;

import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

/**
 * Toàn bộ giỏ hàng của 1 khách: các dòng + tạm tính + số lượng.
 * Tạm tính tính theo giá trong DB tại thời điểm xem (không tin dữ liệu client).
 */
@Getter
public class CartView {

    private final List<CartLine> lines;
    private final BigDecimal subtotal;
    private final int totalQuantity;

    public CartView(List<CartLine> lines) {
        this.lines = List.copyOf(lines);
        this.subtotal = lines.stream()
                .map(CartLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalQuantity = lines.stream().mapToInt(CartLine::getQuantity).sum();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Có ít nhất 1 dòng không đặt được (hết hàng, ngừng bán, vượt tồn) ⇒ chặn thanh toán. */
    public boolean isHasIssues() {
        return lines.stream().anyMatch(line -> !line.isAvailable());
    }

    /** Số dòng đặt được (dùng cho câu thông báo ở bước thanh toán). */
    public int getOrderableLineCount() {
        return (int) lines.stream().filter(CartLine::isAvailable).count();
    }
}
