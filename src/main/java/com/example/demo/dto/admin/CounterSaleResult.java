package com.example.demo.dto.admin;

import com.example.demo.entity.Order;
import java.math.BigDecimal;

/**
 * Kết quả một lượt bán tại quầy: đơn hàng đã tạo + tiền khách đưa + tiền thừa (nếu trả tiền mặt).
 * Tiền thừa do server tính từ tổng tiền hoá đơn, KHÔNG nhận từ trình duyệt.
 */
public record CounterSaleResult(Order order, BigDecimal cashReceived, BigDecimal change) {

    public boolean isCash() {
        return cashReceived != null;
    }
}
