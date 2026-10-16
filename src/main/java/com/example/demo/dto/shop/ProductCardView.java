package com.example.demo.dto.shop;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Dữ liệu 1 thẻ sản phẩm trên trang danh sách.
 * Được dựng bằng JPQL "select new …" (gộp giá thấp nhất + tổng tồn kho trong 1 truy vấn)
 * nên KHÔNG phải load từng variant cho từng sản phẩm (tránh N+1).
 *
 * Giá hiển thị = giá thấp nhất trong các biến thể đang bán; sản phẩm chưa có biến thể
 * hoặc đã hết hàng thì minPrice/totalStock là null/0 → template hiện "Liên hệ"/"Tạm hết hàng".
 */
@Getter
@Setter
public class ProductCardView {

    private final Integer id;
    private final String name;
    private final String productCode;
    private final String brandName;
    private final String categoryName;
    private final BigDecimal minPrice;
    private final BigDecimal minOriginalPrice;
    private final Long totalStock;
    private final Long variantCount;

    /** Ảnh đại diện – Service gắn thêm sau khi truy vấn ảnh của cả trang trong 1 lượt. */
    private String imageUrl;

    public ProductCardView(Integer id, String name, String productCode, String brandName, String categoryName,
                           BigDecimal minPrice, BigDecimal minOriginalPrice, Long totalStock, Long variantCount) {
        this.id = id;
        this.name = name;
        this.productCode = productCode;
        this.brandName = brandName;
        this.categoryName = categoryName;
        this.minPrice = minPrice;
        this.minOriginalPrice = minOriginalPrice;
        this.totalStock = totalStock;
        this.variantCount = variantCount;
    }

    /** Có đang giảm giá (giá gốc gạch ngang lớn hơn giá bán thấp nhất)? */
    public boolean isDiscounted() {
        return minPrice != null && minOriginalPrice != null && minOriginalPrice.compareTo(minPrice) > 0;
    }

    /** Còn hàng để bán? */
    public boolean isAvailable() {
        return totalStock != null && totalStock > 0;
    }
}
