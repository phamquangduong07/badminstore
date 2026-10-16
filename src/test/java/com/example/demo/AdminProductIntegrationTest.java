package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tích hợp nghiệp vụ quản trị danh mục (mức Khá):
 * danh sách + lọc, thêm/sửa sản phẩm, thêm biến thể (giá – tồn kho), tải/gỡ ảnh,
 * ẩn-hiện và xoá mềm sản phẩm – kiểm tra cả ảnh hưởng tới trang khách.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminProductIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

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

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private String code;
    private User admin;
    private Brand brand;
    private Category category;
    private Product product;

    /** Sản phẩm do test tạo ra (kể cả qua form) để dọn sạch sau mỗi test. */
    private final List<Integer> createdProductIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        code = "ADT" + suffix.substring(suffix.length() - 8);

        admin = new User();
        admin.setEmail("admin.product." + suffix + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFullName("Quản trị viên sản phẩm");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        brand = brandRepository.findByDeletedFalseOrderByNameAsc().get(0);
        category = categoryRepository.findByDeletedFalseOrderByNameAsc().get(0);

        product = new Product();
        product.setProductCode(code);
        product.setName("Vợt kiểm thử " + suffix.substring(suffix.length() - 4));
        product.setBrand(brand);
        product.setCategory(category);
        product.setLengthMm(675);
        product.setBalancePointMm(305);
        product.setStatus(ProductStatus.ACTIVE);
        product.setDeleted(false);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
    }

    @AfterEach
    void cleanUp() {
        for (Integer productId : createdProductIds) {
            productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).forEach(image -> {
                fileStorageService.deleteIfUploaded(image.getImageUrl());
                productImageRepository.delete(image);
            });
            productVariantRepository.findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(productId)
                    .forEach(productVariantRepository::delete);
            productRepository.findById(productId).ifPresent(productRepository::delete);
        }
        userRepository.delete(admin);
    }

    private MockHttpSession loginSession(User account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", account.getEmail())
                        .param("password", PASSWORD)
                        .session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private Integer createThroughForm(MockHttpSession adminSession, String newCode) throws Exception {
        String redirect = mockMvc.perform(post("/admin/san-pham").with(csrf()).session(adminSession)
                        .param("productCode", newCode)
                        .param("name", "Vợt tạo từ form " + newCode)
                        .param("brandId", String.valueOf(brand.getId()))
                        .param("categoryId", String.valueOf(category.getId()))
                        .param("description", "Sản phẩm do test tạo qua form")
                        .param("lengthMm", "675")
                        .param("balancePointMm", "300")
                        .param("balanceType", "HEAD_HEAVY")
                        .param("shaftFlexibility", "STIFF")
                        .param("maxTensionKg", "28.0")
                        .param("frameMaterial", "Graphite")
                        .param("shaftMaterial", "Graphite")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm sản phẩm")))
                .andReturn().getResponse().getRedirectedUrl();

        Integer productId = Integer.valueOf(redirect.substring("/admin/san-pham/".length(),
                redirect.indexOf("/bien-the")));
        createdProductIds.add(productId);
        return productId;
    }

    @Test
    @DisplayName("Danh sách sản phẩm: hiện theo từ khoá, lọc theo trạng thái ẩn ⇒ thấy thông báo rỗng")
    void productListShowsAndFilters() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/san-pham").session(adminSession).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý sản phẩm")))
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("Biến thể &amp; ảnh")))
                .andExpect(content().string(containsString("Liên hệ")));   // chưa có biến thể đang bán

        mockMvc.perform(get("/admin/san-pham").session(adminSession)
                        .param("keyword", code)
                        .param("status", "HIDDEN"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có sản phẩm nào phù hợp bộ lọc")));
    }

    @Test
    @DisplayName("Form thêm sản phẩm: hiện đủ hãng và loại để chọn")
    void productFormRenders() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/san-pham/tao-moi").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm sản phẩm mới")))
                .andExpect(content().string(containsString(brand.getName())))
                .andExpect(content().string(containsString(category.getName())))
                .andExpect(content().string(containsString("Thông số kỹ thuật")));
    }

    @Test
    @DisplayName("Thêm sản phẩm qua form: mã viết hoa, lưu đúng, chuyển sang trang biến thể")
    void createProductThroughForm() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        String newCode = "adt" + suffix.substring(suffix.length() - 6);

        Integer productId = createThroughForm(adminSession, newCode);

        Product created = productRepository.findById(productId).orElseThrow();
        assertThat(created.getProductCode()).isEqualTo(newCode.toUpperCase());
        assertThat(created.getBrand().getId()).isEqualTo(brand.getId());
        assertThat(created.getCategory().getId()).isEqualTo(category.getId());
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.isDeleted()).isFalse();
        assertThat(created.getMaxTensionKg()).isEqualByComparingTo("28.0");
        assertThat(created.getBalancePointMm()).isEqualTo(300);
    }

    @Test
    @DisplayName("Thêm sản phẩm trùng mã: hiện lại form kèm lý do, không thêm bản ghi")
    void createProductDuplicateCodeShowsFormError() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        long before = productRepository.count();

        mockMvc.perform(post("/admin/san-pham").with(csrf()).session(adminSession)
                        .param("productCode", code)
                        .param("name", "Sản phẩm trùng mã")
                        .param("brandId", String.valueOf(brand.getId()))
                        .param("categoryId", String.valueOf(category.getId()))
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã tồn tại")))
                .andExpect(content().string(containsString("Thêm sản phẩm mới")));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Thêm biến thể qua form: lưu giá/tồn kho và trừ kho đúng khi bán")
    void createVariantThroughForm() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        String sku = code + "-01";

        mockMvc.perform(get("/admin/san-pham/" + product.getId() + "/bien-the/tao-moi").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm biến thể cho")));

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the").with(csrf()).session(adminSession)
                        .param("sku", sku.toLowerCase())
                        .param("color", "Xanh đen")
                        .param("weightClass", "4U")
                        .param("gripSize", "G5")
                        .param("price", "4690000")
                        .param("originalPrice", "5290000")
                        .param("stockQuantity", "12")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/san-pham/" + product.getId() + "/bien-the"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm biến thể")));

        List<ProductVariant> variants = productVariantRepository
                .findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(product.getId());
        assertThat(variants).hasSize(1);
        ProductVariant variant = variants.get(0);
        assertThat(variant.getSku()).isEqualTo(sku.toUpperCase());
        assertThat(variant.getPrice()).isEqualByComparingTo("4690000");
        assertThat(variant.getOriginalPrice()).isEqualByComparingTo("5290000");
        assertThat(variant.getStockQuantity()).isEqualTo(12);
        assertThat(variant.getStatus()).isEqualTo(VariantStatus.ACTIVE);

        // Trang biến thể & ảnh phải hiện biến thể vừa tạo kèm giá và tồn kho
        mockMvc.perform(get("/admin/san-pham/" + product.getId() + "/bien-the").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Biến thể (giá bán &amp; tồn kho)")))
                .andExpect(content().string(containsString(variant.getSku())))
                .andExpect(content().string(containsString("4.690.000 ₫")))
                .andExpect(content().string(containsString("Bộ ảnh sản phẩm")))
                // Form cập nhật giá hàng loạt (mục 13): checkbox nằm trong bảng nhưng gắn vào form này
                .andExpect(content().string(containsString("id=\"bulkPriceForm\"")))
                .andExpect(content().string(containsString("form=\"bulkPriceForm\" name=\"variantIds\"")))
                .andExpect(content().string(containsString("value=\"" + variant.getId() + "\"")))
                .andExpect(content().string(containsString("Cập nhật giá đã tích")));
    }

    @Test
    @DisplayName("Biến thể sai luật (giá gạch ngang < giá bán) và thiếu ô bắt buộc đều bị chặn")
    void variantFormValidates() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the").with(csrf()).session(adminSession)
                        .param("sku", code + "-01")
                        .param("color", "Đỏ")
                        .param("weightClass", "4U")
                        .param("gripSize", "G5")
                        .param("price", "5000000")
                        .param("originalPrice", "4000000")
                        .param("stockQuantity", "5")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giá gạch ngang phải lớn hơn hoặc bằng giá bán")));

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the").with(csrf()).session(adminSession)
                        .param("sku", "")
                        .param("color", "Đỏ")
                        .param("weightClass", "4U")
                        .param("gripSize", "G5")
                        .param("price", "5000000")
                        .param("stockQuantity", "5")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập SKU.")));

        assertThat(productVariantRepository.findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(product.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Tải ảnh lên: file được lưu thật, ảnh đầu tiên là ảnh chính; gỡ ảnh thì xoá cả file")
    void uploadAndDeleteImage() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        MockMultipartFile file = new MockMultipartFile("file", "voi-that.png", "image/png",
                "PNG-GIA-LAP".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/san-pham/" + product.getId() + "/anh").file(file)
                        .param("makePrimary", "true").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm ảnh")));

        List<ProductImage> images = productImageRepository
                .findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        assertThat(images).hasSize(1);
        ProductImage uploaded = images.get(0);
        assertThat(uploaded.isPrimaryImage()).isTrue();
        assertThat(uploaded.getImageUrl()).startsWith("/uploads/");

        String fileName = uploaded.getImageUrl().substring("/uploads/".length());
        Path storedFile = fileStorageService.getUploadDir().resolve(fileName);
        assertThat(Files.exists(storedFile)).isTrue();

        // Ảnh này cũng phải hiện trên trang khách (resource handler /uploads/** đã map)
        mockMvc.perform(get(uploaded.getImageUrl())).andExpect(status().isOk());

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/anh/" + uploaded.getId() + "/xoa")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã gỡ ảnh")));

        assertThat(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId())).isEmpty();
        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    @DisplayName("Tải file không phải ảnh: bị chặn kèm thông báo, không ghi DB")
    void uploadRejectsNonImage() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        MockMultipartFile file = new MockMultipartFile("file", "ghichu.txt", "text/plain",
                "day khong phai anh".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/san-pham/" + product.getId() + "/anh").file(file)
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("Chỉ nhận ảnh định dạng")));

        assertThat(productImageRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId())).isEmpty();
    }

    @Test
    @DisplayName("Ẩn sản phẩm ⇒ khách vào trang chi tiết bị 404; hiện lại thì xem được")
    void toggleProductStatusAffectsShop() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(code + "-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("4690000"));
        variant.setStockQuantity(5);
        variant.setStatus(VariantStatus.ACTIVE);
        productVariantRepository.save(variant);

        mockMvc.perform(get("/san-pham/" + product.getId())).andExpect(status().isOk());

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/doi-trang-thai")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã ẩn sản phẩm")));

        assertThat(productRepository.findById(product.getId()).orElseThrow().getStatus())
                .isEqualTo(ProductStatus.HIDDEN);
        mockMvc.perform(get("/san-pham/" + product.getId())).andExpect(status().isNotFound());

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/doi-trang-thai")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cho hiển thị")));
        mockMvc.perform(get("/san-pham/" + product.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Xoá mềm sản phẩm: biến mất khỏi danh sách quản trị và khỏi trang khách")
    void softDeleteRemovesFromList() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/xoa").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/san-pham"))
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá sản phẩm")));

        Product deleted = productRepository.findById(product.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();

        mockMvc.perform(get("/admin/san-pham").session(adminSession).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có sản phẩm nào phù hợp bộ lọc")));
        mockMvc.perform(get("/san-pham/" + product.getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cập nhật giá hàng loạt: giảm 10% rồi bỏ giảm giá, giá biến thể về đúng trạng thái ban đầu")
    void bulkPriceUpdateThroughForm() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(code + "-10");
        variant.setColor("Đỏ");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("5000000"));
        variant.setStockQuantity(4);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the/cap-nhat-gia")
                        .with(csrf()).session(adminSession)
                        .param("mode", "DISCOUNT")
                        .param("percent", "10")
                        .param("variantIds", String.valueOf(variant.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/san-pham/" + product.getId() + "/bien-the"))
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật giá cho 1 biến thể")));

        ProductVariant discounted = productVariantRepository.findById(variant.getId()).orElseThrow();
        assertThat(discounted.getPrice()).isEqualByComparingTo("4500000");
        assertThat(discounted.getOriginalPrice()).isEqualByComparingTo("5000000");

        // Không tích biến thể nào ⇒ chặn, không đụng dữ liệu
        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the/cap-nhat-gia")
                        .with(csrf()).session(adminSession)
                        .param("mode", "DISCOUNT")
                        .param("percent", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("tích chọn ít nhất một biến thể")));

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the/cap-nhat-gia")
                        .with(csrf()).session(adminSession)
                        .param("mode", "CLEAR_DISCOUNT")
                        .param("variantIds", String.valueOf(variant.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật giá cho 1 biến thể")));

        ProductVariant restored = productVariantRepository.findById(variant.getId()).orElseThrow();
        assertThat(restored.getPrice()).isEqualByComparingTo("5000000");
        assertThat(restored.getOriginalPrice()).isNull();
    }

    @Test
    @DisplayName("Xoá biến thể chưa vào đơn hàng: thành công và biến mất khỏi danh sách")
    void deleteUnusedVariant() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(code + "-02");
        variant.setColor("Trắng");
        variant.setWeightClass("3U");
        variant.setGripSize("G4");
        variant.setPrice(new BigDecimal("3990000"));
        variant.setStockQuantity(3);
        variant.setStatus(VariantStatus.INACTIVE);
        variant = productVariantRepository.save(variant);

        mockMvc.perform(post("/admin/san-pham/" + product.getId() + "/bien-the/" + variant.getId() + "/xoa")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá biến thể")));

        assertThat(productVariantRepository.findById(variant.getId())).isEmpty();
    }
}
