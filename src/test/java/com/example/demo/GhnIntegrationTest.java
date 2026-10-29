package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Order;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAddress;
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
import com.example.demo.service.GhnAddressService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Test tích hợp TÍCH HỢP GHN ở chế độ ngoại tuyến (app.ghn.mock=true) – đúng cách chạy demo khi
 * không có mạng/token thật: địa chỉ Tỉnh–Huyện–Xã là dữ liệu GHN lưu sẵn ĐẦY ĐỦ (mã thật),
 * còn phí vận chuyển/vận đơn là số giả.
 * <ol>
 *   <li>API nội bộ /api/ghn/* trả Tỉnh–Huyện–Xã và phí vận chuyển cho giao diện;</li>
 *   <li>sổ địa chỉ lưu lại mã Tỉnh/Huyện/Xã của GHN (và BỎ mã sai để khách không đổi được phí ship);</li>
 *   <li>đơn hàng dùng PHÍ GHN (rẻ hơn 30.000 ₫ cố định) chứ không dùng giá trị trình duyệt gửi lên;</li>
 *   <li>admin đẩy đơn sang GHN lấy mã vận đơn rồi đồng bộ trạng thái "đã giao" về hệ thống.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "app.ghn.enabled=true",
        "app.ghn.mock=true",
        "app.ghn.from-district-id=1489",
        "app.ghn.mock-status=delivered",
        "app.ghn.sync-enabled=false"
})
@AutoConfigureMockMvc
class GhnIntegrationTest {

    /** Phí GHN giả nội thành cho kiện 1,8 kg: 19.000 + 2 × 2.500 = 24.000 ₫ (khác 30.000 ₫ mặc định). */
    private static final long GHN_FEE = 24000L;
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
    private UserAddressRepository addressRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderStatusHistoryRepository historyRepository;

    @Autowired
    private GhnAddressService ghnAddressService;

    private User customer;
    private Product product;
    private ProductVariant variant;

    private final List<Integer> createdOrderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = String.valueOf(System.nanoTime());

        customer = new User();
        customer.setEmail("ghn.khach." + suffix + "@example.com");
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        customer.setFullName("Khách GHN test");
        customer.setPhone("0901234567");
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        customer = userRepository.save(customer);

        product = new Product();
        product.setProductCode("GHN-" + suffix.substring(suffix.length() - 8));
        product.setName("Vợt GHN kiểm thử " + suffix.substring(suffix.length() - 4));
        product.setBrand(brandRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setCategory(categoryRepository.findByDeletedFalseOrderByNameAsc().get(0));
        product.setStatus(ProductStatus.ACTIVE);
        product = productRepository.save(product);

        variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("GHN-SKU-" + suffix.substring(suffix.length() - 8));
        variant.setColor("Xanh");
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
        cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(customer.getId()));
        addressRepository.deleteAll(addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId()));
        productVariantRepository.delete(variant);
        productRepository.delete(product);
        userRepository.delete(customer);
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

    /** Đăng nhập admin mặc định do DataInitializer tạo (xem docs/HUONG_DAN_CHAY.md). */
    private MockHttpSession adminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", "admin@badstore.local")
                        .param("password", "Admin@123")
                        .session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    /** Thêm 1 địa chỉ giao hàng có mã GHN thật (201/1489/1A0216 – Hà Nội, Quận Hoàn Kiếm, Phường Phúc Tân). */
    private void addGhnAddress(MockHttpSession session) throws Exception {
        mockMvc.perform(post("/dia-chi/them").with(csrf()).session(session)
                        .param("receiverName", "Khách GHN test")
                        .param("receiverPhone", "0901234567")
                        .param("provinceName", "Hà Nội")
                        .param("districtName", "Quận Hoàn Kiếm")
                        .param("wardName", "Phường Phúc Tân")
                        .param("addressDetail", "12 Hàng Bài")
                        .param("defaultAddress", "true")
                        .param("provinceId", "201")
                        .param("districtId", "1489")
                        .param("wardCode", "1A0216"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã lưu địa chỉ")));
    }

    /** Đặt 1 đơn COD giao về địa chỉ mặc định; trả về id đơn. */
    private Integer placeCodOrder(MockHttpSession session) throws Exception {
        UserAddress address = addressRepository
                .findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(customer.getId()).orElseThrow();

        mockMvc.perform(post("/gio-hang/them").with(csrf()).session(session)
                        .param("variantId", String.valueOf(variant.getId()))
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection());

        MvcResult result = mockMvc.perform(post("/thanh-toan").with(csrf()).session(session)
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD")
                        .param("note", "Đơn kiểm thử tích hợp GHN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/don-hang/*"))
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        Integer orderId = Integer.valueOf(location.substring(location.lastIndexOf('/') + 1));
        createdOrderIds.add(orderId);
        return orderId;
    }

    @Test
    @DisplayName("API địa chỉ GHN: trả Tỉnh → Huyện → Xã và phí vận chuyển tạm tính theo khối lượng")
    void ghnApis_serveAddressDataAndFee() throws Exception {
        MockHttpSession session = loginSession(customer);

        mockMvc.perform(get("/api/ghn/tinh").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.shippingEnabled").value(true))
                .andExpect(jsonPath("$.mock").value(true))
                // Dữ liệu GHN lưu sẵn là TOÀN BỘ (63 tỉnh/thành), không còn rút gọn vài tỉnh như trước
                .andExpect(jsonPath("$.items.length()").value(63))
                .andExpect(jsonPath("$.items[?(@.id == 201)].name").value(hasItem("Hà Nội")));

        mockMvc.perform(get("/api/ghn/huyen").session(session).param("provinceId", "201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(30))
                .andExpect(jsonPath("$.items[?(@.id == 1489)].name").value(hasItem("Quận Hoàn Kiếm")));

        mockMvc.perform(get("/api/ghn/xa").session(session).param("districtId", "1489"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(18))
                .andExpect(jsonPath("$.items[?(@.name == 'Phường Phúc Tân')].id").value(hasItem("1A0216")));

        mockMvc.perform(get("/api/ghn/phi-ship").session(session)
                        .param("districtId", "1489").param("wardCode", "1A0216").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.weightGram").value(1800))
                .andExpect(jsonPath("$.fee").value(GHN_FEE));
    }

    @Test
    @DisplayName("Sổ địa chỉ lưu mã GHN; mã sai bị BỎ (chống khách sửa mã để ăn phí ship rẻ)")
    void addressBook_keepsValidGhnCodesAndDropsInvalidOnes() throws Exception {
        MockHttpSession session = loginSession(customer);

        addGhnAddress(session);

        UserAddress saved = addressRepository
                .findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(customer.getId()).orElseThrow();
        assertThat(saved.getProvinceId()).isEqualTo(201);
        assertThat(saved.getDistrictId()).isEqualTo(1489);
        assertThat(saved.getWardCode()).isEqualTo("1A0216");

        // Gửi mã bịa: server phải kiểm tra lại với dữ liệu GHN rồi bỏ đi, không lưu bừa.
        mockMvc.perform(post("/dia-chi/them").with(csrf()).session(session)
                        .param("receiverName", "Khách GHN test")
                        .param("receiverPhone", "0901234567")
                        .param("provinceName", "Tỉnh Bịa")
                        .param("districtName", "Huyện Bịa")
                        .param("wardName", "Xã Bịa")
                        .param("addressDetail", "99 Đường Bịa")
                        .param("provinceId", "999999")
                        .param("districtId", "888888")
                        .param("wardCode", "XXXXXX"))
                .andExpect(status().is3xxRedirection());

        List<UserAddress> addresses = addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId());
        assertThat(addresses).hasSize(2);
        UserAddress bogus = addresses.stream()
                .filter(address -> "99 Đường Bịa".equals(address.getAddressDetail()))
                .findFirst().orElseThrow();
        assertThat(bogus.getProvinceId()).isNull();
        assertThat(bogus.getDistrictId()).isNull();
        assertThat(bogus.getWardCode()).isNull();

        // Mã GHN ĐÚNG nhưng tên địa danh khách sửa thành nơi khác ⇒ cũng phải bỏ mã (chống lệch tên–mã).
        mockMvc.perform(post("/dia-chi/them").with(csrf()).session(session)
                        .param("receiverName", "Khách GHN test")
                        .param("receiverPhone", "0901234567")
                        .param("provinceName", "Đà Nẵng")
                        .param("districtName", "Quận Hải Châu")
                        .param("wardName", "Phường Thạch Thang")
                        .param("addressDetail", "88 Bạch Đằng")
                        .param("provinceId", "201")
                        .param("districtId", "1489")
                        .param("wardCode", "1A0216"))
                .andExpect(status().is3xxRedirection());

        UserAddress mismatched = addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId()).stream()
                .filter(address -> "88 Bạch Đằng".equals(address.getAddressDetail()))
                .findFirst().orElseThrow();
        assertThat(mismatched.getProvinceName()).isEqualTo("Đà Nẵng");
        assertThat(mismatched.getProvinceId()).isNull();
        assertThat(mismatched.getDistrictId()).isNull();
        assertThat(mismatched.getWardCode()).isNull();
    }

    @Test
    @DisplayName("Sổ địa chỉ: địa chỉ lấy từ dropdown GHN (Tỉnh–Huyện–Xã), không còn ô nhập tay trùng lặp")
    void addressBook_usesGhnDropdowns() throws Exception {
        MockHttpSession session = loginSession(customer);

        mockMvc.perform(get("/dia-chi").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-ghn-province")))
                .andExpect(content().string(containsString("data-ghn-district")))
                .andExpect(content().string(containsString("data-ghn-ward")))
                .andExpect(content().string(containsString("data-ghn-ward-name")))
                .andExpect(content().string(containsString(">Hà Nội</option>")))
                // GHN đang dùng được nên 3 ô nhập tay (placeholder riêng của khối dự phòng) không render
                .andExpect(content().string(not(containsString("Ví dụ: TP. Hồ Chí Minh"))));
    }

    @Test
    @DisplayName("Sổ địa chỉ: GHN đang dùng được thì bắt buộc chọn đủ Tỉnh–Huyện–Xã, lỗi hiện cạnh ô chọn")
    void addressBook_requiresFullGhnLocation() throws Exception {
        MockHttpSession session = loginSession(customer);
        int before = addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId()).size();

        mockMvc.perform(post("/dia-chi/them").with(csrf()).session(session)
                        .param("receiverName", "Khách thiếu địa chỉ GHN")
                        .param("receiverPhone", "0901234567")
                        .param("provinceName", "Hà Nội")
                        .param("districtName", "Quận Hoàn Kiếm")
                        .param("wardName", "Phường Phúc Tân")
                        .param("addressDetail", "1 Tràng Tiền"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng chọn tỉnh/thành phố.")))
                .andExpect(content().string(containsString("Vui lòng chọn quận/huyện.")))
                .andExpect(content().string(containsString("Vui lòng chọn phường/xã.")));

        assertThat(addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId()))
                .hasSize(before);

        // Đã chọn đủ Tỉnh–Huyện–Xã nhưng thiếu số nhà: form giữ lại lựa chọn cho JavaScript khôi phục dropdown
        mockMvc.perform(post("/dia-chi/them").with(csrf()).session(session)
                        .param("receiverName", "Khách thiếu số nhà")
                        .param("receiverPhone", "0901234567")
                        .param("provinceName", "Hà Nội")
                        .param("districtName", "Quận Hoàn Kiếm")
                        .param("wardName", "Phường Phúc Tân")
                        .param("provinceId", "201")
                        .param("districtId", "1489")
                        .param("wardCode", "1A0216"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập số nhà, tên đường.")))
                .andExpect(content().string(containsString("data-ghn-district=\"1489\"")))
                .andExpect(content().string(containsString("data-ghn-ward=\"1A0216\"")));

        assertThat(addressRepository
                .findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(customer.getId()))
                .hasSize(before);
    }

    @Test
    @DisplayName("Đặt hàng: phí vận chuyển lấy từ GHN + lưu mã địa chỉ GHN vào đơn để đẩy vận đơn sau này")
    void checkout_usesGhnFeeAndStoresCodes() throws Exception {
        MockHttpSession session = loginSession(customer);
        addGhnAddress(session);
        Integer orderId = placeCodOrder(session);

        Order order = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(order.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(GHN_FEE));
        assertThat(order.getTotalAmount()).isEqualByComparingTo("3004000");
        assertThat(order.getShippingProvinceId()).isEqualTo(201);
        assertThat(order.getShippingDistrictId()).isEqualTo(1489);
        assertThat(order.getShippingWardCode()).isEqualTo("1A0216");
        // Đặt hàng chưa trừ kho – kho chỉ trừ khi xác nhận
        assertThat(order.isStockDeducted()).isFalse();
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(ghnAddressService.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Đẩy đơn sang GHN lấy mã vận đơn rồi đồng bộ \"đã giao\" ⇒ đơn giao thành công, COD thu tiền")
    void pushAndSync_bringsOrderToDelivered() throws Exception {
        MockHttpSession shopSession = loginSession(customer);
        addGhnAddress(shopSession);
        Integer orderId = placeCodOrder(shopSession);

        MockHttpSession admin = adminSession();

        // 1) Xác nhận đơn: PENDING → CONFIRMED (trừ kho)
        mockMvc.perform(post("/admin/don-hang/" + orderId + "/trang-thai").with(csrf()).session(admin)
                        .param("target", "CONFIRMED").param("note", "Xác nhận để gửi GHN"))
                .andExpect(status().is3xxRedirection());
        assertThat(productVariantRepository.findById(variant.getId()).orElseThrow().getStockQuantity())
                .isEqualTo(8);

        // 2) Đẩy sang GHN: lấy mã vận đơn, ghi vào đơn
        mockMvc.perform(post("/admin/don-hang/" + orderId + "/ghn/tao-van-don").with(csrf()).session(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("GHN")));

        Order afterPush = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(afterPush.getGhnOrderCode()).startsWith("GHNMOCK");

        // Trang chi tiết đơn (admin) hiển thị khối vận chuyển GHN kèm mã vận đơn
        mockMvc.perform(get("/admin/don-hang/" + orderId).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vận chuyển GHN")))
                .andExpect(content().string(containsString(afterPush.getGhnOrderCode())));

        // 3) Đồng bộ trạng thái: GHN (mock) báo "delivered" ⇒ CONFIRMED → SHIPPING → DELIVERED
        mockMvc.perform(post("/admin/don-hang/" + orderId + "/ghn/dong-bo").with(csrf()).session(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Giao thành công")));

        Order delivered = orderRepository.findDetailForAdmin(orderId).orElseThrow();
        assertThat(delivered.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.isStockDeducted()).isTrue();
        assertThat(delivered.getItems()).hasSize(1);
        // COD giao thành công ⇒ đánh dấu đã thanh toán
        Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Lịch sử đơn ghi rõ nguồn đồng bộ để tra cứu lại
        assertThat(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(orderId))
                .anySatisfy(history -> assertThat(history.getNote()).contains("Đồng bộ vận đơn GHN"));
    }
}
