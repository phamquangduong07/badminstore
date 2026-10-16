package com.example.demo.dto.shop;

import com.example.demo.entity.CartItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * Một dòng giỏ hàng đã "làm phẳng" để template hiển thị:
 * không chứa Entity nên không sợ lỗi lazy-load, và mang luôn các cờ nghiệp vụ
 * (còn bán / còn đủ hàng) để giao diện cảnh báo đúng chỗ.
 *
 * Dựng từ CartItem đã fetch join variant + product (xem CartItemRepository#findCartByUserId).
 */
@Getter
public class CartLine {

    private final Integer cartItemId;
    private final Integer productId;
    private final String productName;
    private final String productCode;
    private final Integer variantId;
    private final String sku;
    private final String color;
    private final String weightClass;
    private final String gripSize;
    private final String imageUrl;
    private final BigDecimal price;
    private final BigDecimal originalPrice;
    private final int quantity;
    private final int stockQuantity;
    private final boolean productActive;
    private final boolean variantActive;

    /** Số lượng tối đa được phép đặt cho dòng này (theo tồn kho) – Service truyền vào. */
    private final int maxQuantity;

    public CartLine(CartItem item, String imageUrl, int maxQuantity) {
        ProductVariant variant = item.getProductVariant();
        Product product = variant.getProduct();

        this.cartItemId = item.getId();
        this.productId = product.getId();
        this.productName = product.getName();
        this.productCode = product.getProductCode();
        this.variantId = variant.getId();
        this.sku = variant.getSku();
        this.color = variant.getColor();
        this.weightClass = variant.getWeightClass();
        this.gripSize = variant.getGripSize();
        this.imageUrl = imageUrl;
        this.price = variant.getPrice();
        this.originalPrice = variant.getOriginalPrice();
        this.quantity = item.getQuantity();
        this.stockQuantity = variant.getStockQuantity();
        this.productActive = product.getStatus() == ProductStatus.ACTIVE && !product.isDeleted();
        this.variantActive = variant.getStatus() == VariantStatus.ACTIVE;
        this.maxQuantity = maxQuantity;
    }

    /** Thành tiền của dòng = đơn giá × số lượng. */
    public BigDecimal getLineTotal() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /** Nhãn phiên bản để hiển thị và ghi vào flash: "Xanh đen · 4U · G5". */
    public String getVariantLabel() {
        return color + " · " + weightClass + " · " + gripSize;
    }

    /** Đang giảm giá (giá gốc gạch ngang lớn hơn giá bán)? */
    public boolean isDiscounted() {
        return originalPrice != null && originalPrice.compareTo(price) > 0;
    }

    /**
     * Lý do dòng hàng này không đặt được (null = đặt được).
     * Trang giỏ hàng hiện lý do cạnh dòng đó và chặn nút thanh toán.
     */
    public String getIssue() {
        if (!productActive) {
            return "Sản phẩm đã ngừng bán";
        }
        if (!variantActive) {
            return "Phiên bản đã ngừng bán";
        }
        if (stockQuantity <= 0) {
            return "Tạm hết hàng";
        }
        if (quantity > stockQuantity) {
            return "Chỉ còn " + stockQuantity + " sản phẩm";
        }
        return null;
    }

    /** Dòng hàng đặt được ngay với đúng số lượng đang có trong giỏ. */
    public boolean isAvailable() {
        return getIssue() == null;
    }
}
