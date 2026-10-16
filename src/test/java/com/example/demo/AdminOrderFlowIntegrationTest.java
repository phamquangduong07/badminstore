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
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test tích hợp khu quản trị: dashboard hiển thị, danh sách đơn có lọc, xác nhận nhanh (trừ kho),
 * đổi trạng thái theo máy trạng thái, xác nhận thanh toán, và chặn khách thường vào /admin/**.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderFlowIntegrationTest {

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    private String suffix;
    private User admin;
    private User customer;
    private Product product;
    private ProductVariant variant;
    private UserAddress address;

    /** Id các đơn do test tạo ra (kể cả đơn MANUAL user_id = NULL) để dọn sạch sau mỗi test. */
    private final List<Integer> createdOrderIds = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());

        admin = userRepository.save(account("admin.don." + suffix + "@example.com", UserRole.ADMIN));
        customer = userRepository.save(account("khach.don." + suffix + "@example.com", UserRole.CUSTOMER));

        product = new Product();
        product.setProductCode("ADM-" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt cầu lông chạy thử quản trị đơn");
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("ADM-SKU-" + suffix.substring(suffix.length() - 8));
        variant.setColor("Đỏ đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));
        variant.setStockQuantity(10);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);

        address = new UserAddress();
        address.setUser(customer);
        address.setReceiverName("Trần Văn Khách");
        address.setReceiverPhone("0912345678");
        address.setProvinceName("Hà Nội");
        address.setDistrictName("Quận Cầu Giấy");
        address.setWardName("Phường Dịch Vọng");
        address.setAddressDetail("45 Xuân Thủy");
        address.setDefaultAddress(true);
        address = addressRepository.save(address);
    }

    @AfterEach
    void cleanUp() {
        for (Integer orderId : createdOrderIds) {
            if (orderRepository.existsById(orderId)) {
                historyRepository.deleteAll(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId));
                paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
                orderItemRepository.deleteAll(orderItemRepository.findByOrderIdOrderByProductVariantIdAsc(orderId));
                orderRepository.deleteById(orderId);
            }
        }
        cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(customer.getId()));
        addressRepository.delete(address);
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(customer);
        userRepository.delete(admin);
    }

    private User account(String email, UserRole role) {
        User created = new User();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode(PASSWORD));
        created.setFullName(role == UserRole.ADMIN ? "Quản trị viên test" : "Khách mua test");
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

    /** Khách tự đặt 1 cây vợt rồi trả về id đơn (đơn đang PENDING). */
    private Integer placeOrder(MockHttpSession customerSession, String paymentMethod) throws Exception {
        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(customerSession)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(post("/thanh-toan").with(csrf()).session(customerSession)
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", paymentMethod))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirect = result.getResponse().getRedirectedUrl();
        Integer orderId = Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);
        return orderId;
    }

    /** Admin tạo đơn thủ công qua form web ⇒ trả về id đơn vừa tạo. */
    private Integer createManualOrderThroughWeb(MockHttpSession adminSession, String sku, int quantity,
                                                String paymentMethod, boolean confirmNow) throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/don-hang/tao-don").with(csrf()).session(adminSession)
                        .param("customerEmail", "")
                        .param("receiverName", "Khách mua tại quầy")
                        .param("receiverPhone", "0901112223")
                        .param("shippingAddress", "Mua trực tiếp tại cửa hàng BadStore")
                        .param("paymentMethod", paymentMethod)
                        .param("shippingFee", "0")
                        .param("note", "Khách quen mua tại quầy")
                        .param("confirmNow", confirmNow ? "true" : "false")
                        .param("lines[0].sku", sku)
                        .param("lines[0].quantity", String.valueOf(quantity)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã tạo đơn thủ công")))
                .andReturn();

        String redirect = result.getResponse().getRedirectedUrl();
        Integer orderId = Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);
        return orderId;
    }

    @Test
    @DisplayName("Admin xem được dashboard, danh sách đơn và chi tiết đơn có khu thao tác")
    void adminPagesRender() throws Exception {
        Integer orderId = placeOrder(loginSession(customer), "COD");
        String orderCode = orderRepository.findById(orderId).orElseThrow().getOrderCode();
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tổng quan bán hàng")))
                .andExpect(content().string(containsString("revenueChart")))
                .andExpect(content().string(containsString("Sản phẩm bán chạy")))
                .andExpect(content().string(containsString("Sắp hết hàng")));

        mockMvc.perform(get("/admin/don-hang").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý đơn hàng")))
                .andExpect(content().string(containsString(orderCode)))
                .andExpect(content().string(containsString("Trần Văn Khách")));

        mockMvc.perform(get("/admin/don-hang/" + orderId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(orderCode)))
                .andExpect(content().string(containsString("Vợt cầu lông chạy thử quản trị đơn")))
                .andExpect(content().string(containsString("Đổi trạng thái đơn")))
                .andExpect(content().string(containsString("Xác nhận đã nhận tiền")))
                .andExpect(content().string(containsString("Khách đặt hàng trên website")));
    }

    @Test
    @DisplayName("Nút Xác nhận ở danh sách: đơn sang CONFIRMED, TRỪ KHO và ghi lịch sử")
    void quickConfirm_deductsStock() throws Exception {
        MockHttpSession customerSession = loginSession(customer);
        Integer orderId = placeOrder(customerSession, "COD");
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/xac-nhan").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/don-hang/" + orderId))
                .andExpect(flash().attribute("successMessage", containsString("Đã xác nhận đơn")));

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.isStockDeducted()).isTrue();
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity()).isEqualTo(9);
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("Đổi trạng thái qua form admin: SHIPPING → DELIVERED (COD đã trả); chuyển sai luật bị chặn")
    void changeStatusThroughAdminForm() throws Exception {
        Integer orderId = placeOrder(loginSession(customer), "COD");
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/xac-nhan").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/trang-thai").with(csrf()).session(adminSession)
                        .param("target", "SHIPPING")
                        .param("note", "Đã giao cho đơn vị vận chuyển"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đang giao hàng")));

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/trang-thai").with(csrf()).session(adminSession)
                        .param("target", "DELIVERED")
                        .param("note", "Giao thành công, khách đã trả tiền mặt"))
                .andExpect(status().is3xxRedirection());

        Order delivered = orderRepository.findById(orderId).orElseThrow();
        assertThat(delivered.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.isStockDeducted()).isTrue();
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();

        // DELIVERED ⇒ CONFIRMED là chuyển sai luật, phải bị chặn và giữ nguyên trạng thái
        mockMvc.perform(post("/admin/don-hang/" + orderId + "/trang-thai").with(csrf()).session(adminSession)
                        .param("target", "CONFIRMED")
                        .param("note", "Thử chuyển lùi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("Không thể chuyển đơn")));

        assertThat(orderRepository.findById(orderId).orElseThrow().getOrderStatus())
                .isEqualTo(OrderStatus.DELIVERED);
        // 4 dòng lịch sử: tạo đơn → xác nhận → giao hàng → giao thành công (lần chuyển sai không ghi)
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(4);
    }

    @Test
    @DisplayName("Admin xác nhận thanh toán chuyển khoản: ghi mã giao dịch, lần hai bị chặn")
    void confirmPaymentByAdmin() throws Exception {
        Integer orderId = placeOrder(loginSession(customer), "BANK_TRANSFER");
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/xac-nhan-thanh-toan").with(csrf()).session(adminSession)
                        .param("transactionCode", "FT20261016"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã xác nhận thanh toán")));

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getTransactionCode()).isEqualTo("FT20261016");

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/xac-nhan-thanh-toan").with(csrf()).session(adminSession)
                        .param("transactionCode", "FT999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("đã được xác nhận thanh toán")));
    }

    @Test
    @DisplayName("Danh sách admin lọc theo trạng thái + từ khoá mã đơn")
    void adminListFilters() throws Exception {
        Integer orderId = placeOrder(loginSession(customer), "COD");
        String orderCode = orderRepository.findById(orderId).orElseThrow().getOrderCode();
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/don-hang").session(adminSession)
                        .param("status", "PENDING")
                        .param("keyword", orderCode))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(orderCode)));

        mockMvc.perform(get("/admin/don-hang").session(adminSession)
                        .param("status", "CANCELLED")
                        .param("keyword", orderCode))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có đơn hàng nào phù hợp bộ lọc")));

        mockMvc.perform(get("/admin/don-hang").session(adminSession)
                        .param("status", "PENDING")
                        .param("keyword", "khong-co-don-nao-nhu-the"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có đơn hàng nào phù hợp bộ lọc")));
    }

    @Test
    @DisplayName("Trang tạo đơn thủ công: hiện form và gợi ý SKU đang bán")
    void manualOrderFormRenders() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/don-hang/tao-don").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tạo đơn thủ công")))
                .andExpect(content().string(containsString("skuOptions")))
                .andExpect(content().string(containsString(variant.getSku())))
                .andExpect(content().string(containsString(customer.getEmail())));
    }

    @Test
    @DisplayName("Tạo đơn thủ công tiền mặt tại quầy: MANUAL, xác nhận ngay ⇒ trừ kho, payment PAID")
    void createManualOrder_walkInCash() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        Integer orderId = createManualOrderThroughWeb(adminSession, variant.getSku(), 2, "CASH", true);

        Order created = orderRepository.findById(orderId).orElseThrow();
        assertThat(created.getOrderSource()).isEqualTo(OrderSource.MANUAL);
        assertThat(created.getUser()).isNull();                             // khách vãng lai
        assertThat(created.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(created.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(created.isStockDeducted()).isTrue();
        assertThat(created.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(created.getShippingFee()).isEqualByComparingTo("0");
        assertThat(created.getTotalAmount()).isEqualByComparingTo("2980000");
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        // 2 dòng lịch sử: tạo đơn + xác nhận ngay
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId)).hasSize(2);
    }

    @Test
    @DisplayName("Tạo đơn thủ công với SKU lạ: hiện lại form kèm lý do, KHÔNG tạo đơn nào")
    void createManualOrder_unknownSkuShowsFormError() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        long ordersBefore = orderRepository.count();

        mockMvc.perform(post("/admin/don-hang/tao-don").with(csrf()).session(adminSession)
                        .param("receiverName", "Khách mua tại quầy")
                        .param("receiverPhone", "0901112223")
                        .param("shippingAddress", "Mua trực tiếp tại cửa hàng BadStore")
                        .param("paymentMethod", "CASH")
                        .param("confirmNow", "true")
                        .param("lines[0].sku", "SKU-KHONG-TON-TAI")
                        .param("lines[0].quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không tìm thấy SKU")))
                .andExpect(content().string(containsString("Tạo đơn thủ công")));

        assertThat(orderRepository.count()).isEqualTo(ordersBefore);
    }

    @Test
    @DisplayName("Khách thường không vào được khu quản trị (403)")
    void customerCannotAccessAdmin() throws Exception {
        MockHttpSession customerSession = loginSession(customer);

        mockMvc.perform(get("/admin").session(customerSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/don-hang").session(customerSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/don-hang/1/xac-nhan").with(csrf()).session(customerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Khách vãng lai bị đưa về trang đăng nhập khi vào /admin")
    void anonymousRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dang-nhap"));
    }
}
