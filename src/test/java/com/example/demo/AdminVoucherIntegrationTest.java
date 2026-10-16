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

import com.example.demo.entity.User;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/**
 * Test tích hợp trang quản trị voucher: danh sách có lọc, tạo mới (chống trùng mã),
 * sửa, bật/ngừng áp dụng và hiển thị lỗi validate ngay trên form.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminVoucherIntegrationTest {

    private static final String PASSWORD = "Secret123";
    private static final String START_AT = "2026-10-01T00:00";
    private static final String END_AT = "2026-12-31T23:59";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private String code;
    private User admin;
    private Voucher voucher;

    private final List<Integer> createdVoucherIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        code = "VT" + suffix.substring(suffix.length() - 8);

        admin = new User();
        admin.setEmail("admin.voucher." + suffix + "@example.com");
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setFullName("Quản trị viên voucher");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);

        voucher = new Voucher();
        voucher.setCode(code);
        voucher.setName("Giảm 10% thử nghiệm");
        voucher.setDescription("Voucher do test tạo");
        voucher.setDiscountType(DiscountType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10"));
        voucher.setMinOrderValue(new BigDecimal("1000000"));
        voucher.setMaxDiscountAmount(new BigDecimal("200000"));
        voucher.setQuantity(20);
        voucher.setUsedQuantity(0);
        voucher.setStartAt(LocalDateTime.of(2026, 10, 1, 0, 0));
        voucher.setEndAt(LocalDateTime.of(2026, 12, 31, 23, 59));
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher = voucherRepository.save(voucher);
        createdVoucherIds.add(voucher.getId());
    }

    @AfterEach
    void cleanUp() {
        for (Integer id : createdVoucherIds) {
            voucherRepository.findById(id).ifPresent(voucherRepository::delete);
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

    @Test
    @DisplayName("Danh sách voucher: hiện voucher theo mã, lọc theo trạng thái và từ khoá")
    void list_showsAndFilters() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/voucher").session(adminSession).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý voucher")))
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("Giảm 10% thử nghiệm")))
                .andExpect(content().string(containsString("Đang áp dụng")));

        mockMvc.perform(get("/admin/voucher").session(adminSession)
                        .param("keyword", code)
                        .param("status", "INACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có voucher nào phù hợp bộ lọc")));
    }

    @Test
    @DisplayName("Tạo voucher qua form: mã được viết hoa, lưu DB và có flash thành công")
    void create_throughForm() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        String newCode = "vt" + suffix.substring(suffix.length() - 6);

        mockMvc.perform(post("/admin/voucher").with(csrf()).session(adminSession)
                        .param("code", newCode)
                        .param("name", "Giảm 50.000₫ cho đơn từ 2 triệu")
                        .param("description", "Áp dụng cho vợt cầu lông")
                        .param("discountType", "FIXED")
                        .param("discountValue", "50000")
                        .param("minOrderValue", "2000000")
                        .param("maxDiscountAmount", "")
                        .param("quantity", "15")
                        .param("startAt", START_AT)
                        .param("endAt", END_AT)
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/voucher"))
                .andExpect(flash().attribute("successMessage", containsString("Đã tạo voucher")));

        Voucher created = voucherRepository.findByCode(newCode.toUpperCase()).orElseThrow();
        createdVoucherIds.add(created.getId());
        assertThat(created.getDiscountType()).isEqualTo(DiscountType.FIXED);
        assertThat(created.getDiscountValue()).isEqualByComparingTo("50000");
        assertThat(created.getMinOrderValue()).isEqualByComparingTo("2000000");
        assertThat(created.getMaxDiscountAmount()).isNull();
        assertThat(created.getQuantity()).isEqualTo(15);
        assertThat(created.getUsedQuantity()).isZero();
    }

    @Test
    @DisplayName("Tạo voucher trùng mã: hiện lại form kèm lý do, không thêm bản ghi")
    void create_duplicateCodeShowsFormError() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        long before = voucherRepository.count();

        mockMvc.perform(post("/admin/voucher").with(csrf()).session(adminSession)
                        .param("code", code)
                        .param("name", "Voucher trùng mã")
                        .param("discountType", "PERCENT")
                        .param("discountValue", "5")
                        .param("minOrderValue", "0")
                        .param("quantity", "5")
                        .param("startAt", START_AT)
                        .param("endAt", END_AT)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã tồn tại")))
                .andExpect(content().string(containsString("Tạo voucher mới")));

        assertThat(voucherRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("Form voucher: lỗi Bean Validation hiện cạnh ô nhập (thiếu tên, phần trăm > 100)")
    void formShowsValidationErrors() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/voucher").with(csrf()).session(adminSession)
                        .param("code", "VT" + suffix.substring(suffix.length() - 6))
                        .param("name", "")
                        .param("discountType", "PERCENT")
                        .param("discountValue", "150")
                        .param("minOrderValue", "0")
                        .param("quantity", "5")
                        .param("startAt", START_AT)
                        .param("endAt", END_AT)
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập tên chương trình giảm giá.")));
    }

    @Test
    @DisplayName("Sửa voucher: đổi tên + số lượt, giữ nguyên lượt đã dùng")
    void edit_updatesVoucher() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        voucher.setUsedQuantity(2);
        voucherRepository.save(voucher);

        mockMvc.perform(get("/admin/voucher/" + voucher.getId() + "/sua").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("đã dùng")))
                .andExpect(content().string(containsString(">2<")));

        mockMvc.perform(post("/admin/voucher/" + voucher.getId()).with(csrf()).session(adminSession)
                        .param("code", code)
                        .param("name", "Giảm 15% mùa cuối năm")
                        .param("description", "Đã cập nhật")
                        .param("discountType", "PERCENT")
                        .param("discountValue", "15")
                        .param("minOrderValue", "1500000")
                        .param("maxDiscountAmount", "300000")
                        .param("quantity", "40")
                        .param("startAt", START_AT)
                        .param("endAt", END_AT)
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật voucher")));

        Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Giảm 15% mùa cuối năm");
        assertThat(updated.getDiscountValue()).isEqualByComparingTo("15");
        assertThat(updated.getMinOrderValue()).isEqualByComparingTo("1500000");
        assertThat(updated.getQuantity()).isEqualTo(40);
        assertThat(updated.getUsedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("Bật/ngừng áp dụng voucher: đổi trạng thái và ghi flash tương ứng")
    void toggleStatus_switches() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/voucher/" + voucher.getId() + "/doi-trang-thai").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã ngừng áp dụng")))
                .andExpect(redirectedUrl("/admin/voucher"));
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getStatus())
                .isEqualTo(VoucherStatus.INACTIVE);

        mockMvc.perform(post("/admin/voucher/" + voucher.getId() + "/doi-trang-thai").with(csrf()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("Đã bật áp dụng")));
        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getStatus())
                .isEqualTo(VoucherStatus.ACTIVE);
    }

    @Test
    @DisplayName("Khách không vào được trang quản trị voucher (403)")
    void customerCannotAccessVoucherAdmin() throws Exception {
        User customer = new User();
        customer.setEmail("khach.voucher." + suffix + "@example.com");
        customer.setPasswordHash(passwordEncoder.encode(PASSWORD));
        customer.setFullName("Khách thử");
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);
        customer = userRepository.save(customer);

        try {
            MockHttpSession customerSession = loginSession(customer);
            mockMvc.perform(get("/admin/voucher").session(customerSession)).andExpect(status().isForbidden());
            mockMvc.perform(post("/admin/voucher/" + voucher.getId() + "/doi-trang-thai").with(csrf()).session(customerSession))
                    .andExpect(status().isForbidden());
        } finally {
            userRepository.delete(customer);
        }
    }
}
