package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Kiểu giảm giá của voucher – khớp CK_vouchers_type. */
@Getter
@RequiredArgsConstructor
public enum DiscountType {

    PERCENT("Giảm theo phần trăm"),
    FIXED("Giảm số tiền cố định");

    private final String label;
}
