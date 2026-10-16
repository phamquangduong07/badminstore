package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductReview;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.ReviewStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductReviewRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
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
 * Test tích hợp ĐÁNH GIÁ SẢN PHẨM (mức Khá) – chạy trên CSDL thật:
 * người mua đã nhận hàng đánh giá được, người chưa mua bị chặn, mỗi lượt mua một lần,
 * điểm 1–5 và nội dung bắt buộc. Đánh giá sau khi gửi hiện trên trang chi tiết kèm điểm trung bình.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

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
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductReviewRepository productReviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    /** Khách đã mua và đã nhận hàng sản phẩm kiểm thử. */
    private User buyer;
    /** Khách chưa từng mua sản phẩm kiểm thử. */
    private User stranger;
    private Product product;
    private ProductVariant variant;
    private Order order;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());

        buyer = user("review.buyer." + suffix + "@example.com", "Khách đã mua");
        stranger = user("review.other." + suffix + "@example.com", "Khách chưa mua");

        Brand brand = brandRepository.findByDeletedFalseOrderByNameAsc().get(0);
        Category category = categoryRepository.findByDeletedFalseOrderByNameAsc().get(0);

        product = new Product();
        product.setProductCode("RV" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt kiểm thử đánh giá " + suffix.substring(suffix.length() - 4));
        product.setBrand(brand);
        product.setCategory(category);
        product.setStatus(ProductStatus.ACTIVE);
        product.setDeleted(false);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(product.getProductCode() + "-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("4690000"));
        variant.setStockQuantity(5);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);

        // Đơn đã giao của người mua: khớp CK_orders_stock (DELIVERED ⇒ stock_deducted = 1)
        order = new Order();
        order.setOrderCode("RV" + suffix.substring(suffix.length() - 10));
        order.setOrderSource(OrderSource.ONLINE);
        order.setUser(buyer);
        order.setReceiverName("Khách đã mua");
        order.setReceiverPhone("0901234567");
        order.setShippingAddress("12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh");
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.addItem(OrderItem.from(variant, 1));
        order.recalculateTotals();
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setStockDeducted(true);
        order = orderRepository.save(order);
    }

    @AfterEach
    void cleanUp() {
        // Thứ tự xoá theo khoá ngoại: đánh giá (trỏ tới order_items) → đơn (cascade xoá dòng đơn)
        // → biến thể → sản phẩm → người dùng
        productReviewRepository.findByProductId(product.getId()).forEach(productReviewRepository::delete);
        orderRepository.delete(order);
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(buyer);
        userRepository.delete(stranger);
    }

    private User user(String email, String fullName) {
        User account = new User();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(PASSWORD));
        account.setFullName(fullName);
        account.setRole(UserRole.CUSTOMER);
        account.setStatus(UserStatus.ACTIVE);
        return userRepository.save(account);
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

    @Test
    @DisplayName("Khách vãng lai: chỉ thấy lời mời đăng nhập, không thấy form đánh giá")
    void anonymousSeesLoginPrompt() throws Exception {
        mockMvc.perform(get("/san-pham/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("để đánh giá sản phẩm")))
                .andExpect(content().string(containsString("Chưa có đánh giá nào cho sản phẩm này")))
                .andExpect(content().string(not(containsString("Chia sẻ cảm nhận của bạn"))));
    }

    @Test
    @DisplayName("Khách đã nhận hàng gửi đánh giá: lưu VISIBLE, hiện lại trên trang chi tiết kèm điểm trung bình")
    void buyerCanSubmitReview() throws Exception {
        MockHttpSession session = loginSession(buyer);

        mockMvc.perform(get("/san-pham/" + product.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chia sẻ cảm nhận của bạn")));

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "5")
                        .param("comment", "  Vợt nhẹ, đập cầu rất êm  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/san-pham/" + product.getId())))
                .andExpect(flash().attribute("successMessage", containsString("Cảm ơn bạn đã đánh giá")));

        List<ProductReview> reviews = productReviewRepository.findByProductId(product.getId());
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getRating()).isEqualTo((byte) 5);
        assertThat(reviews.get(0).getComment()).isEqualTo("Vợt nhẹ, đập cầu rất êm");
        assertThat(reviews.get(0).getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(reviews.get(0).getUser().getId()).isEqualTo(buyer.getId());

        mockMvc.perform(get("/san-pham/" + product.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vợt nhẹ, đập cầu rất êm")))
                .andExpect(content().string(containsString(buyer.getFullName())))
                .andExpect(content().string(containsString("5,0/5")))
                .andExpect(content().string(containsString("1 lượt đánh giá")))
                .andExpect(content().string(containsString("Cảm ơn bạn đã mua và đánh giá")))
                .andExpect(content().string(not(containsString("Chia sẻ cảm nhận của bạn"))));
    }

    @Test
    @DisplayName("Khách chưa mua: thấy ghi chú điều kiện và bị chặn khi gửi đánh giá")
    void strangerCannotReview() throws Exception {
        MockHttpSession session = loginSession(stranger);

        mockMvc.perform(get("/san-pham/" + product.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chỉ khách")))
                .andExpect(content().string(not(containsString("Chia sẻ cảm nhận của bạn"))));

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "5")
                        .param("comment", "Đánh giá khi chưa mua"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Chỉ khách đã mua và đã nhận sản phẩm này")));

        assertThat(productReviewRepository.findByProductId(product.getId())).isEmpty();
    }

    @Test
    @DisplayName("Mỗi lượt mua chỉ đánh giá một lần: lần gửi thứ hai bị chặn")
    void duplicateReviewIsBlocked() throws Exception {
        MockHttpSession session = loginSession(buyer);

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "4")
                        .param("comment", "Lần đầu"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "2")
                        .param("comment", "Lần hai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        containsString("Mỗi lượt mua chỉ đánh giá một lần")));

        List<ProductReview> reviews = productReviewRepository.findByProductId(product.getId());
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getComment()).isEqualTo("Lần đầu");
    }

    @Test
    @DisplayName("Dữ liệu sai (số sao ngoài 1–5, nội dung trống) bị chặn trước khi ghi")
    void invalidInputsAreRejected() throws Exception {
        MockHttpSession session = loginSession(buyer);

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "9")
                        .param("comment", "Số sao sai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("từ 1 đến 5")));

        mockMvc.perform(post("/danh-gia").with(csrf()).session(session)
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "5")
                        .param("comment", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("vài dòng cảm nhận")));

        assertThat(productReviewRepository.findByProductId(product.getId())).isEmpty();
    }

    @Test
    @DisplayName("Chưa đăng nhập mà gửi đánh giá: bị chuyển về trang đăng nhập")
    void anonymousPostRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/danh-gia").with(csrf())
                        .param("productId", String.valueOf(product.getId()))
                        .param("rating", "5")
                        .param("comment", "Khách vãng lai"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/dang-nhap")));

        assertThat(productReviewRepository.findByProductId(product.getId())).isEmpty();
    }
}
