package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.shop.CartLine;
import com.example.demo.dto.shop.CartView;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử nghiệp vụ giỏ hàng bằng Mockito (không cần CSDL):
 * tập trung vào quy tắc validate số lượng và các trường hợp hàng ngừng bán/hết hàng.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Integer USER_ID = 7;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    @DisplayName("Thêm biến thể mới vào giỏ: lưu đúng số lượng và trả về dòng hàng để hiện flash")
    void addToCart_createsNewLine() {
        ProductVariant variant = variant(11, 10);
        when(productVariantRepository.findById(11)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByUserIdAndProductVariantId(USER_ID, 11)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem saved = invocation.getArgument(0);
            saved.setId(100);
            return saved;
        });

        CartLine line = cartService.addToCart(USER_ID, 11, 3);

        assertThat(line.getQuantity()).isEqualTo(3);
        assertThat(line.getLineTotal()).isEqualByComparingTo("4470000");   // 1.490.000 × 3
        assertThat(line.getVariantLabel()).isEqualTo("Xanh đen · 4U · G5");
        assertThat(line.getIssue()).isNull();
        assertThat(line.getImageUrl()).isEqualTo(ProductService.NO_IMAGE); // chưa có ảnh ⇒ dùng ảnh mặc định
    }

    @Test
    @DisplayName("Thêm cùng biến thể lần 2 thì cộng dồn số lượng chứ không tạo dòng mới")
    void addToCart_accumulatesExistingLine() {
        ProductVariant variant = variant(11, 10);
        CartItem existing = item(50, variant, 2);
        when(productVariantRepository.findById(11)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByUserIdAndProductVariantId(USER_ID, 11)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartLine line = cartService.addToCart(USER_ID, 11, 3);

        assertThat(line.getQuantity()).isEqualTo(5);
        assertThat(existing.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Số lượng cũ + mới vượt tồn kho thì chặn và báo rõ SKU thiếu hàng")
    void addToCart_rejectsWhenExceedingStock() {
        ProductVariant variant = variant(11, 10);
        when(productVariantRepository.findById(11))
                .thenReturn(Optional.of(variant));
        when(cartItemRepository.findByUserIdAndProductVariantId(USER_ID, 11))
                .thenReturn(Optional.of(item(50, variant, 9)));

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 11, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không đủ tồn kho cho SKU YX-AX100ZZ-01")
                .hasMessageContaining("chỉ còn 10 sản phẩm");

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Số lượng không hợp lệ (0, âm, quá lớn) bị chặn ngay ở tầng nghiệp vụ")
    void addToCart_rejectsInvalidQuantity() {
        ProductVariant variant = variant(11, 100);
        when(productVariantRepository.findById(11)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 11, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng phải từ 1 trở lên");

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 11, -2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng phải từ 1 trở lên");

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 11, CartService.MAX_QUANTITY_PER_LINE + 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa " + CartService.MAX_QUANTITY_PER_LINE);
    }

    @Test
    @DisplayName("Phiên bản ngừng bán / sản phẩm đã ẩn / hết hàng đều không cho thêm vào giỏ")
    void addToCart_rejectsUnsellableVariant() {
        ProductVariant inactive = variant(11, 5);
        inactive.setStatus(VariantStatus.INACTIVE);
        when(productVariantRepository.findById(11)).thenReturn(Optional.of(inactive));
        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 11, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã ngừng bán");

        ProductVariant hiddenProduct = variant(12, 5);
        hiddenProduct.getProduct().setStatus(ProductStatus.HIDDEN);
        when(productVariantRepository.findById(12)).thenReturn(Optional.of(hiddenProduct));
        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 12, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã ngừng bán");

        ProductVariant empty = variant(13, 0);
        when(productVariantRepository.findById(13)).thenReturn(Optional.of(empty));
        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 13, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hàng");
    }

    @Test
    @DisplayName("Biến thể không tồn tại ⇒ NotFoundException (trang 404)")
    void addToCart_throwsWhenVariantMissing() {
        when(productVariantRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.addToCart(USER_ID, 999, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy phiên bản sản phẩm");
    }

    @Test
    @DisplayName("Sửa số lượng: ghi đè số lượng mới khi còn đủ tồn kho")
    void updateQuantity_overwritesQuantity() {
        ProductVariant variant = variant(11, 10);
        CartItem existing = item(50, variant, 1);
        when(cartItemRepository.findByIdAndUserId(50, USER_ID)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartLine line = cartService.updateQuantity(USER_ID, 50, 4);

        assertThat(line.getQuantity()).isEqualTo(4);
        assertThat(existing.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("Sửa số lượng vượt tồn kho hoặc về 0 đều bị chặn")
    void updateQuantity_validatesQuantity() {
        ProductVariant variant = variant(11, 5);
        when(cartItemRepository.findByIdAndUserId(50, USER_ID))
                .thenReturn(Optional.of(item(50, variant, 1)));

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, 50, 6))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ còn 5 sản phẩm");

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, 50, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng phải từ 1 trở lên");

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dòng giỏ hàng của người khác coi như không tồn tại (không sửa/xoá được)")
    void updateAndRemove_rejectForeignCartItem() {
        when(cartItemRepository.findByIdAndUserId(50, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.updateQuantity(USER_ID, 50, 2))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("giỏ hàng của bạn");

        assertThatThrownBy(() -> cartService.removeItem(USER_ID, 50))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("giỏ hàng của bạn");

        verify(cartItemRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Trang giỏ hàng: tính tạm tính, tổng số lượng và đánh dấu dòng không đặt được")
    void getCart_computesTotalsAndFlags() {
        ProductVariant ok = variant(11, 10);
        CartItem okItem = item(50, ok, 2);

        ProductVariant soldOut = variant(12, 0);
        CartItem soldOutItem = item(51, soldOut, 1);

        ProductVariant notEnough = variant(13, 1);
        CartItem notEnoughItem = item(52, notEnough, 3);

        when(cartItemRepository.findCartByUserId(USER_ID))
                .thenReturn(List.of(okItem, soldOutItem, notEnoughItem));

        CartView cart = cartService.getCart(USER_ID);

        assertThat(cart.getTotalQuantity()).isEqualTo(6);
        assertThat(cart.getSubtotal()).isEqualByComparingTo("8940000");   // 6 sản phẩm × 1.490.000
        assertThat(cart.isHasIssues()).isTrue();
        assertThat(cart.getOrderableLineCount()).isEqualTo(1);
        assertThat(cart.getLines().get(1).getIssue()).isEqualTo("Tạm hết hàng");
        assertThat(cart.getLines().get(2).getIssue()).isEqualTo("Chỉ còn 1 sản phẩm");
    }

    @Test
    @DisplayName("Số sản phẩm cho badge header: cộng dồn số lượng, giỏ trống trả về 0")
    void countItems_sumsQuantity() {
        when(cartItemRepository.sumQuantityByUserId(USER_ID)).thenReturn(4L);
        assertThat(cartService.countItems(USER_ID)).isEqualTo(4);

        when(cartItemRepository.sumQuantityByUserId(USER_ID)).thenReturn(null);
        assertThat(cartService.countItems(USER_ID)).isZero();
    }

    @Test
    @DisplayName("Xoá tất cả: xoá mọi dòng của khách và trả về số dòng đã xoá")
    void clearCart_deletesAllLines() {
        ProductVariant variant = variant(11, 10);
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(item(50, variant, 1), item(51, variant, 2)));

        assertThat(cartService.clearCart(USER_ID)).isEqualTo(2);

        ArgumentCaptor<List<CartItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartItemRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    // ==================== dữ liệu giả ====================

    private ProductVariant variant(int variantId, int stock) {
        Product product = new Product();
        product.setId(1);
        product.setName("Vợt cầu lông Yonex Astrox 100 ZZ");
        product.setProductCode("YX-AX100ZZ");
        product.setStatus(ProductStatus.ACTIVE);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSku("YX-AX100ZZ-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));
        variant.setOriginalPrice(new BigDecimal("1990000"));
        variant.setStockQuantity(stock);
        variant.setStatus(VariantStatus.ACTIVE);
        return variant;
    }

    private CartItem item(int cartItemId, ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.setId(cartItemId);
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return item;
    }
}
