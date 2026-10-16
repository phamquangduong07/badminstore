package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Trạng thái biến thể (màu × trọng lượng × cỡ cán) – khớp CK_variants_status. */
@Getter
@RequiredArgsConstructor
public enum VariantStatus {

    ACTIVE("Đang bán"),
    INACTIVE("Ngừng bán");

    private final String label;
}
