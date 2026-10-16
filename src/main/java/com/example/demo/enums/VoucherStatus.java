package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trạng thái voucher – khớp CK_vouchers_status trong BadStore.sql. */
@Getter
@RequiredArgsConstructor
public enum VoucherStatus {

    ACTIVE("Đang áp dụng"),
    INACTIVE("Ngừng áp dụng");

    private final String label;
}
