package com.example.demo.dto.shop;

import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Dữ liệu trang chi tiết sản phẩm: sản phẩm + danh sách biến thể + bộ ảnh.
 *
 * Lưu ý: Product ở đây đã được fetch join brand/category (xem ProductRepository#findDetailById)
 * nên template đọc được tên hãng/loại mà không bị lỗi lazy-load (dự án đặt open-in-view = false).
 */
public record ProductDetailView(Product product, List<ProductVariant> variants, List<ProductImage> images) {

    /** Còn ít nhất 1 biến thể đang bán và còn tồn kho. */
    public boolean isPurchasable() {
        return variants.stream().anyMatch(ProductVariant::isInStock);
    }

    /** Giá thấp nhất trong các biến thể đang bán (null nếu sản phẩm chưa có biến thể nào). */
    public BigDecimal getMinPrice() {
        return variants.stream().map(ProductVariant::getPrice).min(Comparator.naturalOrder()).orElse(null);
    }

    /** Giá cao nhất trong các biến thể đang bán. */
    public BigDecimal getMaxPrice() {
        return variants.stream().map(ProductVariant::getPrice).max(Comparator.naturalOrder()).orElse(null);
    }

    /** Các phiên bản có giá khác nhau ⇒ hiển thị "từ X ₫ đến Y ₫" thay vì một giá duy nhất. */
    public boolean isPriceRange() {
        return getMinPrice() != null && getMinPrice().compareTo(getMaxPrice()) != 0;
    }

    /** Có ít nhất 1 phiên bản đang giảm giá (giá gốc gạch ngang lớn hơn giá bán). */
    public boolean isDiscounted() {
        return variants.stream().anyMatch(ProductVariant::isDiscounted);
    }

    /** Phiên bản còn hàng đầu tiên – chọn sẵn ở ô chọn phiên bản (mặc định của trang chi tiết). */
    public Integer getDefaultVariantId() {
        return variants.stream()
                .filter(ProductVariant::isInStock)
                .map(ProductVariant::getId)
                .findFirst()
                .orElse(null);
    }

    /** Tồn kho lớn nhất trong các phiên bản đang bán – giá trị max ban đầu của ô số lượng. */
    public int getMaxStock() {
        return variants.stream()
                .filter(ProductVariant::isInStock)
                .mapToInt(ProductVariant::getStockQuantity)
                .max()
                .orElse(0);
    }
}
