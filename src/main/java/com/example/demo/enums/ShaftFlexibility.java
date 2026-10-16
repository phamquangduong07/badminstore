package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Độ cứng thân vợt (shaft) – khớp CK_products_flex. */
@Getter
@RequiredArgsConstructor
public enum ShaftFlexibility {

    SOFT("Mềm"),
    MEDIUM("Trung bình"),
    STIFF("Cứng"),
    EXTRA_STIFF("Rất cứng");

    private final String label;
}
