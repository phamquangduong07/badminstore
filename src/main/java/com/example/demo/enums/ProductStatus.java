package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trạng thái sản phẩm – khớp CK_products_status trong BadStore.sql. */
@Getter
@RequiredArgsConstructor
public enum ProductStatus {

    ACTIVE("Đang bán"),
    DRAFT("Bản nháp"),
    HIDDEN("Đã ẩn");

    private final String label;
}
