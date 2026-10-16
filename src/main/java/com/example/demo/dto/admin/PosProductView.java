package com.example.demo.dto.admin;

import com.example.demo.entity.ProductVariant;
import java.math.BigDecimal;

/**
 * Một dòng kết quả tìm kiếm ở màn bán hàng tại quầy (trả về dạng JSON cho JavaScript).
 * Chỉ chứa dữ liệu của biến thể + tên sản phẩm đã được nạp sẵn (không chạm quan hệ LAZY khác).
 */
public record PosProductView(Integer variantId,
                             String sku,
                             String productName,
                             String variantLabel,
                             BigDecimal price,
                             int stock,
                             String imageUrl,
                             boolean available) {

    public static PosProductView of(ProductVariant variant) {
        String label = variant.getColor() + " / " + variant.getWeightClass() + " / " + variant.getGripSize();
        return new PosProductView(variant.getId(), variant.getSku(), variant.getProduct().getName(), label,
                variant.getPrice(), variant.getStockQuantity(), variant.getImageUrl(), variant.getStockQuantity() > 0);
    }

    /* Getter kiểu bean để Thymeleaf đọc được khi cần render trực tiếp (giữ đồng bộ với JSON). */
    public Integer getVariantId() {
        return variantId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public String getVariantLabel() {
        return variantLabel;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isAvailable() {
        return available;
    }
}
