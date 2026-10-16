package com.example.demo.dto.shop;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Điều kiện tìm kiếm/lọc trên trang danh sách sản phẩm (bind trực tiếp từ query string).
 * Tất cả trường đều là "tuỳ chọn": null = không lọc.
 */
@Getter
@Setter
@NoArgsConstructor
public class ProductFilter {

    /** Từ khoá tìm theo tên vợt. */
    private String keyword;

    private Integer brandId;

    private Integer categoryId;

    private BigDecimal minPrice;

    private BigDecimal maxPrice;

    /** Tên enum ProductSort dạng chuỗi – Service tự chuyển đổi để tránh lỗi 400 khi khách sửa URL. */
    private String sort;
}
