package com.example.demo.service;

import com.example.demo.dto.admin.BulkPriceForm;
import com.example.demo.dto.admin.ProductForm;
import com.example.demo.dto.admin.ProductRow;
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
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quản trị danh mục bán hàng: sản phẩm, biến thể (giá + tồn kho) và bộ ảnh.
 *
 * Nguyên tắc:
 * – xoá sản phẩm là XOÁ MỀM (is_deleted = 1) để không phá dữ liệu đơn hàng cũ;
 * – biến thể đã xuất hiện trong đơn hàng thì KHÔNG cho xoá, chỉ được "ngừng bán";
 * – mọi luật của BadStore.sql (CK/UQ) đều được kiểm tra trước và báo lỗi tiếng Việt dễ hiểu;
 * – ảnh upload đi qua FileStorageService (giới hạn 2 MB, kiểm tra MIME + đổi tên file).
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    /** Số sản phẩm mỗi trang ở khu quản trị. */
    public static final int PAGE_SIZE = 10;

    private static final Set<String> WEIGHT_CLASSES = Set.of("2U", "3U", "4U", "5U");
    private static final Set<String> GRIP_SIZES = Set.of("G3", "G4", "G5", "G6");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal MAX_DISCOUNT_PERCENT = BigDecimal.valueOf(90);

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderItemRepository orderItemRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    // ==================== SẢN PHẨM ====================

    /** Danh sách sản phẩm cho admin: chỉ 3 truy vấn cho cả trang (sản phẩm, biến thể, ảnh). */
    @Transactional(readOnly = true)
    public Page<ProductRow> search(String keyword, Integer brandId, Integer categoryId, ProductStatus status,
                                   int pageNumber) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Product> products = productRepository.searchForAdmin(status, brandId, categoryId, normalizedKeyword,
                PageRequest.of(Math.max(0, pageNumber - 1), PAGE_SIZE));

        List<Integer> productIds = products.getContent().stream().map(Product::getId).toList();

        Map<Integer, List<ProductVariant>> variantsByProduct = productIds.isEmpty() ? Map.of()
                : productVariantRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.groupingBy(variant -> variant.getProduct().getId()));

        Map<Integer, String> thumbnails = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (ProductImage image : productImageRepository
                    .findByProductIdInOrderByPrimaryImageDescSortOrderAscIdAsc(productIds)) {
                thumbnails.putIfAbsent(image.getProduct().getId(), image.getImageUrl());
            }
        }

        return products.map(product -> toRow(product,
                variantsByProduct.getOrDefault(product.getId(), List.of()),
                thumbnails.get(product.getId())));
    }

    @Transactional(readOnly = true)
    public List<Brand> brands() {
        return brandRepository.findByDeletedFalseOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categoryRepository.findByDeletedFalseOrderByNameAsc();
    }

    /** Sản phẩm cho khu quản trị: fetch join sẵn hãng + loại (open-in-view = false). */
    @Transactional(readOnly = true)
    public Product requireProduct(Integer id) {
        return productRepository.findDetailById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm cần xử lý."));
    }

    /** Thêm sản phẩm mới: mã tự viết hoa, không trùng (khớp UX_products_code). */
    @Transactional
    public Product createProduct(ProductForm form) {
        String code = normalizeCode(form.getProductCode());
        if (productRepository.existsByProductCodeAndDeletedFalse(code)) {
            throw new BusinessException("Mã sản phẩm \"" + code + "\" đã tồn tại, bạn đặt mã khác nhé.");
        }
        Product product = new Product();
        product.setProductCode(code);
        product.setDeleted(false);
        applyProductForm(product, form);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Integer id, ProductForm form) {
        Product product = requireProduct(id);
        String code = normalizeCode(form.getProductCode());
        if (!code.equals(product.getProductCode())
                && productRepository.existsByProductCodeAndIdNotAndDeletedFalse(code, id)) {
            throw new BusinessException("Mã sản phẩm \"" + code + "\" đã bị dùng bởi sản phẩm khác.");
        }
        product.setProductCode(code);
        applyProductForm(product, form);
        return productRepository.save(product);
    }

    /** Bật/ẩn sản phẩm: ACTIVE ⇄ HIDDEN (khách chỉ thấy ACTIVE). */
    @Transactional
    public Product toggleProductStatus(Integer id) {
        Product product = requireProduct(id);
        product.setStatus(product.getStatus() == ProductStatus.ACTIVE ? ProductStatus.HIDDEN : ProductStatus.ACTIVE);
        return productRepository.save(product);
    }

    /** Xoá mềm: đặt is_deleted = 1, mọi truy vấn khách/admin khác đều lọc theo cờ này. */
    @Transactional
    public Product softDeleteProduct(Integer id) {
        Product product = requireProduct(id);
        product.setDeleted(true);
        return productRepository.save(product);
    }

    // ==================== BIẾN THỂ ====================

    @Transactional(readOnly = true)
    public List<ProductVariant> variantsOf(Integer productId) {
        requireProduct(productId);
        return productVariantRepository.findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(productId);
    }

    /** Biến thể phải thuộc đúng sản phẩm đang mở – chặn sửa chéo sản phẩm qua URL. */
    @Transactional(readOnly = true)
    public ProductVariant requireVariantOf(Integer productId, Integer variantId) {
        return requireVariant(productId, variantId);
    }

    @Transactional
    public ProductVariant createVariant(Integer productId, VariantForm form) {
        // Validate dữ liệu form TRƯỚC khi truy vấn sản phẩm: dữ liệu sai không cần chạm CSDL.
        ProductVariant variant = new ProductVariant();
        variant.setSku(normalizeCode(form.getSku()));
        applyVariantForm(variant, form);
        variant.setProduct(requireProduct(productId));
        requireFreeSku(variant);
        requireFreeCombination(variant, false);
        return productVariantRepository.save(variant);
    }

    @Transactional
    public ProductVariant updateVariant(Integer productId, Integer variantId, VariantForm form) {
        ProductVariant variant = requireVariant(productId, variantId);
        variant.setSku(normalizeCode(form.getSku()));
        applyVariantForm(variant, form);
        requireFreeSku(variant);
        requireFreeCombination(variant, true);
        return productVariantRepository.save(variant);
    }

    /**
     * Ngừng bán / bán lại biến thể.
     */
    @Transactional
    public ProductVariant toggleVariantStatus(Integer productId, Integer variantId) {
        ProductVariant variant = requireVariant(productId, variantId);
        variant.setStatus(variant.getStatus() == VariantStatus.ACTIVE ? VariantStatus.INACTIVE : VariantStatus.ACTIVE);
        return productVariantRepository.save(variant);
    }

    /**
     * Xoá biến thể: chỉ cho xoá những biến thể CHƯA từng vào đơn hàng
     * (order_items giữ lịch sử theo FK) – còn lại hướng dẫn admin chuyển sang "Ngừng bán".
     */
    @Transactional
    public void deleteVariant(Integer productId, Integer variantId) {
        ProductVariant variant = requireVariant(productId, variantId);
        if (orderItemRepository.existsByProductVariantId(variantId)) {
            throw new BusinessException("Biến thể " + variant.getSku()
                    + " đã có trong đơn hàng nên không thể xoá. Hãy chuyển sang trạng thái \"Ngừng bán\".");
        }
        productVariantRepository.delete(variant);
    }

    // ==================== CẬP NHẬT GIÁ HÀNG LOẠT ====================

    /**
     * Cập nhật giá cho NHIỀU biến thể được tích chọn (mục 13 – mức Khá, thay cho bảng promotion):
     * – DISCOUNT: lấy giá gạch ngang (hoặc giá đang bán) làm giá gốc, đặt giá bán = giá gốc giảm %;
     * – CLEAR_DISCOUNT: trả giá bán về giá gạch ngang rồi xoá giá gạch ngang.
     * Chỉ tác động lên biến thể THUỘC sản phẩm đang mở và giữ ràng buộc CK_variants_original (original ≥ price).
     *
     * @return số biến thể thực sự được cập nhật
     */
    @Transactional
    public int updatePricesBulk(Integer productId, BulkPriceForm form) {
        requireProduct(productId);

        List<Integer> variantIds = form.getVariantIds() == null ? List.of() : form.getVariantIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (variantIds.isEmpty()) {
            throw new BusinessException("Bạn hãy tích chọn ít nhất một biến thể để cập nhật giá.");
        }

        BulkPriceMode mode = form.getMode() == null ? BulkPriceMode.DISCOUNT : form.getMode();
        BigDecimal percent = form.getPercent() == null ? BigDecimal.ZERO : form.getPercent();
        if (mode == BulkPriceMode.DISCOUNT
                && (percent.signum() <= 0 || percent.compareTo(MAX_DISCOUNT_PERCENT) > 0)) {
            throw new BusinessException("Phần trăm giảm giá phải lớn hơn 0 và không quá "
                    + MAX_DISCOUNT_PERCENT.toPlainString() + "%. Bạn nhập lại giúp nhé.");
        }

        int updated = 0;
        for (Integer variantId : variantIds) {
            // findByIdAndProductId: id lạ / biến thể của sản phẩm khác thì bỏ qua, không lộ dữ liệu
            ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                    .orElse(null);
            if (variant == null) {
                continue;
            }

            if (mode == BulkPriceMode.DISCOUNT) {
                BigDecimal base = variant.getOriginalPrice() != null ? variant.getOriginalPrice() : variant.getPrice();
                BigDecimal newPrice = base.multiply(BigDecimal.valueOf(100).subtract(percent))
                        .divide(HUNDRED, 0, RoundingMode.HALF_UP);
                newPrice = roundToThousand(newPrice);
                if (newPrice.compareTo(base) >= 0) {
                    continue;   // làm tròn lên bằng giá gốc ⇒ không còn ý nghĩa giảm giá
                }
                variant.setOriginalPrice(base);
                variant.setPrice(newPrice);
            } else {
                if (variant.getOriginalPrice() == null) {
                    continue;   // không có giá gạch ngang thì không có gì để bỏ
                }
                variant.setPrice(variant.getOriginalPrice());
                variant.setOriginalPrice(null);
            }

            productVariantRepository.save(variant);
            updated++;
        }

        if (updated == 0) {
            throw new BusinessException("Không có biến thể nào cần cập nhật: "
                    + (mode == BulkPriceMode.DISCOUNT
                    ? "phần trăm giảm quá nhỏ (giá làm tròn về giá cũ)."
                    : "các biến thể đã chọn chưa có giá gạch ngang."));
        }
        return updated;
    }

    /** Làm tròn giá về mệnh giá 1.000 ₫ cho dễ đọc. */
    private BigDecimal roundToThousand(BigDecimal value) {
        return value.divide(THOUSAND, 0, RoundingMode.HALF_UP).multiply(THOUSAND);
    }

    // ==================== ẢNH SẢN PHẨM ====================

    @Transactional(readOnly = true)
    public List<ProductImage> imagesOf(Integer productId) {
        requireProduct(productId);
        return productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
    }

    /** Tải ảnh lên: ảnh đầu tiên tự thành ảnh chính; nếu chọn "đặt làm ảnh chính" thì thay ảnh chính cũ. */
    @Transactional
    public ProductImage addImage(Integer productId, MultipartFile file, boolean makePrimary) {
        Product product = requireProduct(productId);
        String imageUrl = fileStorageService.storeImage(file);

        List<ProductImage> existing = productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        boolean firstImage = existing.isEmpty();

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(imageUrl);
        image.setSortOrder(existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1);
        image.setPrimaryImage(firstImage || makePrimary);
        ProductImage saved = productImageRepository.save(image);

        if (!firstImage && makePrimary) {
            // Bỏ cờ ảnh chính cũ SAU khi đã ghi ảnh mới (unique index UX_product_images_primary chỉ cho 1 dòng).
            productImageRepository.findFirstByProductIdAndPrimaryImageTrue(productId)
                    .filter(current -> !current.getId().equals(saved.getId()))
                    .ifPresent(current -> {
                        current.setPrimaryImage(false);
                        productImageRepository.save(current);
                    });
        }
        return saved;
    }

    /** Đặt ảnh làm ảnh chính: tắt ảnh chính cũ trước rồi bật ảnh mới. */
    @Transactional
    public ProductImage makePrimary(Integer productId, Integer imageId) {
        ProductImage image = requireImage(productId, imageId);
        if (image.isPrimaryImage()) {
            return image;
        }
        productImageRepository.findFirstByProductIdAndPrimaryImageTrue(productId).ifPresent(current -> {
            current.setPrimaryImage(false);
            productImageRepository.saveAndFlush(current);
        });
        image.setPrimaryImage(true);
        return productImageRepository.save(image);
    }

    /** Gỡ ảnh: xoá bản ghi + file đã upload; nếu là ảnh chính thì ảnh kế tiếp lên thay. */
    @Transactional
    public void deleteImage(Integer productId, Integer imageId) {
        ProductImage image = requireImage(productId, imageId);
        boolean wasPrimary = image.isPrimaryImage();
        String imageUrl = image.getImageUrl();

        productImageRepository.delete(image);
        productImageRepository.flush();   // nhả unique index trước khi gán ảnh chính mới

        if (wasPrimary) {
            productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().findFirst()
                    .ifPresent(next -> {
                        next.setPrimaryImage(true);
                        productImageRepository.save(next);
                    });
        }
        fileStorageService.deleteIfUploaded(imageUrl);
    }

    // ==================== HÀM NỘI BỘ ====================

    private ProductRow toRow(Product product, List<ProductVariant> variants, String thumbnailUrl) {
        List<ProductVariant> sellable = variants.stream()
                .filter(variant -> variant.getStatus() == VariantStatus.ACTIVE)
                .toList();
        BigDecimal minPrice = sellable.stream()
                .map(ProductVariant::getPrice)
                .min(Comparator.naturalOrder())
                .orElse(null);
        long totalStock = sellable.stream().mapToLong(ProductVariant::getStockQuantity).sum();
        return new ProductRow(product, minPrice, totalStock, sellable.size(), thumbnailUrl);
    }

    private String normalizeCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || code.length() > 50) {
            throw new BusinessException("Mã (SKU/mã sản phẩm) phải có từ 1 đến 50 ký tự.");
        }
        return code;
    }

    private void applyProductForm(Product product, ProductForm form) {
        Brand brand = brandRepository.findByIdAndDeletedFalse(form.getBrandId())
                .orElseThrow(() -> new BusinessException("Hãng sản xuất không tồn tại hoặc đã bị xoá."));
        Category category = categoryRepository.findByIdAndDeletedFalse(form.getCategoryId())
                .orElseThrow(() -> new BusinessException("Loại (lối chơi) không tồn tại hoặc đã bị xoá."));

        product.setName(form.getName() == null ? "" : form.getName().trim());
        product.setBrand(brand);
        product.setCategory(category);
        product.setDescription(trimToNull(form.getDescription()));
        product.setLengthMm(form.getLengthMm());
        product.setBalancePointMm(form.getBalancePointMm());
        product.setBalanceType(form.getBalanceType());
        product.setShaftFlexibility(form.getShaftFlexibility());
        product.setMaxTensionKg(form.getMaxTensionKg());
        product.setFrameMaterial(trimToNull(form.getFrameMaterial()));
        product.setShaftMaterial(trimToNull(form.getShaftMaterial()));
        product.setStatus(form.getStatus() == null ? ProductStatus.ACTIVE : form.getStatus());
    }

    /** Chuẩn hoá + kiểm tra luật của biến thể rồi gán vào entity. */
    private void applyVariantForm(ProductVariant variant, VariantForm form) {
        String color = form.getColor() == null ? "" : form.getColor().trim();
        String weightClass = form.getWeightClass() == null ? "" : form.getWeightClass().trim().toUpperCase(Locale.ROOT);
        String gripSize = form.getGripSize() == null ? "" : form.getGripSize().trim().toUpperCase(Locale.ROOT);

        if (color.isEmpty()) {
            throw new BusinessException("Vui lòng nhập màu sắc của biến thể.");
        }
        if (!WEIGHT_CLASSES.contains(weightClass)) {
            throw new BusinessException("Trọng lượng chỉ nhận 2U, 3U, 4U hoặc 5U.");
        }
        if (!GRIP_SIZES.contains(gripSize)) {
            throw new BusinessException("Cỡ cán chỉ nhận G3, G4, G5 hoặc G6.");
        }

        BigDecimal price = form.getPrice();
        if (price == null || price.signum() < 0) {
            throw new BusinessException("Giá bán không được âm.");
        }
        price = price.setScale(0, RoundingMode.HALF_UP);

        BigDecimal originalPrice = form.getOriginalPrice();
        if (originalPrice != null) {
            originalPrice = originalPrice.setScale(0, RoundingMode.HALF_UP);
            if (originalPrice.signum() == 0) {
                originalPrice = null;
            } else if (originalPrice.compareTo(price) < 0) {
                throw new BusinessException("Giá gạch ngang phải lớn hơn hoặc bằng giá bán.");
            }
        }
        if (form.getStockQuantity() < 0) {
            throw new BusinessException("Tồn kho không được âm.");
        }

        variant.setColor(color);
        variant.setWeightClass(weightClass);
        variant.setGripSize(gripSize);
        variant.setImageUrl(trimToNull(form.getImageUrl()));
        variant.setPrice(price);
        variant.setOriginalPrice(originalPrice);
        variant.setStockQuantity(form.getStockQuantity());
        variant.setStatus(form.getStatus() == null ? VariantStatus.ACTIVE : form.getStatus());
    }

    private void requireFreeSku(ProductVariant variant) {
        boolean taken = variant.getId() == null
                ? productVariantRepository.existsBySku(variant.getSku())
                : productVariantRepository.existsBySkuAndIdNot(variant.getSku(), variant.getId());
        if (taken) {
            throw new BusinessException("SKU \"" + variant.getSku() + "\" đã tồn tại ở một biến thể khác.");
        }
    }

    private void requireFreeCombination(ProductVariant variant, boolean updating) {
        Integer productId = variant.getProduct().getId();
        boolean duplicated = updating
                ? productVariantRepository.existsByProductIdAndColorAndWeightClassAndGripSizeAndIdNot(
                        productId, variant.getColor(), variant.getWeightClass(), variant.getGripSize(), variant.getId())
                : productVariantRepository.existsByProductIdAndColorAndWeightClassAndGripSize(
                        productId, variant.getColor(), variant.getWeightClass(), variant.getGripSize());
        if (duplicated) {
            throw new BusinessException("Sản phẩm đã có biến thể " + variant.getColor() + " – "
                    + variant.getWeightClass() + " – " + variant.getGripSize()
                    + ". Mỗi tổ hợp màu/trọng lượng/cỡ cán chỉ được tạo một lần.");
        }
    }

    private ProductVariant requireVariant(Integer productId, Integer variantId) {
        return productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy biến thể cần xử lý."));
    }

    private ProductImage requireImage(Integer productId, Integer imageId) {
        return productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ảnh cần xử lý."));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
