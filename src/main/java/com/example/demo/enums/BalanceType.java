package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Kiểu điểm cân bằng của vợt – khớp CK_products_balance_type. */
@Getter
@RequiredArgsConstructor
public enum BalanceType {

    HEAD_HEAVY("Nặng đầu (tấn công)"),
    EVEN("Cân bằng"),
    HEAD_LIGHT("Nhẹ đầu (phòng thủ)");

    private final String label;
}
