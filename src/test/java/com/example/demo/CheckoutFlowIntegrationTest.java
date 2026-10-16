package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Order;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAddress;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoucherRepository;
import com.example.demo.service.OrderService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test tích hợp TOÀN BỘ luồng mua hàng trên CSDL thật:
 * thêm giỏ → trang thanh toán (địa chỉ + voucher + phí ship) → đặt đơn PENDING (CHƯA trừ kho)
 * → xác nhận (trừ kho) → giao hàng → giao thành công (COD ⇒ đã thanh toán) → hoàn trả (cộng kho, hoàn tiền, trả voucher).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckoutFlowIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserAddressRepository addressRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private OrderService orderService;

    private String suffix;
    private User user;
    private User admin;
    private User otherUser;
    private Product product;
    private ProductVariant variant;
    private UserAddress address;
    private Voucher voucher;

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());

        user = userRepository.save(account("dat.hang." + suffix + "@example.com", UserRole.CUSTOMER));
        admin = userRepository.save(account("quan.tri." + suffix + "@example.com", UserRole.ADMIN));
        otherUser = userRepository.save(account("nguoi.khac." + suffix + "@example.com", UserRole.CUSTOMER));

        product = new Product();
        product.setProductCode("CHK-" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt cầu lông chạy thử đặt hàng");
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("CHK-SKU-" + suffix.substring(suffix.length() - 8));
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));
        variant.setStockQuantity(5);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);

        address = new UserAddress();
        address.setUser(user);
        address.setReceiverName("Nguyễn Văn Test");
        address.setReceiverPhone("0987654321");
        address.setProvinceName("TP. Hồ Chí Minh");
        address.setDistrictName("Quận 1");
        address.setWardName("Phường Bến Thành");
        address.setAddressDetail("123 Nguyễn Trãi");
        address.setDefaultAddress(true);
        address = addressRepository.save(address);

        voucher = new Voucher();
        voucher.setCode("GIAM10-" + suffix.substring(suffix.length() - 6));
        voucher.setName("Giảm 10% cho đơn từ 1 triệu");
        voucher.setDescription("Voucher dùng cho test tự động");
        voucher.setDiscountType(DiscountType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10"));
        voucher.setMinOrderValue(new BigDecimal("1000000"));
        voucher.setMaxDiscountAmount(new BigDecimal("500000"));
        voucher.setQuantity(5);
        voucher.setUsedQuantity(0);
        voucher.setStartAt(LocalDateTime.now().minusDays(1));
        voucher.setEndAt(LocalDateTime.now().plusDays(7));
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher = voucherRepository.save(voucher);
    }

    @AfterEach
    void cleanUp() {
        // Xoá theo đúng thứ tự khoá ngoại: lịch sử → thanh toán → dòng hàng → đơn → giỏ/địa chỉ/voucher → sản phẩm → tài khoản
        for (User owner : List.of(user, otherUser)) {
            for (Order order : orderRepository.findByUserIdOrderByCreatedAtDesc(owner.getId())) {
                historyRepository.deleteAll(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
                paymentRepository.findByOrderId(order.getId()).ifPresent(paymentRepository::delete);
                orderItemRepository.deleteAll(orderItemRepository.findByOrderIdOrderByProductVariantIdAsc(order.getId()));
                orderRepository.delete(order);
            }
            cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(owner.getId()));
        }
        voucherRepository.delete(voucher);
        addressRepository.delete(address);
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(user);
        userRepository.delete(admin);
        userRepository.delete(otherUser);
    }

    private User account(String email, UserRole role) {
        User created = new User();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode(PASSWORD));
        created.setFullName(role == UserRole.ADMIN ? "Quản trị viên test" : "Khách test");
        created.setRole(role);
        created.setStatus(UserStatus.ACTIVE);
        return created;
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

    /** Thêm vợt vào giỏ hàng qua đúng endpoint mà khách dùng. */
    private void addToCart(MockHttpSession session, int quantity) throws Exception {
        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(session)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", String.valueOf(quantity)))
                .andExpect(status().is3xxRedirection());
    }

    /** Gửi form đặt hàng COD kèm voucher ⇒ trả về id đơn vừa tạo (đọc từ URL chuyển hướng). */
    private Integer submitCheckout(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/thanh-toan").with(csrf()).session(session)
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD")
                        .param("voucherCode", voucher.getCode())
                        .param("note", "Giao trong giờ hành chính"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đặt hàng thành công")))
                .andReturn();

        String redirect = result.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith("/don-hang/");
        return Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
    }

    /** Thêm 2 cây vợt vào giỏ rồi đặt hàng COD kèm voucher ⇒ trả về id đơn vừa tạo. */
    private Integer placeOrderThroughWeb(MockHttpSession session) throws Exception {
        addToCart(session, 2);
        return submitCheckout(session);
    }

    @Test
    @DisplayName("Đặt hàng qua web: đơn PENDING chưa trừ kho, có snapshot + payment + lịch sử, giỏ được xoá")
    void placeOrder_createsPendingOrderAndClearsCart() throws Exception {
        MockHttpSession session = loginSession(user);

        addToCart(session, 2);

        // Trang thanh toán: chọn sẵn địa chỉ mặc định, hiện phí ship, voucher và tổng tiền tính ở server
        mockMvc.perform(get("/thanh-toan").session(session).param("voucher", voucher.getCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nguyễn Văn Test")))
                .andExpect(content().string(containsString("123 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh")))
                .andExpect(content().string(containsString("2.980.000 ₫")))     // tạm tính 1.490.000 × 2
                .andExpect(content().string(containsString("30.000 ₫")))        // phí ship
                .andExpect(content().string(containsString("298.000 ₫")))       // giảm 10%
                .andExpect(content().string(containsString("2.712.000 ₫")))     // tổng phải trả
                .andExpect(content().string(containsString(voucher.getCode())));

        Integer orderId = submitCheckout(session);

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderCode()).startsWith("BS");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderSource()).isEqualTo(OrderSource.ONLINE);
        assertThat(order.isStockDeducted()).isFalse();
        assertThat(order.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("298000");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("2712000");
        assertThat(order.getReceiverName()).isEqualTo("Nguyễn Văn Test");
        assertThat(order.getNote()).isEqualTo("Giao trong giờ hành chính");

        var items = orderItemRepository.findByOrderIdOrderByProductVariantIdAsc(orderId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getProductName()).isEqualTo("Vợt cầu lông chạy thử đặt hàng");
        assertThat(items.get(0).getSku()).isEqualTo(variant.getSku());
        assertThat(items.get(0).getUnitPrice()).isEqualByComparingTo("1490000");
        assertThat(items.get(0).getQuantity()).isEqualTo(2);
        assertThat(items.get(0).getSubtotal()).isEqualByComparingTo("2980000");

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("2712000");

        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(1);

        // Điểm quan trọng: đơn mới KHÔNG trừ kho; voucher đã dùng 1 lượt; giỏ hàng đã xoá
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isEqualTo(1);
        assertThat(cartItemRepository.countByUserId(user.getId())).isZero();

        // Khách xem được chi tiết + danh sách đơn của mình
        mockMvc.perform(get("/don-hang/" + orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(order.getOrderCode())))
                .andExpect(content().string(containsString("Chờ xác nhận")))
                .andExpect(content().string(containsString("Khách đặt hàng trên website")))
                .andExpect(content().string(containsString(voucher.getCode())));

        mockMvc.perform(get("/don-hang").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(order.getOrderCode())))
                .andExpect(content().string(containsString("2.712.000 ₫")));
    }

    @Test
    @DisplayName("Vòng đời đơn: xác nhận (trừ kho) → giao → thành công (COD đã trả) → hoàn trả (cộng kho, hoàn tiền)")
    void fullOrderLifecycle_deductsThenRestoresStock() throws Exception {
        Integer orderId = placeOrderThroughWeb(loginSession(user));

        orderService.changeStatusByAdmin(orderId, OrderStatus.CONFIRMED, admin.getId(), "Đã gọi xác nhận với khách");
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(3);
        Order confirmed = orderRepository.findById(orderId).orElseThrow();
        assertThat(confirmed.isStockDeducted()).isTrue();
        assertThat(confirmed.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);

        orderService.changeStatusByAdmin(orderId, OrderStatus.SHIPPING, admin.getId(), "Đã giao cho đơn vị vận chuyển");
        orderService.changeStatusByAdmin(orderId, OrderStatus.DELIVERED, admin.getId(), "Giao thành công");

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);      // COD trả khi nhận hàng
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(4);

        MockHttpSession session = loginSession(user);
        mockMvc.perform(get("/don-hang/" + orderId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Giao thành công")))
                .andExpect(content().string(containsString("Đã thanh toán")));

        // Hoàn trả 100%: cộng lại kho, hoàn tiền, trả lượt voucher
        orderService.returnByCustomer(user.getId(), orderId,
                "Vợt bị lỗi mặt sơn ở phần đầu vợt, tôi xin hoàn trả");

        Order returned = orderRepository.findById(orderId).orElseThrow();
        assertThat(returned.getOrderStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(returned.isStockDeducted()).isFalse();
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isZero();
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(5);
    }

    @Test
    @DisplayName("Khách huỷ đơn PENDING: trả lượt voucher, kho không đổi, có ghi lý do vào lịch sử")
    void cancelPendingOrderThroughWeb_releasesVoucher() throws Exception {
        Integer orderId = placeOrderThroughWeb(loginSession(user));

        mockMvc.perform(post("/don-hang/" + orderId + "/huy").with(csrf()).session(loginSession(user))
                        .param("reason", "Tôi đổi ý muốn chọn cây vợt khác phù hợp hơn"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/don-hang/" + orderId))
                .andExpect(flash().attribute("successMessage", containsString("Đã huỷ đơn hàng")));

        Order cancelled = orderRepository.findById(orderId).orElseThrow();
        assertThat(cancelled.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.isStockDeducted()).isFalse();
        assertThat(cancelled.getCancelReason()).contains("đổi ý");
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getUsedQuantity()).isZero();
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("Hết hàng giữa lúc đặt: chặn tại bước thanh toán, không tạo đơn, báo rõ SKU")
    void checkoutIsRejectedWhenStockRanOut() throws Exception {
        MockHttpSession session = loginSession(user);
        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(session)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "3"))
                .andExpect(status().is3xxRedirection());

        // Một khách khác vừa mua gần hết hàng
        variant.setStockQuantity(1);
        productVariantRepository.save(variant);

        mockMvc.perform(post("/thanh-toan").with(csrf()).session(session)
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("Không đủ tồn kho cho SKU")))
                .andExpect(flash().attribute("errorMessage", containsString("chỉ còn 1 sản phẩm")));

        assertThat(orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("Không xem được đơn của người khác (404) và khách vãng lai bị đưa về trang đăng nhập")
    void orderAccessIsProtected() throws Exception {
        Integer orderId = placeOrderThroughWeb(loginSession(user));

        mockMvc.perform(get("/don-hang/" + orderId).session(loginSession(otherUser)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/don-hang"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dang-nhap"));

        mockMvc.perform(get("/thanh-toan"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dang-nhap"));
    }

    @Test
    @DisplayName("Giỏ trống mà vào trang thanh toán: quay về giỏ hàng kèm thông báo")
    void checkoutWithEmptyCartRedirectsToCart() throws Exception {
        mockMvc.perform(get("/thanh-toan").session(loginSession(user)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gio-hang"))
                .andExpect(flash().attribute("errorMessage", containsString("Giỏ hàng của bạn đang trống")));
    }
}
