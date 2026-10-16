package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trạng thái tài khoản – khớp CK_users_status trong BadStore.sql. */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    ACTIVE("Đang hoạt động"),
    INACTIVE("Chưa kích hoạt"),
    LOCKED("Đã bị khoá");

    private final String label;
}
