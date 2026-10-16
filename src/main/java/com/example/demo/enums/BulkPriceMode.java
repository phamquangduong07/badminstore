package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Kiểu cập nhật giá hàng loạt cho biến thể (mục 13 trong docs/TIEU_CHI_KHA.md).
 *
 * Dự án KHÔNG có bảng promotion: giảm giá chỉ dùng cặp cột original_price → price của biến thể
 * và voucher cho tổng hoá đơn, nên thao tác hàng loạt ở đây chỉ sửa đúng 2 cột đó.
 */
@Getter
@RequiredArgsConstructor
public enum BulkPriceMode {

    /** Giảm giá theo %: giá gạch ngang = giá đang bán, giá bán mới = giá cũ giảm %. */
    DISCOUNT("Giảm giá theo %"),

    /** Bỏ giảm giá: trả giá bán về giá gạch ngang rồi xoá giá gạch ngang. */
    CLEAR_DISCOUNT("Bỏ giảm giá (giá bán = giá gạch ngang)");

    private final String label;
}
