package com.example.demo.dto.admin;

import com.example.demo.entity.Product;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Một dòng trong bảng quản trị sản phẩm: sản phẩm + số liệu tổng hợp từ biến thể
 * (tính sẵn ở Service nên template không phải gọi thêm truy vấn nào).
 */
@Getter
public class ProductRow {

    private final Product product;
    /** Giá thấp nhất trong các biến thể đang bán; null = chưa có biến thể nào bán được. */
    private final BigDecimal minPrice;
    private final long totalStock;
    private final long activeVariantCount;
    private final String thumbnailUrl;

    public ProductRow(Product product, BigDecimal minPrice, long totalStock, long activeVariantCount,
                      String thumbnailUrl) {
        this.product = product;
        this.minPrice = minPrice;
        this.totalStock = totalStock;
        this.activeVariantCount = activeVariantCount;
        this.thumbnailUrl = thumbnailUrl;
    }

    /** Chưa có biến thể đang bán thì không thể đặt hàng. */
    public boolean isSellable() {
        return activeVariantCount > 0;
    }
}
