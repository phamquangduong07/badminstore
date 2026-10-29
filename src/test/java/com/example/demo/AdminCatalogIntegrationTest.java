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
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
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
 * Test tích hợp quản trị hãng/loại vợt và ảnh biến thể:
 * render trang, thêm/sửa/xoá mềm, chặn xoá khi đang được sản phẩm dùng,
 * tải ảnh biến thể thật xuống thư mục uploads và gỡ ảnh.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminCatalogIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private User admin;

    private final List<Integer> createdBrandIds = new ArrayList<>();
    private final List<Integer> createdCategoryIds = new ArrayList<>();
    private final List<Integer> createdProductIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());

        admin = new User();
        admin.setEmail("admin.catalog." + suffix + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFullName("Quản trị viên danh mục");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);
    }

    @AfterEach
    void cleanUp() {
        // Xoá sản phẩm trước để nhả khoá ngoại tới hãng/loại.
        for (Integer productId : createdProductIds) {
            productVariantRepository.findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(productId)
                    .forEach(variant -> {
                        fileStorageService.deleteIfUploaded(variant.getImageUrl());
                        productVariantRepository.delete(variant);
                    });
            productRepository.findById(productId).ifPresent(productRepository::delete);
        }
        for (Integer brandId : createdBrandIds) {
            brandRepository.findById(brandId).ifPresent(brandRepository::delete);
        }
        for (Integer categoryId : createdCategoryIds) {
            categoryRepository.findById(categoryId).ifPresent(categoryRepository::delete);
        }
        userRepository.delete(admin);
    }

    private MockHttpSession loginSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", admin.getEmail())
                        .param("password", PASSWORD)
                        .session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    @Test
    @DisplayName("Hãng: xem danh sách, thêm kèm logo, sửa tên/gỡ logo, xoá mềm")
    void brandCrudAndLogo() throws Exception {
        MockHttpSession adminSession = loginSession();
        String brandName = "Hãng Test " + suffix.substring(suffix.length() - 5);

        mockMvc.perform(get("/admin/thuong-hieu").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý thương hiệu")))
                .andExpect(content().string(containsString("Thêm thương hiệu")));

        mockMvc.perform(get("/admin/thuong-hieu/tao-moi").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm thương hiệu mới")))
                .andExpect(content().string(containsString("Logo hãng")));

        MockMultipartFile logo = new MockMultipartFile("logoFile", "logo.png", "image/png",
                "PNG-GIA-LAP".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/thuong-hieu").file(logo)
                        .param("name", brandName)
                        .param("description", "Hãng do test tạo")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/thuong-hieu"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm hãng")));

        Brand brand = brandRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> item.getName().equals(brandName))
                .findFirst().orElseThrow();
        createdBrandIds.add(brand.getId());
        assertThat(brand.getLogo()).startsWith("/uploads/");
        Path storedLogo = fileStorageService.getUploadDir()
                .resolve(brand.getLogo().substring("/uploads/".length()));
        assertThat(Files.exists(storedLogo)).isTrue();

        mockMvc.perform(get("/admin/thuong-hieu").session(adminSession).param("keyword", brandName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(brandName)));

        mockMvc.perform(get("/admin/thuong-hieu/" + brand.getId() + "/sua").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sửa thương hiệu #" + brand.getId())))
                .andExpect(content().string(containsString("Gỡ logo hiện tại")));

        String newName = brandName + " mới";
        mockMvc.perform(multipart("/admin/thuong-hieu/" + brand.getId())
                        .param("name", newName)
                        .param("description", "Đã đổi tên")
                        .param("removeLogo", "true")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật hãng")));

        Brand updated = brandRepository.findById(brand.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo(newName);
        assertThat(updated.getLogo()).isNull();
        assertThat(Files.exists(storedLogo)).isFalse();

        mockMvc.perform(post("/admin/thuong-hieu/" + brand.getId() + "/xoa")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá hãng")));

        assertThat(brandRepository.findById(brand.getId()).orElseThrow().isDeleted()).isTrue();
        mockMvc.perform(get("/admin/thuong-hieu").session(adminSession).param("keyword", newName))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa có thương hiệu nào")));
    }

    @Test
    @DisplayName("Loại vợt: thêm, sửa, tìm kiếm và xoá mềm")
    void categoryCrud() throws Exception {
        MockHttpSession adminSession = loginSession();
        String name = "Loại Test " + suffix.substring(suffix.length() - 5);

        mockMvc.perform(get("/admin/loai-vot/tao-moi").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thêm loại vợt mới")));

        mockMvc.perform(post("/admin/loai-vot").with(csrf()).session(adminSession)
                        .param("name", name)
                        .param("description", "Lối chơi do test tạo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/loai-vot"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm loại vợt")));

        Category category = categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(item -> item.getName().equals(name))
                .findFirst().orElseThrow();
        createdCategoryIds.add(category.getId());

        mockMvc.perform(get("/admin/loai-vot").session(adminSession).param("keyword", name))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý loại vợt")))
                .andExpect(content().string(containsString(name)));

        String newName = name + " mới";
        mockMvc.perform(post("/admin/loai-vot/" + category.getId()).with(csrf()).session(adminSession)
                        .param("name", newName)
                        .param("description", "Mô tả mới"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật loại vợt")));

        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getName()).isEqualTo(newName);

        mockMvc.perform(post("/admin/loai-vot/" + category.getId() + "/xoa")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá loại vợt")));

        assertThat(categoryRepository.findById(category.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Chặn xoá hãng đang được sản phẩm sử dụng, dữ liệu hãng được giữ nguyên")
    void cannotDeleteBrandInUse() throws Exception {
        MockHttpSession adminSession = loginSession();
        Brand brand = createBrand("Hãng đang dùng " + suffix.substring(suffix.length() - 5));
        Category category = createCategory("Loại đang dùng " + suffix.substring(suffix.length() - 5));
        createProduct("PRD" + suffix.substring(suffix.length() - 8), brand, category);

        mockMvc.perform(post("/admin/thuong-hieu/" + brand.getId() + "/xoa")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("đang được 1 sản phẩm sử dụng")));

        assertThat(brandRepository.findById(brand.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Biến thể: tải ảnh thật, hiện ảnh trong bảng quản trị và gỡ ảnh đúng cách")
    void variantImageUploadAndRemove() throws Exception {
        MockHttpSession adminSession = loginSession();
        Brand brand = createBrand("Hãng ảnh " + suffix.substring(suffix.length() - 5));
        Category category = createCategory("Loại ảnh " + suffix.substring(suffix.length() - 5));
        Product product = createProduct("IMG" + suffix.substring(suffix.length() - 8), brand, category);
        String sku = "IMG" + suffix.substring(suffix.length() - 7);

        MockMultipartFile image = new MockMultipartFile("imageFile", "bien-the.png", "image/png",
                "PNG-GIA-LAP".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/san-pham/" + product.getId() + "/bien-the").file(image)
                        .param("sku", sku)
                        .param("color", "Xanh đen")
                        .param("weightClass", "4U")
                        .param("gripSize", "G5")
                        .param("price", "4690000")
                        .param("stockQuantity", "8")
                        .param("status", "ACTIVE")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/san-pham/" + product.getId() + "/bien-the"))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm biến thể")));

        ProductVariant variant = productVariantRepository
                .findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(product.getId()).get(0);
        assertThat(variant.getImageUrl()).startsWith("/uploads/");
        Path storedImage = fileStorageService.getUploadDir()
                .resolve(variant.getImageUrl().substring("/uploads/".length()));
        assertThat(Files.exists(storedImage)).isTrue();

        mockMvc.perform(get("/admin/san-pham/" + product.getId() + "/bien-the").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ảnh biến thể " + variant.getSku())));

        mockMvc.perform(get("/admin/san-pham/" + product.getId() + "/bien-the/" + variant.getId() + "/sua")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ảnh riêng của biến thể")))
                .andExpect(content().string(containsString("Gỡ ảnh hiện tại")));

        mockMvc.perform(multipart("/admin/san-pham/" + product.getId() + "/bien-the/" + variant.getId())
                        .param("sku", sku)
                        .param("color", "Xanh đen")
                        .param("weightClass", "4U")
                        .param("gripSize", "G5")
                        .param("price", "4690000")
                        .param("stockQuantity", "8")
                        .param("status", "ACTIVE")
                        .param("removeImage", "true")
                        .with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật biến thể")));

        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getImageUrl()).isNull();
        assertThat(Files.exists(storedImage)).isFalse();
    }

    // ==================== DỮ LIỆU GIẢ ====================

    private Brand createBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setDeleted(false);
        brand = brandRepository.save(brand);
        createdBrandIds.add(brand.getId());
        return brand;
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setDeleted(false);
        category = categoryRepository.save(category);
        createdCategoryIds.add(category.getId());
        return category;
    }

    private Product createProduct(String code, Brand brand, Category category) {
        Product product = new Product();
        product.setProductCode(code);
        product.setName("Vợt kiểm thử " + code);
        product.setBrand(brand);
        product.setCategory(category);
        product.setStatus(ProductStatus.ACTIVE);
        product.setDeleted(false);
        product = productRepository.save(product);
        createdProductIds.add(product.getId());
        return product;
    }
}
