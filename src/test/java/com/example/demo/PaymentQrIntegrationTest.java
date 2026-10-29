package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Order;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Mã QR chuyển khoản trên CSDL thật: QR của quầy (POS), dùng lại đúng mã đơn khi bán, và trang đơn
 * hàng của khách/admin chỉ hiện QR khi đơn còn chờ thanh toán chuyển khoản.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentQrIntegrationTest {

    private static final String PASSWORD = "Secret123";
    private static final String PRICE = "1490000";
    private static final Pattern ORDER_CODE_IN_JSON = Pattern.compile("\"orderCode\"\\s*:\\s*\"(BS\\d{15})\"");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BrandRepository brandRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductVariantRepository productVariantRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private OrderStatusHistoryRepository historyRepository;

    private final List<Integer> createdOrderIds = new ArrayList<>();
    private User admin;
    private User customer;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());
        String tail = suffix.substring(suffix.length() - 8);

        admin = account("qr.admin." + suffix + "@example.com", UserRole.ADMIN, null);
        customer = account("qr.khach." + suffix + "@example.com", UserRole.CUSTOMER, "09" + tail);

        product = new Product();
        product.setProductCode("QR-" + tail);
        product.setName("Vợt QR kiểm thử " + tail);
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("QR-SKU-" + tail);
        variant.setColor("Đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal(PRICE));
        variant.setStockQuantity(10);
        variant.setStatus(VariantStatus.ACTIVE);
        variant = productVariantRepository.save(variant);
    }

    @AfterEach
    void cleanUp() {
        for (Integer orderId : createdOrderIds) {
            historyRepository.deleteAll(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId));
            paymentRepository.findByOrderId(orderId).ifPresent(paymentRepository::delete);
            orderItemRepository.deleteAll(orderItemRepository.findByOrderIdOrderByProductVariantIdAsc(orderId));
            orderRepository.deleteById(orderId);
        }
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(customer);
        userRepository.delete(admin);
    }

    private User account(String email, UserRole role, String phone) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Tài khoản QR test");
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private MockHttpSession loginSession(User account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", account.getEmail()).param("password", PASSWORD).session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private String qrBody(String sku, int quantity, String orderCode) {
        String code = orderCode == null ? "null" : "\"" + orderCode + "\"";
        return "{\"voucherCode\":null,\"orderCode\":" + code
                + ",\"lines\":[{\"sku\":\"" + sku + "\",\"quantity\":" + quantity + "}]}";
    }

    private String requestQr(MockHttpSession session, String sku, int quantity, String orderCode) throws Exception {
        return mockMvc.perform(post("/admin/ban-hang/ma-qr").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(sku, quantity, orderCode)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String codeFrom(String json) {
        Matcher matcher = ORDER_CODE_IN_JSON.matcher(json);
        assertThat(matcher.find()).as("orderCode trong JSON: %s", json).isTrue();
        return matcher.group(1);
    }

    private Integer sellByTransfer(MockHttpSession session, String orderCode, boolean confirmed, String phone)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/admin/ban-hang").with(csrf()).session(session)
                .param("lines[0].sku", variant.getSku())
                .param("lines[0].quantity", "2")
                .param("paymentMethod", "BANK_TRANSFER")
                .param("orderCode", orderCode)
                .param("transferConfirmed", String.valueOf(confirmed));
        if (phone != null) {
            request.param("customerPhone", phone);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        Integer orderId = Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);
        return orderId;
    }

    @Test
    @DisplayName("POS: /ma-qr trả QR đúng số tiền do server tính + mã đơn cấp sẵn, gọi lại thì giữ nguyên mã")
    void posQr_computesAmountOnServerAndKeepsOrderCode() throws Exception {
        MockHttpSession session = loginSession(admin);

        String first = requestQr(session, variant.getSku(), 2, null);
        String code = codeFrom(first);
        assertThat(first).contains("\"available\":true").contains("\"amount\":2980000")
                .contains("amount=2980000").contains("addInfo=" + code);

        // đổi số lượng ⇒ số tiền đổi theo, mã đơn giữ nguyên khi gửi lại
        String again = requestQr(session, variant.getSku(), 3, code);
        assertThat(again).contains("\"amount\":4470000");
        assertThat(codeFrom(again)).isEqualTo(code);
    }

    @Test
    @DisplayName("POS: hoá đơn trống / sai SKU thì không dựng QR; khách thường không gọi được endpoint")
    void posQr_rejectsEmptyInvalidAndNonAdmin() throws Exception {
        MockHttpSession session = loginSession(admin);

        mockMvc.perform(post("/admin/ban-hang/ma-qr").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voucherCode\":null,\"orderCode\":null,\"lines\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/admin/ban-hang/ma-qr").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody("KHONG-CO-SKU-NAY", 1, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/admin/ban-hang/ma-qr").with(csrf()).session(loginSession(customer))
                        .contentType(MediaType.APPLICATION_JSON).content(qrBody(variant.getSku(), 1, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POS: bán chuyển khoản dùng đúng mã đơn đã in trong QR; mã giả mạo hoặc đã dùng bị bỏ")
    void sell_transfer_usesReservedCodeButNotForgedOne() throws Exception {
        MockHttpSession session = loginSession(admin);
        String code = codeFrom(requestQr(session, variant.getSku(), 2, null));

        Integer orderId = sellByTransfer(session, code, true, null);
        Order order = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(order.getOrderCode()).isEqualTo(code);
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        Integer forgedId = sellByTransfer(session, "HACKED-CODE", true, null);
        assertThat(orderRepository.findDetailForAdmin(forgedId).orElseThrow().getOrderCode())
                .matches("BS\\d{15}").isNotEqualTo("HACKED-CODE");

        // mã đã dùng cho đơn trước ⇒ không được dùng lại cho đơn mới
        Integer reusedId = sellByTransfer(session, code, true, null);
        assertThat(orderRepository.findDetailForAdmin(reusedId).orElseThrow().getOrderCode()).isNotEqualTo(code);
    }

    @Test
    @DisplayName("Trang đơn: khách và admin thấy QR khi chuyển khoản còn chờ; hết QR sau khi admin xác nhận đã nhận tiền")
    void orderPages_showQrOnlyWhilePending() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        MockHttpSession customerSession = loginSession(customer);
        String code = codeFrom(requestQr(adminSession, variant.getSku(), 2, null));

        // đơn quầy gắn với khách theo SĐT, chưa tích "đã nhận tiền" ⇒ payment PENDING
        Integer orderId = sellByTransfer(adminSession, code, false, customer.getPhone());
        assertThat(paymentRepository.findByOrderId(orderId).orElseThrow().getPaymentStatus())
                .isEqualTo(PaymentStatus.PENDING);

        mockMvc.perform(get("/don-hang/" + orderId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quét mã QR để chuyển khoản")))
                .andExpect(content().string(containsString("https://img.vietqr.io/image/")))
                .andExpect(content().string(containsString("addInfo=" + code)))
                .andExpect(content().string(containsString("amount=2980000")));

        mockMvc.perform(get("/admin/don-hang/" + orderId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quét mã QR để chuyển khoản")));

        mockMvc.perform(post("/admin/don-hang/" + orderId + "/xac-nhan-thanh-toan").with(csrf())
                        .session(adminSession).param("transactionCode", "FT" + System.nanoTime() % 1_000_000))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/don-hang/" + orderId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("img.vietqr.io"))));
        mockMvc.perform(get("/admin/don-hang/" + orderId).session(adminSession))
                .andExpect(content().string(not(containsString("img.vietqr.io"))));
    }

    @Test
    @DisplayName("Đơn tiền mặt không hiện QR")
    void orderPage_cashOrderHasNoQr() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        MvcResult result = mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(adminSession)
                        .param("lines[0].sku", variant.getSku()).param("lines[0].quantity", "1")
                        .param("paymentMethod", "CASH").param("customerPhone", customer.getPhone()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String redirect = result.getResponse().getRedirectedUrl();
        Integer orderId = Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);

        mockMvc.perform(get("/don-hang/" + orderId).session(loginSession(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("img.vietqr.io"))))
                .andExpect(content().string(matchesPattern("(?s).*BS\\d{15}.*")));
    }
}
