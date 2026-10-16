package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.junit.jupiter.api.AfterEach;
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
 * Test tích hợp (dùng DB thật) cho toàn bộ luồng xác thực: đăng ký → đăng nhập → flash → phân quyền → đăng xuất.
 * Bổ sung cho AuthServiceTest vì ở đây kiểm chứng cả Spring Security, CSRF, Thymeleaf và tiếng Việt có dấu.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String TEST_EMAIL = "flow.test@example.com";
    private static final String TEST_PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(userRepository::delete);
    }

    private void createCustomer() {
        User user = new User();
        user.setEmail(TEST_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD));
        user.setFullName("Khách Chạy Thử");
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    /** Đăng nhập đúng như form thật: POST /dang-nhap kèm token CSRF và tham số email/mật khẩu. */
    private MvcResult login(MockHttpSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", email)
                        .param("password", password)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    @Test
    @DisplayName("Đăng ký qua form thật: lưu mật khẩu BCrypt và chuyển sang trang đăng nhập (PRG)")
    void registerThroughForm() throws Exception {
        String email = "dang.ky." + System.nanoTime() + "@example.com";
        try {
            mockMvc.perform(post("/dang-ky").with(csrf())
                            .param("email", email)
                            .param("fullName", "Nguyễn Văn Đăng Ký")
                            .param("phone", "0912345678")
                            .param("password", TEST_PASSWORD)
                            .param("confirmPassword", TEST_PASSWORD))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/dang-nhap"))
                    .andExpect(flash().attribute("successMessage", containsString("Đăng ký thành công")));

            User saved = userRepository.findByEmail(email).orElseThrow();
            assertThat(saved.getPasswordHash()).startsWith("$2");
            assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
            assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        } finally {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    @DisplayName("Đăng ký không hợp lệ: hiển thị lại form kèm lỗi từng ô, không lưu gì")
    void registerWithInvalidDataShowsFieldErrors() throws Exception {
        mockMvc.perform(post("/dang-ky").with(csrf())
                        .param("email", "khong-phai-email")
                        .param("fullName", "")
                        .param("password", "123")
                        .param("confirmPassword", "123"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("is-invalid")));
    }

    @Test
    @DisplayName("Đăng ký trùng email: form hiện lỗi tiếng Việt, không tạo thêm tài khoản")
    void registerDuplicateEmailShowsError() throws Exception {
        createCustomer();

        mockMvc.perform(post("/dang-ky").with(csrf())
                        .param("email", TEST_EMAIL)
                        .param("fullName", "Khách Chạy Thử")
                        .param("password", TEST_PASSWORD)
                        .param("confirmPassword", TEST_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được sử dụng")));

        assertThat(userRepository.findByEmail(TEST_EMAIL).orElseThrow().getFullName()).isEqualTo("Khách Chạy Thử");
    }

    @Test
    @DisplayName("Đăng nhập thành công: flash tiếng Việt được lưu và hiển thị ở trang chủ")
    void loginSucceedsAndFlashMessageIsRendered() throws Exception {
        createCustomer();
        MockHttpSession session = new MockHttpSession();

        MvcResult loginResult = login(session, TEST_EMAIL, TEST_PASSWORD);
        assertThat(loginResult.getResponse().getRedirectedUrl()).endsWith("/").doesNotContain("error");

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng nhập thành công. Xin chào Khách Chạy Thử!")))
                .andExpect(content().string(containsString("Khách Chạy Thử")))
                .andExpect(content().string(containsString("Đăng xuất")));
    }

    @Test
    @DisplayName("Đăng nhập sai mật khẩu: quay lại trang đăng nhập kèm cờ lỗi")
    void loginFailsWithWrongPassword() throws Exception {
        createCustomer();

        MvcResult result = login(new MockHttpSession(), TEST_EMAIL, "sai-mat-khau");

        assertThat(result.getResponse().getRedirectedUrl()).contains("/dang-nhap?error");
    }

    @Test
    @DisplayName("Phân quyền: chưa đăng nhập bị chuyển hướng, CUSTOMER bị chặn 403, ADMIN đi qua được")
    void authorisationRules() throws Exception {
        mockMvc.perform(get("/admin/bat-ky"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/admin/bat-ky").with(user("khach@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/bat-ky").with(user("admin@badstore.local").roles("ADMIN")))
                .andExpect(status().isNotFound());   // đã qua phân quyền, chỉ là chưa có controller
    }

    @Test
    @DisplayName("Đăng xuất bằng POST: kết thúc phiên và quay về trang đăng nhập")
    void logoutWorks() throws Exception {
        createCustomer();
        MockHttpSession session = new MockHttpSession();
        login(session, TEST_EMAIL, TEST_PASSWORD);

        MvcResult logoutResult = mockMvc.perform(post("/dang-xuat").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(logoutResult.getResponse().getRedirectedUrl()).contains("/dang-nhap?logout");

        mockMvc.perform(get("/admin/bat-ky").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Admin do DataInitializer tạo phải đăng nhập được bằng mật khẩu cấu hình")
    void seededAdminCanLogIn() throws Exception {
        MvcResult result = login(new MockHttpSession(), "admin@badstore.local", "Admin@123");

        assertThat(result.getResponse().getRedirectedUrl()).doesNotContain("error");
    }
}
