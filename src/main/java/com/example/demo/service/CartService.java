package com.example.demo.service;

import com.example.demo.dto.shop.CartLine;
import com.example.demo.dto.shop.CartView;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ giỏ hàng (bảng cart_items – mỗi biến thể chỉ có 1 dòng/khách).
 *
 * Nguyên tắc kiểm tra số lượng (tiêu chí "validate chính xác số lượng" mức Khá):
 * – luôn đọc lại tồn kho từ DB ở MỌI thao tác (thêm/sửa), không tin số lượng từ client;
 * – số lượng 1 dòng từ 1..{@value #MAX_QUANTITY_PER_LINE} và không vượt tồn kho hiện tại;
 * – khi cộng dồn số lượng cũ + số lượng mới vẫn phải ≤ tồn kho.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    /** Chặn số lượng phi lý trên một dòng (khách nhập tay vào ô số lượng). */
    public static final int MAX_QUANTITY_PER_LINE = 20;

    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    /** Giỏ hàng đầy đủ của khách (đã gắn ảnh đại diện cho từng dòng). */
    @Transactional(readOnly = true)
    public CartView getCart(Integer userId) {
        List<CartItem> items = cartItemRepository.findCartByUserId(userId);
        Map<Integer, String> imageByProduct = primaryImages(items);
        List<CartLine> lines = items.stream()
                .map(item -> new CartLine(item,
                        imageOf(item.getProductVariant(), imageByProduct),
                        maxQuantity(item.getProductVariant())))
                .toList();
        return new CartView(lines);
    }

    /** Tổng số sản phẩm trong giỏ – hiện trên badge ở header. */
    @Transactional(readOnly = true)
    public int countItems(Integer userId) {
        Long total = cartItemRepository.sumQuantityByUserId(userId);
        return total == null ? 0 : (int) Math.min(Integer.MAX_VALUE, total.longValue());
    }

    /** Thêm vào giỏ: cùng biến thể thì cộng dồn số lượng, luôn chặn vượt tồn kho. */
    @Transactional
    public CartLine addToCart(Integer userId, Integer variantId, Integer quantity) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiên bản sản phẩm bạn vừa chọn."));

        int wanted = quantity == null ? 1 : quantity;
        requireValidQuantity(wanted);
        requireSellable(variant);

        CartItem item = cartItemRepository.findByUserIdAndProductVariantId(userId, variantId).orElse(null);
        int current = item == null ? 0 : item.getQuantity();
        int total = current + wanted;
        if (total > variant.getStockQuantity()) {
            throw new BusinessException(notEnoughStock(variant, total));
        }

        if (item == null) {
            item = new CartItem();
            item.setUser(userRepository.getReferenceById(userId));
            item.setProductVariant(variant);
        }
        item.setQuantity(total);
        return toLine(cartItemRepository.save(item), variant);
    }

    /** Sửa số lượng 1 dòng: chỉ áp dụng cho dòng thuộc giỏ của chính khách đang đăng nhập. */
    @Transactional
    public CartLine updateQuantity(Integer userId, Integer cartItemId, Integer quantity) {
        int wanted = quantity == null ? 0 : quantity;
        if (wanted < 1) {
            throw new BusinessException("Số lượng phải từ 1 trở lên. Muốn bỏ sản phẩm này, hãy dùng nút xoá.");
        }
        requireValidQuantity(wanted);

        CartItem item = myItem(userId, cartItemId);
        ProductVariant variant = item.getProductVariant();
        requireSellable(variant);
        requireValidQuantity(wanted);
        if (wanted > variant.getStockQuantity()) {
            throw new BusinessException(notEnoughStock(variant, wanted));
        }

        item.setQuantity(wanted);
        return toLine(cartItemRepository.save(item), variant);
    }

    /** Xoá 1 dòng khỏi giỏ. */
    @Transactional
    public void removeItem(Integer userId, Integer cartItemId) {
        cartItemRepository.delete(myItem(userId, cartItemId));
    }

    /** Xoá sạch giỏ (dùng cho nút "Xoá tất cả"), trả về số dòng đã xoá. */
    @Transactional
    public int clearCart(Integer userId) {
        List<CartItem> items = cartItemRepository.findCartByUserId(userId);
        if (!items.isEmpty()) {
            cartItemRepository.deleteAll(items);
        }
        return items.size();
    }

    // ==================== phần dùng chung ====================

    /** Dòng giỏ hàng của chính khách – người khác sửa id trên URL thì coi như không tồn tại. */
    private CartItem myItem(Integer userId, Integer cartItemId) {
        return cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm này trong giỏ hàng của bạn."));
    }

    /** Số lượng hợp lệ cho 1 dòng giỏ: từ 1 đến {@value #MAX_QUANTITY_PER_LINE}. */
    private void requireValidQuantity(int quantity) {
        if (quantity < 1) {
            throw new BusinessException("Số lượng phải từ 1 trở lên.");
        }
        if (quantity > MAX_QUANTITY_PER_LINE) {
            throw new BusinessException("Mỗi phiên bản chỉ được đặt tối đa " + MAX_QUANTITY_PER_LINE + " sản phẩm.");
        }
    }

    /** Biến thể + sản phẩm phải đang bán thì mới cho vào giỏ. */
    private void requireSellable(ProductVariant variant) {
        Product product = variant.getProduct();
        if (product.getStatus() != ProductStatus.ACTIVE || product.isDeleted()) {
            throw new BusinessException("Sản phẩm \"" + product.getName() + "\" đã ngừng bán.");
        }
        if (variant.getStatus() != VariantStatus.ACTIVE) {
            throw new BusinessException("Phiên bản " + label(variant) + " đã ngừng bán.");
        }
        if (variant.getStockQuantity() <= 0) {
            throw new BusinessException("Phiên bản " + label(variant) + " đã hết hàng.");
        }
    }

    private String notEnoughStock(ProductVariant variant, int wanted) {
        return "Không đủ tồn kho cho SKU " + variant.getSku() + ": bạn cần " + wanted
                + " nhưng chỉ còn " + variant.getStockQuantity() + " sản phẩm.";
    }

    private String label(ProductVariant variant) {
        return variant.getColor() + " " + variant.getWeightClass() + " " + variant.getGripSize()
                + " (SKU " + variant.getSku() + ")";
    }

    private int maxQuantity(ProductVariant variant) {
        return Math.max(0, Math.min(variant.getStockQuantity(), MAX_QUANTITY_PER_LINE));
    }

    private CartLine toLine(CartItem item, ProductVariant variant) {
        return new CartLine(item, imageOf(variant, primaryImages(List.of(item))), maxQuantity(variant));
    }

    /** Ảnh hiển thị của dòng: ưu tiên ảnh riêng của biến thể, sau đó ảnh đại diện sản phẩm. */
    private String imageOf(ProductVariant variant, Map<Integer, String> imageByProduct) {
        if (variant.getImageUrl() != null && !variant.getImageUrl().isBlank()) {
            return variant.getImageUrl();
        }
        return imageByProduct.getOrDefault(variant.getProduct().getId(), ProductService.NO_IMAGE);
    }

    /** 1 truy vấn cho tất cả dòng: ảnh đại diện của từng sản phẩm có trong giỏ. */
    private Map<Integer, String> primaryImages(List<CartItem> items) {
        List<Integer> productIds = items.stream()
                .map(item -> item.getProductVariant().getProduct().getId())
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> imageByProduct = new HashMap<>();
        for (ProductImage image : productImageRepository
                .findByProductIdInOrderByPrimaryImageDescSortOrderAscIdAsc(productIds)) {
            imageByProduct.putIfAbsent(image.getProduct().getId(), image.getImageUrl());
        }
        return imageByProduct;
    }
}
