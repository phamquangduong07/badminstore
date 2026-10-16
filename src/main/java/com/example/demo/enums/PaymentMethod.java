package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Phương thức thanh toán – khớp CK_payments_method. */
@Getter
@RequiredArgsConstructor
public enum PaymentMethod {

    COD("Thanh toán khi nhận hàng (COD)"),
    BANK_TRANSFER("Chuyển khoản ngân hàng"),
    CASH("Tiền mặt tại quầy");

    private final String label;
}
