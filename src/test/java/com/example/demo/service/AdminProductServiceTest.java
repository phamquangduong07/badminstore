package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.admin.BulkPriceForm;
import com.example.demo.dto.admin.ProductForm;
import com.example.demo.dto.admin.VariantForm;
import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.BulkPriceMode;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Kiểm thử nghiệp vụ quản trị danh mục: mã không trùng, xoá mềm, luật biến thể
 * (SKU, tổ hợp màu/trọng lượng/cỡ cán, giá gạch ngang) và quản lý ảnh chính.
 */
@ExtendWith(MockitoExtension.class)
class AdminProductServiceTest {

    private static final Integer PRODUCT_ID = 124;
    private static final Integer VARIANT_ID = 370;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AdminProductService adminProductService;

    // ==================== SẢN PHẨM ====================

    @Test
    @DisplayName("Thêm sản phẩm: mã được viết hoa và gán đúng hãng/loại")
    void createProduct_normalizesCode() {
        when(brandRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(brand()));
        when(categoryRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.of(category()));
        when(productRepository.existsByProductCodeAndDeletedFalse("YX-NEW")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = adminProductService.createProduct(productForm("  yx-new  "));

        assertThat(created.getProductCode()).isEqualTo("YX-NEW");
        assertThat(created.getName()).isEqualTo("Vợt thử nghiệm");
        assertThat(created.getBrand().getId()).isEqualTo(1);
        assertThat(created.getCategory().getId()).isEqualTo(2);
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Thêm sản phẩm: mã đã tồn tại ⇒ báo lỗi, không ghi DB")
    void createProduct_rejectsDuplicateCode() {
        when(productRepository.existsByProductCodeAndDeletedFalse("YX-NEW")).thenReturn(true);

        assertThatThrownBy(() -> adminProductService.createProduct(productForm("YX-NEW")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thêm sản phẩm: hãng đã bị xoá ⇒ báo lỗi tiếng Việt")
    void createProduct_rejectsMissingBrand() {
        when(brandRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProductService.createProduct(productForm("YX-NEW")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Hãng sản xuất không tồn tại");
    }

    @Test
    @DisplayName("Ẩn/hiện sản phẩm: ACTIVE ⇄ HIDDEN")
    void toggleProductStatus_switchesBothWays() {
        Product product = product(ProductStatus.ACTIVE);
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(adminProductService.toggleProductStatus(PRODUCT_ID).getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(adminProductService.toggleProductStatus(PRODUCT_ID).getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("Xoá sản phẩm là XOÁ MỀM: chỉ bật cờ is_deleted, không xoá dữ liệu")
    void softDeleteProduct_setsFlagOnly() {
        Product product = product(ProductStatus.ACTIVE);
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product deleted = adminProductService.softDeleteProduct(PRODUCT_ID);

        assertThat(deleted.isDeleted()).isTrue();
        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    @DisplayName("Sản phẩm không tồn tại (đã xoá mềm) ⇒ 404")
    void requireProduct_throwsWhenMissing() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminProductService.requireProduct(PRODUCT_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy sản phẩm");
    }

    // ==================== BIẾN THỂ ====================

    @Test
    @DisplayName("Thêm biến thể: SKU trùng ⇒ chặn trước khi ghi DB")
    void createVariant_rejectsDuplicateSku() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.existsBySku("YX-AX100ZZ-01")).thenReturn(true);

        assertThatThrownBy(() -> adminProductService.createVariant(PRODUCT_ID, variantForm("yx-ax100zz-01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại ở một biến thể khác");

        verify(productVariantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thêm biến thể: trùng tổ hợp màu/trọng lượng/cỡ cán ⇒ chặn (UQ_product_variant)")
    void createVariant_rejectsDuplicateCombination() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.existsBySku("YX-AX100ZZ-01")).thenReturn(false);
        when(productVariantRepository.existsByProductIdAndColorAndWeightClassAndGripSize(
                PRODUCT_ID, "Xanh đen", "4U", "G5")).thenReturn(true);

        assertThatThrownBy(() -> adminProductService.createVariant(PRODUCT_ID, variantForm("YX-AX100ZZ-01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ được tạo một lần");

        verify(productVariantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giá gạch ngang thấp hơn giá bán ⇒ chặn (CK_variants_original)")
    void createVariant_rejectsOriginalPriceBelowPrice() {
        VariantForm form = variantForm("YX-AX100ZZ-01");
        form.setPrice(new BigDecimal("5000000"));
        form.setOriginalPrice(new BigDecimal("4900000"));

        assertThatThrownBy(() -> adminProductService.createVariant(PRODUCT_ID, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giá gạch ngang phải lớn hơn hoặc bằng giá bán");

        verify(productVariantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Trọng lượng/cỡ cán ngoài danh mục ⇒ chặn (CK_variants_weight, CK_variants_grip)")
    void createVariant_rejectsInvalidWeightAndGrip() {
        VariantForm badWeight = variantForm("YX-AX100ZZ-01");
        badWeight.setWeightClass("6U");
        assertThatThrownBy(() -> adminProductService.createVariant(PRODUCT_ID, badWeight))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Trọng lượng chỉ nhận");

        VariantForm badGrip = variantForm("YX-AX100ZZ-01");
        badGrip.setGripSize("G7");
        assertThatThrownBy(() -> adminProductService.createVariant(PRODUCT_ID, badGrip))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cỡ cán chỉ nhận");
    }

    @Test
    @DisplayName("Thêm biến thể: giá gốc bằng 0 được coi như không nhập, giá chuẩn hoá về số nguyên đồng")
    void createVariant_normalizesPrices() {
        VariantForm form = variantForm("YX-AX100ZZ-01");
        form.setPrice(new BigDecimal("4690000.4"));
        form.setOriginalPrice(BigDecimal.ZERO);
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductVariant created = adminProductService.createVariant(PRODUCT_ID, form);

        assertThat(created.getPrice()).isEqualByComparingTo("4690000");
        assertThat(created.getOriginalPrice()).isNull();
        assertThat(created.getWeightClass()).isEqualTo("4U");
        assertThat(created.getStatus()).isEqualTo(VariantStatus.ACTIVE);
    }

    @Test
    @DisplayName("Thêm biến thể: lưu ảnh upload và gán vào image_url của biến thể")
    void createVariant_storesUploadedImage() {
        VariantForm form = variantForm("YX-AX100ZZ-01");
        form.setImageFile(upload("bien-the.png"));

        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.existsBySku("YX-AX100ZZ-01")).thenReturn(false);
        when(productVariantRepository.existsByProductIdAndColorAndWeightClassAndGripSize(
                PRODUCT_ID, "Xanh đen", "4U", "G5")).thenReturn(false);
        when(fileStorageService.storeImage(any())).thenReturn("/uploads/bien-the.png");
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductVariant created = adminProductService.createVariant(PRODUCT_ID, form);

        assertThat(created.getImageUrl()).isEqualTo("/uploads/bien-the.png");
    }

    @Test
    @DisplayName("Sửa biến thể: tích gỡ ảnh ⇒ image_url về null và xoá file upload cũ")
    void updateVariant_removesUploadedImage() {
        ProductVariant variant = variant();
        variant.setImageUrl("/uploads/anh-cu.png");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID))
                .thenReturn(Optional.of(variant));
        when(productVariantRepository.existsBySkuAndIdNot("YX-AX100ZZ-01", VARIANT_ID)).thenReturn(false);
        when(productVariantRepository.existsByProductIdAndColorAndWeightClassAndGripSizeAndIdNot(
                PRODUCT_ID, "Xanh đen", "4U", "G5", VARIANT_ID)).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VariantForm form = variantForm("YX-AX100ZZ-01");
        form.setImageUrl("/uploads/anh-cu.png");
        form.setRemoveImage(true);

        ProductVariant updated = adminProductService.updateVariant(PRODUCT_ID, VARIANT_ID, form);

        assertThat(updated.getImageUrl()).isNull();
        verify(fileStorageService).deleteIfUploaded("/uploads/anh-cu.png");
    }

    @Test
    @DisplayName("Xoá biến thể đã có trong đơn hàng ⇒ chặn và hướng dẫn chuyển sang Ngừng bán")
    void deleteVariant_blockedWhenUsedInOrder() {
        ProductVariant variant = variant();
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(orderItemRepository.existsByProductVariantId(VARIANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> adminProductService.deleteVariant(PRODUCT_ID, VARIANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngừng bán");

        verify(productVariantRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xoá biến thể chưa từng vào đơn ⇒ xoá thật")
    void deleteVariant_deletesWhenNeverOrdered() {
        ProductVariant variant = variant();
        variant.setImageUrl("/uploads/anh-bien-the.png");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(orderItemRepository.existsByProductVariantId(VARIANT_ID)).thenReturn(false);

        adminProductService.deleteVariant(PRODUCT_ID, VARIANT_ID);

        verify(productVariantRepository).delete(variant);
        verify(fileStorageService).deleteIfUploaded("/uploads/anh-bien-the.png");
    }

    @Test
    @DisplayName("Xoá biến thể: ảnh còn được bộ ảnh sản phẩm dùng ⇒ giữ file, chỉ xoá bản ghi")
    void deleteVariant_keepsImageSharedWithProductGallery() {
        ProductVariant variant = variant();
        variant.setImageUrl("/uploads/anh-dung-chung.png");
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(orderItemRepository.existsByProductVariantId(VARIANT_ID)).thenReturn(false);
        when(productImageRepository.existsByImageUrl("/uploads/anh-dung-chung.png")).thenReturn(true);

        adminProductService.deleteVariant(PRODUCT_ID, VARIANT_ID);

        verify(productVariantRepository).delete(variant);
        verify(fileStorageService, never()).deleteIfUploaded(any());
    }

    // ==================== CẬP NHẬT GIÁ HÀNG LOẠT ====================

    @Test
    @DisplayName("Giảm giá hàng loạt: giá cũ thành giá gạch ngang, giá bán mới làm tròn nghìn")
    void updatePricesBulk_discountsSelectedVariants() {
        ProductVariant plain = variant();
        ProductVariant alreadyDiscounted = variant();
        alreadyDiscounted.setId(371);
        alreadyDiscounted.setPrice(new BigDecimal("5290000"));
        alreadyDiscounted.setOriginalPrice(new BigDecimal("5590000"));

        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(plain));
        when(productVariantRepository.findByIdAndProductId(371, PRODUCT_ID)).thenReturn(Optional.of(alreadyDiscounted));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BulkPriceForm form = new BulkPriceForm();
        form.setVariantIds(List.of(VARIANT_ID, 371));
        form.setPercent(new BigDecimal("10"));

        assertThat(adminProductService.updatePricesBulk(PRODUCT_ID, form)).isEqualTo(2);

        assertThat(plain.getOriginalPrice()).isEqualByComparingTo("4690000");
        assertThat(plain.getPrice()).isEqualByComparingTo("4221000");
        // Biến thể đang giảm giá thì tính tiếp trên giá gạch ngang cũ để không mất mốc giá gốc
        assertThat(alreadyDiscounted.getOriginalPrice()).isEqualByComparingTo("5590000");
        assertThat(alreadyDiscounted.getPrice()).isEqualByComparingTo("5031000");
    }

    @Test
    @DisplayName("Bỏ giảm giá hàng loạt: giá bán trả về giá gạch ngang và xoá giá gạch ngang")
    void updatePricesBulk_clearsDiscount() {
        ProductVariant variant = variant();
        variant.setOriginalPrice(new BigDecimal("5290000"));
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.findByIdAndProductId(VARIANT_ID, PRODUCT_ID)).thenReturn(Optional.of(variant));
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BulkPriceForm form = new BulkPriceForm();
        form.setVariantIds(List.of(VARIANT_ID));
        form.setMode(BulkPriceMode.CLEAR_DISCOUNT);

        assertThat(adminProductService.updatePricesBulk(PRODUCT_ID, form)).isEqualTo(1);
        assertThat(variant.getPrice()).isEqualByComparingTo("5290000");
        assertThat(variant.getOriginalPrice()).isNull();
    }

    @Test
    @DisplayName("Cập nhật giá hàng loạt: chưa tích biến thể hoặc % ngoài 1–90 đều bị chặn trước khi lưu")
    void updatePricesBulk_validatesInput() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));

        BulkPriceForm empty = new BulkPriceForm();
        assertThatThrownBy(() -> adminProductService.updatePricesBulk(PRODUCT_ID, empty))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tích chọn ít nhất một biến thể");

        BulkPriceForm zeroPercent = new BulkPriceForm();
        zeroPercent.setVariantIds(List.of(VARIANT_ID));
        zeroPercent.setPercent(BigDecimal.ZERO);
        assertThatThrownBy(() -> adminProductService.updatePricesBulk(PRODUCT_ID, zeroPercent))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không quá 90%");

        BulkPriceForm tooBig = new BulkPriceForm();
        tooBig.setVariantIds(List.of(VARIANT_ID));
        tooBig.setPercent(new BigDecimal("95"));
        assertThatThrownBy(() -> adminProductService.updatePricesBulk(PRODUCT_ID, tooBig))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không quá 90%");

        verify(productVariantRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật giá hàng loạt: id biến thể của sản phẩm khác bị bỏ qua, không đổi gì thì báo rõ")
    void updatePricesBulk_ignoresForeignVariants() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(productVariantRepository.findByIdAndProductId(999, PRODUCT_ID)).thenReturn(Optional.empty());

        BulkPriceForm form = new BulkPriceForm();
        form.setVariantIds(List.of(999));

        assertThatThrownBy(() -> adminProductService.updatePricesBulk(PRODUCT_ID, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không có biến thể nào cần cập nhật");

        verify(productVariantRepository, never()).save(any());
    }

    // ==================== ẢNH ====================

    @Test
    @DisplayName("Ảnh đầu tiên của sản phẩm luôn thành ảnh chính")
    void addImage_firstImageBecomesPrimary() {
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(fileStorageService.storeImage(any())).thenReturn("/uploads/anh1.png");
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)).thenReturn(List.of());
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductImage image = adminProductService.addImage(PRODUCT_ID, upload("vuot.png"), false);

        assertThat(image.getImageUrl()).isEqualTo("/uploads/anh1.png");
        assertThat(image.isPrimaryImage()).isTrue();
        assertThat(image.getSortOrder()).isZero();
    }

    @Test
    @DisplayName("Tải ảnh mới và chọn làm ảnh chính ⇒ tắt cờ ảnh chính cũ")
    void addImage_withMakePrimary_turnsOffOldPrimary() {
        ProductImage oldPrimary = image(1, "/uploads/anh-cu.png", true);
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product(ProductStatus.ACTIVE)));
        when(fileStorageService.storeImage(any())).thenReturn("/uploads/anh-moi.png");
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)).thenReturn(List.of(oldPrimary));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.findFirstByProductIdAndPrimaryImageTrue(PRODUCT_ID))
                .thenReturn(Optional.of(oldPrimary));

        ProductImage added = adminProductService.addImage(PRODUCT_ID, upload("moi.png"), true);

        assertThat(added.isPrimaryImage()).isTrue();
        assertThat(added.getSortOrder()).isEqualTo(1);
        assertThat(oldPrimary.isPrimaryImage()).isFalse();
    }

    @Test
    @DisplayName("Đặt ảnh chính: ảnh cũ bị hạ cờ trước, ảnh mới được bật")
    void makePrimary_switchesFlag() {
        ProductImage current = image(1, "/uploads/a.png", true);
        ProductImage target = image(2, "/uploads/b.png", false);
        when(productImageRepository.findByIdAndProductId(2, PRODUCT_ID)).thenReturn(Optional.of(target));
        when(productImageRepository.findFirstByProductIdAndPrimaryImageTrue(PRODUCT_ID)).thenReturn(Optional.of(current));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductImage result = adminProductService.makePrimary(PRODUCT_ID, 2);

        assertThat(result.isPrimaryImage()).isTrue();
        assertThat(current.isPrimaryImage()).isFalse();
        verify(productImageRepository).saveAndFlush(current);
        verify(productImageRepository).save(target);
    }

    @Test
    @DisplayName("Gỡ ảnh chính ⇒ ảnh kế tiếp lên làm ảnh chính và file upload bị xoá")
    void deleteImage_promotesNextImage() {
        ProductImage primary = image(1, "/uploads/a.png", true);
        ProductImage next = image(2, "/uploads/b.png", false);
        when(productImageRepository.findByIdAndProductId(1, PRODUCT_ID)).thenReturn(Optional.of(primary));
        when(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(PRODUCT_ID)).thenReturn(List.of(next));
        when(productImageRepository.save(any(ProductImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        adminProductService.deleteImage(PRODUCT_ID, 1);

        assertThat(next.isPrimaryImage()).isTrue();
        verify(productImageRepository).delete(primary);
        verify(fileStorageService).deleteIfUploaded("/uploads/a.png");
    }

    // ==================== DỮ LIỆU GIẢ ====================

    private ProductForm productForm(String code) {
        ProductForm form = new ProductForm();
        form.setProductCode(code);
        form.setName("Vợt thử nghiệm");
        form.setBrandId(1);
        form.setCategoryId(2);
        form.setLengthMm(675);
        form.setBalancePointMm(305);
        form.setStatus(ProductStatus.ACTIVE);
        return form;
    }

    private VariantForm variantForm(String sku) {
        VariantForm form = new VariantForm();
        form.setSku(sku);
        form.setColor("Xanh đen");
        form.setWeightClass("4U");
        form.setGripSize("G5");
        form.setPrice(new BigDecimal("4690000"));
        form.setStockQuantity(12);
        form.setStatus(VariantStatus.ACTIVE);
        return form;
    }

    private Product product(ProductStatus status) {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProductCode("YX-AX100ZZ");
        product.setName("Vợt cầu lông Yonex Astrox 100 ZZ");
        product.setBrand(brand());
        product.setCategory(category());
        product.setStatus(status);
        return product;
    }

    private ProductVariant variant() {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setProduct(product(ProductStatus.ACTIVE));
        variant.setSku("YX-AX100ZZ-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("4690000"));
        variant.setStockQuantity(12);
        variant.setStatus(VariantStatus.ACTIVE);
        return variant;
    }

    private ProductImage image(String fileName) {
        return image(null, "/uploads/" + fileName, false);
    }

    private MockMultipartFile upload(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", "PNG-GIA-LAP".getBytes(StandardCharsets.UTF_8));
    }

    private ProductImage image(Integer id, String url, boolean primary) {
        ProductImage image = new ProductImage();
        image.setId(id);
        image.setImageUrl(url);
        image.setPrimaryImage(primary);
        return image;
    }

    private Brand brand() {
        Brand brand = new Brand();
        brand.setId(1);
        brand.setName("Yonex");
        return brand;
    }

    private Category category() {
        Category category = new Category();
        category.setId(2);
        category.setName("Tấn công");
        return category;
    }
}
