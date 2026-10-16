package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.demo.dto.shop.CompareItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

/**
 * Kiểm thử so sánh sản phẩm (mức Khá): danh sách lưu trong session, tối đa 4 sản phẩm,
 * chỉ nhận sản phẩm đang bán và giữ đúng thứ tự khách đã thêm.
 */
@ExtendWith(MockitoExtension.class)
class CompareServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;

    @InjectMocks
    private CompareService compareService;

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    private void givenIdsInSession(Integer... ids) {
        session.setAttribute(CompareService.SESSION_KEY, new ArrayList<>(List.of(ids)));
    }

    @Test
    @DisplayName("Thêm sản phẩm đang bán vào danh sách so sánh: lưu vào session")
    void add_storesNewProductInSession() {
        when(productRepository.findDetailById(10)).thenReturn(Optional.of(product(10, ProductStatus.ACTIVE)));

        CompareService.AddResult result = compareService.add(session, 10);

        assertThat(result.added()).isTrue();
        assertThat(result.message()).contains("Đã thêm");
        assertThat(compareService.currentIds(session)).containsExactly(10);
        assertThat(compareService.count(session)).isEqualTo(1);
    }

    @Test
    @DisplayName("Thêm trùng sản phẩm: không thêm lần hai, trả về thông báo đã có")
    void add_ignoresDuplicate() {
        when(productRepository.findDetailById(10)).thenReturn(Optional.of(product(10, ProductStatus.ACTIVE)));
        givenIdsInSession(10);

        CompareService.AddResult result = compareService.add(session, 10);

        assertThat(result.added()).isFalse();
        assertThat(result.message()).contains("đã có trong danh sách so sánh");
        assertThat(compareService.currentIds(session)).containsExactly(10);
    }

    @Test
    @DisplayName("Danh sách đã đủ 4 sản phẩm: chặn thêm và hướng dẫn bỏ bớt")
    void add_blocksWhenFull() {
        givenIdsInSession(1, 2, 3, 4);
        when(productRepository.findDetailById(5)).thenReturn(Optional.of(product(5, ProductStatus.ACTIVE)));

        assertThatThrownBy(() -> compareService.add(session, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa 4 sản phẩm");

        assertThat(compareService.currentIds(session)).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("Sản phẩm đang ẩn/ngừng bán: không cho so sánh")
    void add_rejectsInactiveProduct() {
        when(productRepository.findDetailById(10)).thenReturn(Optional.of(product(10, ProductStatus.HIDDEN)));

        assertThatThrownBy(() -> compareService.add(session, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngừng bán");
    }

    @Test
    @DisplayName("Sản phẩm không tồn tại (đã xoá mềm): 404")
    void add_throwsWhenProductMissing() {
        when(productRepository.findDetailById(10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> compareService.add(session, 10))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Bỏ một sản phẩm và xoá toàn bộ danh sách so sánh")
    void removeAndClear() {
        givenIdsInSession(10, 11, 12);

        compareService.remove(session, 11);
        assertThat(compareService.currentIds(session)).containsExactly(10, 12);

        compareService.clear(session);
        assertThat(compareService.currentIds(session)).isEmpty();
    }

    @Test
    @DisplayName("Bảng so sánh: giữ thứ tự đã thêm, tính giá thấp nhất – tồn kho từ biến thể đang bán")
    void compareItems_buildsThreeColumnsInOrder() {
        Product first = product(10, ProductStatus.ACTIVE);
        Product second = product(11, ProductStatus.ACTIVE);
        Product third = product(12, ProductStatus.ACTIVE);
        givenIdsInSession(11, 10, 12);

        when(productRepository.findDetailByIdIn(compareService.currentIds(session)))
                .thenReturn(List.of(first, second, third));
        when(productVariantRepository.findByProductIdIn(compareService.currentIds(session)))
                .thenReturn(List.of(variant(1, first, VariantStatus.ACTIVE, "4690000", 5),
                        variant(2, first, VariantStatus.INACTIVE, "100000", 99),
                        variant(3, second, VariantStatus.ACTIVE, "5000000", 0)));
        ProductImage image = new ProductImage();
        image.setProduct(first);
        image.setImageUrl("/images/astrox.svg");
        when(productImageRepository.findByProductIdInOrderByPrimaryImageDescSortOrderAscIdAsc(
                compareService.currentIds(session))).thenReturn(List.of(image));

        List<CompareItem> items = compareService.compareItems(session);

        assertThat(items).hasSize(3);
        assertThat(items.get(0).getProduct().getId()).isEqualTo(11);
        assertThat(items.get(1).getProduct().getId()).isEqualTo(10);
        assertThat(items.get(2).getProduct().getId()).isEqualTo(12);

        CompareItem outOfStockItem = items.get(0);
        assertThat(outOfStockItem.getMinPrice()).isEqualByComparingTo("5000000");
        assertThat(outOfStockItem.getStockLabel()).isEqualTo("Tạm hết hàng");
        assertThat(outOfStockItem.isAvailable()).isFalse();

        CompareItem firstItem = items.get(1);
        assertThat(firstItem.getMinPrice()).isEqualByComparingTo("4690000");
        assertThat(firstItem.getTotalStock()).isEqualTo(5);
        assertThat(firstItem.getVariantLabel()).isEqualTo("1 phiên bản");
        assertThat(firstItem.getStockQuantityLabel()).isEqualTo("5 chiếc");
        assertThat(firstItem.getThumbnailUrl()).isEqualTo("/images/astrox.svg");

        CompareItem thirdItem = items.get(2);
        assertThat(thirdItem.getMinPrice()).isNull();
        assertThat(thirdItem.getStockLabel()).isEqualTo("Chưa mở bán");
        assertThat(thirdItem.getVariantLabel()).isEqualTo("—");
        assertThat(thirdItem.getLengthLabel()).isEqualTo("—");
        assertThat(thirdItem.getBalanceTypeLabel()).isEqualTo("—");
    }

    private Product product(Integer id, ProductStatus status) {
        Product product = new Product();
        product.setId(id);
        product.setName("Vợt so sánh " + id);
        product.setStatus(status);
        return product;
    }

    private ProductVariant variant(Integer id, Product product, VariantStatus status, String price, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSku("SKU-" + id);
        variant.setStatus(status);
        variant.setPrice(new BigDecimal(price));
        variant.setStockQuantity(stock);
        return variant;
    }
}
