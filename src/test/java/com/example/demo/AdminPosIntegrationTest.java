package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Order;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
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

/**
 * Test tích hợp màn hình BÁN HÀNG TẠI QUẦY trên CSDL thật:
 * tìm/quét SKU → tính tiền ở server → thu tiền mặt (kiểm tra tiền thừa) → đơn MANUAL được XÁC NHẬN
 * và TRỪ KHO ngay → in phiếu. Kèm các trường hợp chặn: sai SKU, vượt tồn kho, tiền khách đưa thiếu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminPosIntegrationTest {

    private static final String PASSWORD = "Secret123";
    private static final String PRICE = "1490000";

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    private User admin;
    private Product product;
    private ProductVariant variant;

    /** Đơn do quầy tạo trong test để dọn sạch sau mỗi bài (đơn khách lẻ nên không tìm theo user được). */
    private final List<Integer> createdOrderIds = new ArrayList<>();

    /** Tài khoản khách phát sinh thêm trong một số bài test (dọn sau khi đã xoá đơn). */
    private User extraCustomer;

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());

        admin = new User();
        admin.setEmail("quay." + suffix + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFullName("Nhân viên quầy test");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        product = new Product();
        product.setProductCode("POS-" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt POS kiểm thử " + suffix.substring(suffix.length() - 4));
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("POS-SKU-" + suffix.substring(suffix.length() - 8));
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
        if (extraCustomer != null) {
            userRepository.deleteById(extraCustomer.getId());
        }
        productVariantRepository.delete(variant);
        productRepository.delete(product);
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

    private int stockOfVariant() {
        return productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity();
    }

    @Test
    @DisplayName("Mở màn hình quầy: có ô quét SKU, hoá đơn, khối thanh toán và nút thu tiền")
    void posScreen_rendersSellingScreen() throws Exception {
        mockMvc.perform(get("/admin/ban-hang").session(loginSession(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bán hàng tại quầy")))
                .andExpect(content().string(containsString("Quét mã SKU")))
                .andExpect(content().string(containsString("Thu tiền")))
                .andExpect(content().string(containsString("Tiền mặt")));
    }

    @Test
    @DisplayName("Khách thường không vào được màn hình quầy (chỉ ADMIN)")
    void posScreen_requiresAdmin() throws Exception {
        User customer = userRepository.save(account("khach.pos." + System.nanoTime() + "@example.com",
                UserRole.CUSTOMER));
        try {
            mockMvc.perform(get("/admin/ban-hang").session(loginSession(customer)))
                    .andExpect(status().isForbidden());
        } finally {
            userRepository.delete(customer);
        }
    }

    @Test
    @DisplayName("Tìm sản phẩm ở quầy: quét SKU ra đúng biến thể kèm giá và tồn kho")
    void search_returnsVariantBySku() throws Exception {
        mockMvc.perform(get("/admin/ban-hang/tim").session(loginSession(admin))
                        .param("keyword", variant.getSku()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value(variant.getSku()))
                .andExpect(jsonPath("$[0].productName").value(product.getName()))
                .andExpect(jsonPath("$[0].variantLabel").value("Đen / 4U / G5"))
                .andExpect(jsonPath("$[0].price").value(Integer.valueOf(PRICE)))
                .andExpect(jsonPath("$[0].stock").value(10))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    @DisplayName("Tính tiền tạm thời ở quầy: giá lấy từ DB, không tin số tiền từ trình duyệt")
    void priceEndpoint_computesFromDatabase() throws Exception {
        String body = "{\"voucherCode\":null,\"lines\":[{\"sku\":\"" + variant.getSku()
                + "\",\"quantity\":2}]}";

        mockMvc.perform(post("/admin/ban-hang/tinh-tien").with(csrf()).session(loginSession(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(2980000))
                .andExpect(jsonPath("$.total").value(2980000))
                .andExpect(jsonPath("$.lines[0].stock").value(10));
    }

    @Test
    @DisplayName("Thu tiền mặt: đơn MANUAL xác nhận ngay, trừ kho 2 cây, payment PAID, tiền thừa ghi vào hoá đơn")
    void sell_cash_deductsStockAndPrintsReceipt() throws Exception {
        MockHttpSession session = loginSession(admin);

        MvcResult sellResult = mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", variant.getSku())
                        .param("lines[0].quantity", "2")
                        .param("paymentMethod", "CASH")
                        .param("cashReceived", "3000000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("đã trừ kho")))
                .andReturn();

        String redirect = sellResult.getResponse().getRedirectedUrl();
        assertThat(redirect).startsWith("/admin/ban-hang/phieu/");
        Integer orderId = Integer.valueOf(redirect.substring(redirect.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);

        // Kho bị trừ ngay khi bán tại quầy
        assertThat(stockOfVariant()).isEqualTo(8);

        Order order = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getOrderSource()).isEqualTo(OrderSource.MANUAL);
        assertThat(order.isStockDeducted()).isTrue();
        assertThat(order.getUser()).isNull();
        assertThat(order.getReceiverName()).isEqualTo("Khách lẻ tại quầy");
        assertThat(order.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("2980000");
        assertThat(order.getNote()).contains("Bán hàng tại quầy").contains("Khách đưa 3.000.000 ₫")
                .contains("thừa 20.000 ₫");
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(order.getItems().get(0).getSku()).isEqualTo(variant.getSku());

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Phiếu in hiện đúng mã đơn, người nhận và nội dung cần cho khách
        mockMvc.perform(get(redirect).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Phiếu bán hàng")))
                .andExpect(content().string(containsString(order.getOrderCode())))
                .andExpect(content().string(containsString("Khách lẻ tại quầy")))
                .andExpect(content().string(containsString("Cảm ơn quý khách")));
    }

    @Test
    @DisplayName("Bán tại quầy khi vượt tồn kho: hiển thị lỗi ngay trên màn hình quầy, KHÔNG trừ kho")
    void sell_rejectsQuantityOverStock() throws Exception {
        MockHttpSession session = loginSession(admin);

        mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", variant.getSku())
                        .param("lines[0].quantity", "99")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chưa bán được hoá đơn này")))
                .andExpect(content().string(containsString("chỉ còn 10")));

        assertThat(stockOfVariant()).isEqualTo(10);
    }

    @Test
    @DisplayName("Tiền khách đưa thiếu: chặn lại, không có đơn rác nào được tạo")
    void sell_rejectsShortCash() throws Exception {
        MockHttpSession session = loginSession(admin);

        mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", variant.getSku())
                        .param("lines[0].quantity", "1")
                        .param("paymentMethod", "CASH")
                        .param("cashReceived", "100000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cần thu thêm")));

        assertThat(stockOfVariant()).isEqualTo(10);
        assertThat(createdOrderIds).isEmpty();
    }

    @Test
    @DisplayName("Quét sai SKU: báo lỗi rõ ràng cho nhân viên quầy")
    void sell_rejectsUnknownSku() throws Exception {
        MockHttpSession session = loginSession(admin);

        mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", "KHONG-TON-TAI")
                        .param("lines[0].quantity", "1")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("không tìm thấy SKU")));
    }

    @Test
    @DisplayName("Chuyển khoản + tích đã nhận tiền: hoá đơn được đánh dấu đã thanh toán")
    void sell_transferMarksPaid() throws Exception {
        MockHttpSession session = loginSession(admin);

        MvcResult result = mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", variant.getSku())
                        .param("lines[0].quantity", "1")
                        .param("paymentMethod", "BANK_TRANSFER")
                        .param("transferConfirmed", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Integer orderId = Integer.valueOf(result.getResponse().getRedirectedUrl()
                .substring(result.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);

        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Khách quen để lại SĐT: hoá đơn được gắn vào tài khoản khách và ghi tên khách")
    void sell_attachesRegisteredCustomer() throws Exception {
        User customer = account("khach.quen." + System.nanoTime() + "@example.com", UserRole.CUSTOMER);
        customer.setPhone("0987654321");
        extraCustomer = userRepository.save(customer);

        MockHttpSession session = loginSession(admin);

        MvcResult result = mockMvc.perform(post("/admin/ban-hang").with(csrf()).session(session)
                        .param("lines[0].sku", variant.getSku())
                        .param("lines[0].quantity", "1")
                        .param("paymentMethod", "CASH")
                        .param("customerPhone", "0987654321"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Integer orderId = Integer.valueOf(result.getResponse().getRedirectedUrl()
                .substring(result.getResponse().getRedirectedUrl().lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);

        Order order = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(order.getUser()).isNotNull();
        assertThat(order.getUser().getId()).isEqualTo(extraCustomer.getId());
        assertThat(order.getReceiverPhone()).isEqualTo("0987654321");

        mockMvc.perform(get("/admin/ban-hang/khach").session(session).param("phone", "0987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.email").value(extraCustomer.getEmail()));
    }

    private User account(String email, UserRole role) {
        User created = new User();
        created.setEmail(email);
        created.setPasswordHash(passwordEncoder.encode(PASSWORD));
        created.setFullName(role == UserRole.ADMIN ? "Quản trị test" : "Khách test");
        created.setRole(role);
        created.setStatus(UserStatus.ACTIVE);
        return created;
    }
}
