package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Vai trò người dùng – khớp CK_users_role trong BadStore.sql. */
@Getter
@RequiredArgsConstructor
public enum UserRole {

    ADMIN("Quản trị viên"),
    CUSTOMER("Khách hàng");

    private final String label;
}
