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
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.repository.UserRepository;
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
 * Test tích hợp khu vực tài khoản phía khách: xem hồ sơ, cập nhật thông tin
 * (kể cả làm mới tên trên header), chống trùng email và đổi mật khẩu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private User customer;
    private final List<Integer> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        customer = createUser("khach.taikhoan");
    }

    @AfterEach
    void cleanUp() {
        for (Integer id : createdUserIds) {
            userRepository.findById(id).ifPresent(userRepository::delete);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Trang tài khoản hiển thị hồ sơ, số đơn, số địa chỉ và yêu cầu đăng nhập")
    void profile_requiresLoginAndShowsInfo() throws Exception {
        mockMvc.perform(get("/tai-khoan"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/tai-khoan").session(login(customer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin cá nhân")))
                .andExpect(content().string(containsString(customer.getFullName())))
                .andExpect(content().string(containsString(customer.getEmail())))
                .andExpect(content().string(containsString("Đơn hàng")))
                .andExpect(content().string(containsString("Địa chỉ")));
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: lưu họ tên/email/điện thoại và làm mới tên trên header ngay")
    void updateProfile_savesAndRefreshesSession() throws Exception {
        MockHttpSession session = login(customer);
        String newEmail = "moi." + suffix + "@example.com";

        mockMvc.perform(post("/tai-khoan").with(csrf()).session(session)
                        .param("fullName", "  Tên   Mới  ")
                        .param("email", newEmail)
                        .param("phone", "0987654321"))
                .andExpect(redirectedUrl("/tai-khoan"))
                .andExpect(flash().attribute("successMessage", containsString("Đã cập nhật")));

        User updated = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Tên Mới");
        assertThat(updated.getEmail()).isEqualTo(newEmail);
        assertThat(updated.getPhone()).isEqualTo("0987654321");

        // Trang chủ chỉ hiện họ tên trong header ⇒ đây là bằng chứng principal trong session đã được làm mới.
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tên Mới")));
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: email của tài khoản khác ⇒ báo lỗi, giữ email cũ")
    void updateProfile_rejectsDuplicateEmail() throws Exception {
        User other = createUser("khach.khac");
        String oldEmail = customer.getEmail();

        mockMvc.perform(post("/tai-khoan").with(csrf()).session(login(customer))
                        .param("fullName", customer.getFullName())
                        .param("email", other.getEmail())
                        .param("phone", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được tài khoản khác sử dụng")));

        assertThat(userRepository.findById(customer.getId()).orElseThrow().getEmail()).isEqualTo(oldEmail);
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: họ tên để trống ⇒ báo lỗi cạnh ô nhập")
    void updateProfile_validatesRequiredName() throws Exception {
        mockMvc.perform(post("/tai-khoan").with(csrf()).session(login(customer))
                        .param("fullName", "   ")
                        .param("email", customer.getEmail())
                        .param("phone", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập họ tên")));
    }

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu hiện tại sai ⇒ báo lỗi, không đổi")
    void changePassword_rejectsWrongCurrentPassword() throws Exception {
        MockHttpSession session = login(customer);

        mockMvc.perform(get("/tai-khoan/doi-mat-khau").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại")));

        mockMvc.perform(post("/tai-khoan/doi-mat-khau").with(csrf()).session(session)
                        .param("currentPassword", "SaiMatKhau")
                        .param("newPassword", "NewSecret123")
                        .param("confirmPassword", "NewSecret123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu hiện tại không đúng")));

        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(customer.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Đổi mật khẩu: xác nhận không khớp ⇒ báo lỗi cạnh ô nhập")
    void changePassword_rejectsMismatch() throws Exception {
        mockMvc.perform(post("/tai-khoan/doi-mat-khau").with(csrf()).session(login(customer))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "NewSecret123")
                        .param("confirmPassword", "KhacSecret123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật khẩu xác nhận không khớp")));
    }

    @Test
    @DisplayName("Đổi mật khẩu thành công: mật khẩu cũ hết hiệu lực, mật khẩu mới đăng nhập được")
    void changePassword_success_oldPasswordStopsWorking() throws Exception {
        MockHttpSession session = login(customer);

        mockMvc.perform(post("/tai-khoan/doi-mat-khau").with(csrf()).session(session)
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "NewSecret123")
                        .param("confirmPassword", "NewSecret123"))
                .andExpect(redirectedUrl("/tai-khoan"))
                .andExpect(flash().attribute("successMessage", containsString("Đã đổi mật khẩu")));

        String newHash = userRepository.findById(customer.getId()).orElseThrow().getPasswordHash();
        assertThat(passwordEncoder.matches("NewSecret123", newHash)).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, newHash)).isFalse();

        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", customer.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/dang-nhap?error"));

        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", customer.getEmail())
                        .param("password", "NewSecret123"))
                .andExpect(status().is3xxRedirection());
    }

    // ==================== HÀM HỖ TRỢ ====================

    private User createUser(String label) {
        User user = new User();
        user.setEmail(label + "." + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Người dùng " + label);
        user.setPhone("0912345678");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    private MockHttpSession login(User account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", account.getEmail())
                        .param("password", PASSWORD)
                        .session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }
}
