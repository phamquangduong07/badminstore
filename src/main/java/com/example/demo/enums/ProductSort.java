package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Các kiểu sắp xếp danh sách sản phẩm trên trang khách.
 * Giá trị lưu trong query string (?sort=PRICE_ASC) – enum name nên không cần thêm bảng tra.
 */
@Getter
@RequiredArgsConstructor
public enum ProductSort {

    NEWEST("Mới nhất"),
    NAME_ASC("Tên A → Z"),
    NAME_DESC("Tên Z → A"),
    PRICE_ASC("Giá thấp → cao"),
    PRICE_DESC("Giá cao → thấp");

    private final String label;

    /** Đọc tham số ?sort=… ; giá trị lạ hoặc rỗng thì quay về "Mới nhất" (không báo lỗi 400 cho khách). */
    public static ProductSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEWEST;
        }
        for (ProductSort sort : values()) {
            if (sort.name().equalsIgnoreCase(raw.trim())) {
                return sort;
            }
        }
        return NEWEST;
    }
}
