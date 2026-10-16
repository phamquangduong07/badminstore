package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trạng thái thanh toán – khớp CK_payments_status. */
@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("Chờ thanh toán"),
    PAID("Đã thanh toán"),
    FAILED("Thanh toán thất bại"),
    REFUNDED("Đã hoàn tiền");

    private final String label;
}
