package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.BalanceType;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.ShaftFlexibility;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tích hợp danh mục sản phẩm trên CSDL thật (BadminStore): danh sách + tìm kiếm + lọc giá +
 * sắp xếp theo giá (kiểm chứng luôn câu JPQL gộp giá thấp nhất) + phân trang + trang chi tiết.
 *
 * Dữ liệu test được tạo trong @BeforeEach và dọn sạch trong @AfterEach (mã sản phẩm bắt đầu bằng TESTCAT-).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogIntegrationTest {

    private static final String CODE_PREFIX = "TESTCAT-";
    private static final String BRAND_NAME = "Hãng Kiểm Thử";
    private static final String CATEGORY_NAME = "Loại Kiểm Thử";
    private static final String PRODUCT_NAME = "Vợt Test Kiểm Thử";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        cleanTestData();
        product = createProduct(CODE_PREFIX + "001", PRODUCT_NAME, ProductStatus.ACTIVE);
    }

    @AfterEach
    void cleanUp() {
        cleanTestData();
    }

    @Test
    @DisplayName("Khách vãng lai mở được danh mục: thấy tên, giá thấp nhất, hãng và ảnh sản phẩm")
    void catalogIsPublicAndShowsLowestPrice() throws Exception {
        mockMvc.perform(get("/san-pham"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)))
                .andExpect(content().string(containsString(BRAND_NAME)))
                .andExpect(content().string(containsString("1.000.000")))
                .andExpect(content().string(containsString("1.200.000")))   // giá gốc gạch ngang
                .andExpect(content().string(containsString("Giảm giá")));
    }

    @Test
    @DisplayName("Tìm theo từ khoá: khớp thì thấy sản phẩm, không khớp thì hiện trạng thái rỗng")
    void searchByKeyword() throws Exception {
        mockMvc.perform(get("/san-pham").param("keyword", "Test Kiểm Thử"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)));

        mockMvc.perform(get("/san-pham").param("keyword", "khong-bao-gio-ton-tai-9999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có sản phẩm phù hợp")));
    }

    @Test
    @DisplayName("Lọc theo khoảng giá dựa trên giá thấp nhất của biến thể đang bán")
    void filterByPriceRange() throws Exception {
        mockMvc.perform(get("/san-pham").param("minPrice", "900000").param("maxPrice", "1100000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)));

        mockMvc.perform(get("/san-pham").param("minPrice", "5000000").param("maxPrice", "6000000"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(PRODUCT_NAME))));
    }

    @Test
    @DisplayName("Lọc theo hãng và theo loại vợt đều trả về đúng sản phẩm")
    void filterByBrandAndCategory() throws Exception {
        Brand brand = product.getBrand();
        Category category = product.getCategory();

        mockMvc.perform(get("/san-pham").param("brandId", String.valueOf(brand.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)));

        mockMvc.perform(get("/san-pham").param("categoryId", String.valueOf(category.getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)));
    }

    @Test
    @DisplayName("Sắp xếp theo giá tăng dần: sản phẩm rẻ hơn đứng trước")
    void sortByPriceAscending() throws Exception {
        String html = mockMvc.perform(get("/san-pham")
                        .param("keyword", "Test Kiểm Thử")
                        .param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Vợt Test Kiểm Thử chỉ có 1 sản phẩm nên kiểm chứng câu ORDER BY bằng dữ liệu phân trang ở test dưới.
        assertThat(html).contains(PRODUCT_NAME);
    }

    @Test
    @DisplayName("Trang chi tiết: hiện biến thể đang bán kèm giá, giá gốc, tình trạng kho và thông số kỹ thuật")
    void productDetailShowsVariantsAndSpecs() throws Exception {
        mockMvc.perform(get("/san-pham/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)))
                .andExpect(content().string(containsString("TESTCAT-001")))
                .andExpect(content().string(containsString("Đỏ")))
                .andExpect(content().string(containsString("4U")))
                .andExpect(content().string(containsString("1.000.000")))
                .andExpect(content().string(containsString("1.200.000")))
                .andExpect(content().string(containsString("Còn 5")))
                .andExpect(content().string(containsString("Hết hàng")))
                .andExpect(content().string(containsString("Nặng đầu (tấn công)")))
                .andExpect(content().string(containsString("Cứng")))
                // Biến thể đã ngừng bán (INACTIVE) không được lộ ra cho khách
                .andExpect(content().string(not(containsString("Vàng chanh"))));
    }

    @Test
    @DisplayName("Sản phẩm đã ẩn khỏi cửa hàng thì KHÔNG xem được chi tiết (404)")
    void hiddenProductDetailReturns404() throws Exception {
        Product hidden = createProduct(CODE_PREFIX + "002", "Vợt Test Đã Ẩn", ProductStatus.HIDDEN);

        mockMvc.perform(get("/san-pham/{id}", hidden.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tham số lọc sai kiểu (?minPrice=abc) chỉ hiện cảnh báo chứ không trả lỗi 400")
    void invalidFilterValueDoesNotBreakPage() throws Exception {
        mockMvc.perform(get("/san-pham").param("minPrice", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá không hợp lệ")))
                .andExpect(content().string(containsString("badge")));
    }

    @Test
    @DisplayName("Số trang vượt quá số trang thật thì lùi về trang cuối, không hiện trang trắng")
    void outOfRangePageFallsBackToLastPage() throws Exception {
        mockMvc.perform(get("/san-pham").param("keyword", "Test Kiểm Thử").param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PRODUCT_NAME)));
    }

    @Test
    @DisplayName("Sản phẩm không tồn tại trả về 404 (không phải 200 hay 500)")
    void unknownProductReturns404() throws Exception {
        mockMvc.perform(get("/san-pham/{id}", 999_999_999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Phân trang: 13 sản phẩm chia thành 2 trang, trang 2 vẫn giữ từ khoá tìm kiếm")
    void paginationKeepsFilters() throws Exception {
        // 13 sản phẩm khớp từ khoá "Test Phân Trang" ⇒ 12 sản phẩm trang 1, 1 sản phẩm trang 2
        for (int i = 1; i <= 13; i++) {
            createProduct(CODE_PREFIX + String.format("%03d", i + 10),
                    String.format("Vợt Test Phân Trang %02d", i), ProductStatus.ACTIVE,
                    new BigDecimal(String.valueOf(1_000_000 + i * 100_000)));
        }

        mockMvc.perform(get("/san-pham").param("keyword", "Test Phân Trang"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("13 sản phẩm phù hợp")))
                .andExpect(content().string(containsString("Trang 1/2")))
                .andExpect(content().string(containsString("Sau")));

        mockMvc.perform(get("/san-pham").param("keyword", "Test Phân Trang").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trang 2/2")))
                // Liên kết phân trang phải mang theo từ khoá đang tìm (không bị mất bộ lọc khi sang trang)
                .andExpect(content().string(containsString("keyword=Test")));
    }

    @Test
    @DisplayName("Sắp xếp theo giá chạy đúng trên câu truy vấn gộp: rẻ nhất lên đầu, đắt nhất xuống cuối")
    void priceSortOrdersTheWholeResult() throws Exception {
        for (int i = 1; i <= 12; i++) {
            // Giá tăng dần theo số thứ tự: 01 rẻ nhất … 12 đắt nhất
            createProduct(CODE_PREFIX + String.format("%03d", i + 10),
                    String.format("Vợt Test Sắp Xếp %02d", i), ProductStatus.ACTIVE,
                    new BigDecimal(String.valueOf(1_000_000 + i * 100_000)));
        }

        String ascending = mockMvc.perform(get("/san-pham")
                        .param("keyword", "Test Sắp Xếp").param("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ascending.indexOf("Sắp Xếp 01")).isLessThan(ascending.indexOf("Sắp Xếp 02"));
        assertThat(ascending.indexOf("Sắp Xếp 02")).isLessThan(ascending.indexOf("Sắp Xếp 03"));

        String descending = mockMvc.perform(get("/san-pham")
                        .param("keyword", "Test Sắp Xếp").param("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(descending.indexOf("Sắp Xếp 12")).isLessThan(descending.indexOf("Sắp Xếp 11"));
        assertThat(descending.indexOf("Sắp Xếp 11")).isLessThan(descending.indexOf("Sắp Xếp 10"));
    }

    // ===== Hàm hỗ trợ tạo/dọn dữ liệu test =====

    private Product createProduct(String code, String name, ProductStatus status) {
        return createProduct(code, name, status, new BigDecimal("1000000"));
    }

    /**
     * Tạo 1 sản phẩm test kèm 3 biến thể + 1 ảnh đại diện.
     * Lưu ý: bảng product_variants có ràng buộc UNIQUE (product_id, color, weight_class, grip_size)
     * nên mỗi sản phẩm chỉ được tạo biến thể MỘT lần.
     */
    private Product createProduct(String code, String name, ProductStatus status, BigDecimal basePrice) {
        Brand brand = brandRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> BRAND_NAME.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Brand created = new Brand();
                    created.setName(BRAND_NAME);
                    created.setDescription("Hãng dùng cho test tự động");
                    return brandRepository.save(created);
                });

        Category category = categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> CATEGORY_NAME.equals(item.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Category created = new Category();
                    created.setName(CATEGORY_NAME);
                    created.setDescription("Loại vợt dùng cho test tự động");
                    return categoryRepository.save(created);
                });

        Product created = new Product();
        created.setProductCode(code);
        created.setName(name);
        created.setBrand(brand);
        created.setCategory(category);
        created.setStatus(status);
        created.setDescription("Vợt dùng cho kiểm thử tự động.");
        created.setLengthMm(675);
        created.setBalancePointMm(295);
        created.setBalanceType(BalanceType.HEAD_HEAVY);
        created.setShaftFlexibility(ShaftFlexibility.STIFF);
        created.setMaxTensionKg(new BigDecimal("30.0"));
        created.setFrameMaterial("Graphite");
        created.setShaftMaterial("Graphite");
        created = productRepository.save(created);

        createVariants(created, basePrice);

        ProductImage primary = new ProductImage();
        primary.setProduct(created);
        primary.setImageUrl("/images/favicon.svg");
        primary.setPrimaryImage(true);
        primary.setSortOrder(0);
        productImageRepository.save(primary);

        return created;
    }

    /** 3 biến thể: 1 đang giảm giá, 1 hết hàng, 1 đã ngừng bán (không được hiện cho khách). */
    private void createVariants(Product owner, BigDecimal basePrice) {
        ProductVariant discounted = new ProductVariant();
        discounted.setProduct(owner);
        discounted.setSku(owner.getProductCode() + "-A");
        discounted.setColor("Đỏ");
        discounted.setWeightClass("4U");
        discounted.setGripSize("G5");
        discounted.setPrice(basePrice);
        discounted.setOriginalPrice(basePrice.add(new BigDecimal("200000")));
        discounted.setStockQuantity(5);
        discounted.setStatus(VariantStatus.ACTIVE);
        productVariantRepository.save(discounted);

        ProductVariant outOfStock = new ProductVariant();
        outOfStock.setProduct(owner);
        outOfStock.setSku(owner.getProductCode() + "-B");
        outOfStock.setColor("Xanh");
        outOfStock.setWeightClass("3U");
        outOfStock.setGripSize("G5");
        outOfStock.setPrice(basePrice.add(new BigDecimal("1000000")));
        outOfStock.setStockQuantity(0);
        outOfStock.setStatus(VariantStatus.ACTIVE);
        productVariantRepository.save(outOfStock);

        ProductVariant inactive = new ProductVariant();
        inactive.setProduct(owner);
        inactive.setSku(owner.getProductCode() + "-C");
        inactive.setColor("Vàng chanh");
        inactive.setWeightClass("3U");
        inactive.setGripSize("G4");
        inactive.setPrice(basePrice.add(new BigDecimal("500000")));
        inactive.setStockQuantity(9);
        inactive.setStatus(VariantStatus.INACTIVE);
        productVariantRepository.save(inactive);
    }

    private void cleanTestData() {
        List<Product> testProducts = productRepository.findAll().stream()
                .filter(item -> item.getProductCode() != null && item.getProductCode().startsWith(CODE_PREFIX))
                .toList();

        for (Product item : testProducts) {
            productVariantRepository.deleteAll(
                    productVariantRepository.findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(item.getId()));
            productImageRepository.deleteAll(
                    productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(item.getId()));
            productRepository.delete(item);
        }

        brandRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> BRAND_NAME.equals(item.getName()))
                .forEach(brandRepository::delete);
        categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> CATEGORY_NAME.equals(item.getName()))
                .forEach(categoryRepository::delete);
    }
}
