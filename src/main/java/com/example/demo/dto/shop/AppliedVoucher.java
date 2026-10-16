package com.example.demo.dto.shop;

import java.math.BigDecimal;

/**
 * Voucher đã áp thành công vào đơn – chỉ mang dữ liệu hiển thị (không mang Entity ra view).
 */
public record AppliedVoucher(String code, String name, String description, BigDecimal discountAmount) {

    /* Getters kiểu bean để Thymeleaf đọc được ${voucher.code}, ${voucher.description}… */
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }
}
