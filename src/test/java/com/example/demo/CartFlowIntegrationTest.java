package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.CartItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tích hợp giỏ hàng trên CSDL thật: form POST → Service → cart_items → trang giỏ hàng,
 * kèm kiểm chứng phân quyền (khách chưa đăng nhập), chặn vượt tồn kho và badge ở header.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartFlowIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private final List<User> extraUsers = new ArrayList<>();

    private final List<ProductVariant> extraVariants = new ArrayList<>();

    private String suffix;
    private User user;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());

        user = userRepository.save(customer("gio.hang." + suffix + "@example.com"));

        product = new Product();
        product.setProductCode("CART-" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt cầu lông chạy thử giỏ hàng");
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("CART-SKU-" + suffix.substring(suffix.length() - 8));
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));
        variant.setStockQuantity(5);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);
    }

    @AfterEach
    void cleanUp() {
        // Thứ tự xoá theo khoá ngoại: giỏ hàng → biến thể → sản phẩm → tài khoản.
        cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
        for (User extra : extraUsers) {
            cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(extra.getId()));
        }
        productVariantRepository.deleteAll(extraVariants);
        extraVariants.clear();
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(user);
        for (User extra : extraUsers) {
            userRepository.delete(extra);
        }
        extraUsers.clear();
    }

    /** Biến thể thứ 2 của cùng sản phẩm (dùng khi test cần 2 dòng giỏ hàng khác biến thể). */
    private ProductVariant secondVariant() {
        ProductVariant second = new ProductVariant();
        second.setProduct(product);
        second.setSku("CART-SKU2-" + suffix.substring(suffix.length() - 8));
        second.setColor("Đỏ đô");
        second.setWeightClass("3U");
        second.setGripSize("G5");
        second.setPrice(new BigDecimal("1490000"));
        second.setStockQuantity(5);
        second.setStatus(VariantStatus.ACTIVE);
        second = productVariantRepository.save(second);
        extraVariants.add(second);
        return second;
    }

    private User customer(String email) {
        User created = new User();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode(PASSWORD));
        created.setFullName("Khách Giỏ Hàng");
        created.setRole(UserRole.CUSTOMER);
        created.setStatus(UserStatus.ACTIVE);
        return created;
    }

    private MockHttpSession loginSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", user.getEmail())
                        .param("password", PASSWORD)
                        .session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private CartItem savedCartItem(ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.setUser(user);
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return cartItemRepository.save(item);
    }

    @Test
    @DisplayName("Thêm vào giỏ từ trang chi tiết: lưu cart_items và quay lại trang sản phẩm kèm flash")
    void addToCartThroughForm() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(session)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "2")
                        .param("back", "/san-pham/" + product.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/san-pham/" + product.getId()))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm")))
                .andExpect(flash().attribute("successMessage", containsString("Xanh đen · 4U · G5")));

        List<CartItem> items = cartItemRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Trang giỏ hàng: hiện dòng hàng, thành tiền, tạm tính và badge số lượng ở header")
    void cartPageShowsLinesAndHeaderBadge() throws Exception {
        savedCartItem(variant, 2);
        MockHttpSession session = loginSession();

        mockMvc.perform(get("/gio-hang").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giỏ hàng của bạn")))
                .andExpect(content().string(containsString(product.getName())))
                .andExpect(content().string(containsString("Xanh đen · 4U · G5")))
                .andExpect(content().string(containsString("SKU " + variant.getSku())))
                .andExpect(content().string(containsString("2.980.000 ₫")))     // 1.490.000 × 2
                .andExpect(content().string(containsString("Tạm tính")))
                .andExpect(content().string(containsString("Còn 5")));

        mockMvc.perform(get("/san-pham").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        Pattern.compile("(?s).*cart-badge[^>]*>\\s*2\\s*<.*"))));
    }

    @Test
    @DisplayName("Giỏ trống: hiện trạng thái rỗng và không có bảng sản phẩm")
    void emptyCartShowsEmptyState() throws Exception {
        mockMvc.perform(get("/gio-hang").session(loginSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giỏ hàng của bạn đang trống")));
    }

    @Test
    @DisplayName("Cập nhật số lượng rồi xoá dòng: dữ liệu đổi đúng và có flash tiếng Việt")
    void updateThenRemoveCartItem() throws Exception {
        CartItem item = savedCartItem(variant, 1);
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/gio-hang/cap-nhat").with(csrf()).session(session)
                        .param("cartItemId", String.valueOf(item.getId()))
                        .param("quantity", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gio-hang"))
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật")));

        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(3);

        mockMvc.perform(post("/gio-hang/xoa").with(csrf()).session(session)
                        .param("cartItemId", String.valueOf(item.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá sản phẩm khỏi giỏ hàng")));

        assertThat(cartItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @DisplayName("Xoá tất cả: đếm đúng số dòng đã xoá và báo cho khách")
    void clearAllCartItems() throws Exception {
        savedCartItem(variant, 1);
        savedCartItem(secondVariant(), 2);
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/gio-hang/xoa-tat-ca").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xoá toàn bộ 2 sản phẩm")));

        assertThat(cartItemRepository.countByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("Đặt vượt tồn kho: không ghi CSDL, khách nhận thông báo lỗi nêu rõ SKU")
    void addingMoreThanStockIsRejected() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(session)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "6"))                       // tồn kho chỉ 5
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", containsString("Không đủ tồn kho cho SKU")))
                .andExpect(flash().attribute("errorMessage", containsString("chỉ còn 5 sản phẩm")));

        assertThat(cartItemRepository.countByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("Sửa giỏ hàng của người khác ⇒ 404, dữ liệu của người đó không đổi")
    void cannotTouchAnotherCustomersCartItem() throws Exception {
        User other = userRepository.save(customer("gio.hang.khac." + System.nanoTime() + "@example.com"));
        extraUsers.add(other);
        CartItem foreign = new CartItem();
        foreign.setUser(other);
        foreign.setProductVariant(variant);
        foreign.setQuantity(2);
        foreign = cartItemRepository.save(foreign);

        mockMvc.perform(post("/gio-hang/cap-nhat").with(csrf()).session(loginSession())
                        .param("cartItemId", String.valueOf(foreign.getId()))
                        .param("quantity", "4"))
                .andExpect(status().isNotFound());

        assertThat(cartItemRepository.findById(foreign.getId()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Khách chưa đăng nhập: xem/thêm giỏ hàng đều bị đưa về trang đăng nhập")
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/gio-hang"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dang-nhap"));

        mockMvc.perform(post("/gio-hang/them").with(csrf())
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dang-nhap"));

        assertThat(cartItemRepository.countByUserId(user.getId())).isZero();
    }
}
