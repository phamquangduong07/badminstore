package com.example.demo.dto.shop;

import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.VariantStatus;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;

/**
 * Một CỘT trong bảng so sánh sản phẩm.
 *
 * Mọi giá trị hiển thị được tính sẵn thành chuỗi tiếng Việt ở đây (nhãn "—" khi thiếu dữ liệu)
 * để template không phải chứa logic, và để tránh lazy-load ngoài transaction
 * (dự án đặt spring.jpa.open-in-view = false).
 */
@Getter
public class CompareItem {

    private final Product product;
    private final BigDecimal minPrice;
    private final long totalStock;
    private final long activeVariantCount;
    private final String thumbnailUrl;

    private CompareItem(Product product, BigDecimal minPrice, long totalStock, long activeVariantCount,
                        String thumbnailUrl) {
        this.product = product;
        this.minPrice = minPrice;
        this.totalStock = totalStock;
        this.activeVariantCount = activeVariantCount;
        this.thumbnailUrl = thumbnailUrl;
    }

    /** Tính sẵn giá thấp nhất, tổng tồn kho và số phiên bản đang bán của một sản phẩm. */
    public static CompareItem of(Product product, List<ProductVariant> variants, String thumbnailUrl) {
        List<ProductVariant> sellable = variants.stream()
                .filter(variant -> variant.getStatus() == VariantStatus.ACTIVE)
                .toList();
        BigDecimal minPrice = sellable.stream().map(ProductVariant::getPrice).min(Comparator.naturalOrder())
                .orElse(null);
        long totalStock = sellable.stream().mapToLong(ProductVariant::getStockQuantity).sum();
        return new CompareItem(product, minPrice, totalStock, sellable.size(), thumbnailUrl);
    }

    public boolean isAvailable() {
        return totalStock > 0;
    }

    public String getStockLabel() {
        if (activeVariantCount == 0) {
            return "Chưa mở bán";
        }
        return totalStock > 0 ? "Còn hàng" : "Tạm hết hàng";
    }

    public String getVariantLabel() {
        return activeVariantCount == 0 ? "—" : activeVariantCount + " phiên bản";
    }

    public String getStockQuantityLabel() {
        return activeVariantCount == 0 ? "—" : totalStock + " chiếc";
    }

    public String getLengthLabel() {
        return product.getLengthMm() == null ? "—" : product.getLengthMm() + " mm";
    }

    public String getBalancePointLabel() {
        return product.getBalancePointMm() == null ? "—" : product.getBalancePointMm() + " mm";
    }

    public String getBalanceTypeLabel() {
        return product.getBalanceType() == null ? "—" : product.getBalanceType().getLabel();
    }

    public String getFlexibilityLabel() {
        return product.getShaftFlexibility() == null ? "—" : product.getShaftFlexibility().getLabel();
    }

    public String getMaxTensionLabel() {
        return product.getMaxTensionKg() == null ? "—" : product.getMaxTensionKg() + " kg";
    }

    public String getFrameMaterialLabel() {
        return product.getFrameMaterial() == null ? "—" : product.getFrameMaterial();
    }

    public String getShaftMaterialLabel() {
        return product.getShaftMaterial() == null ? "—" : product.getShaftMaterial();
    }
}
