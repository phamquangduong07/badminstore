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
 * Test tích hợp trang quản trị người dùng: danh sách + bộ lọc, tạo tài khoản (băm BCrypt,
 * chống trùng email, bắt buộc mật khẩu), sửa vai trò/đặt lại mật khẩu và khoá/mở khoá.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest {

    private static final String PASSWORD = "Secret123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String suffix;
    private User admin;
    private final List<Integer> createdUserIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        suffix = String.valueOf(System.nanoTime());
        admin = createUser("admin.users", UserRole.ADMIN, UserStatus.ACTIVE);
    }

    @AfterEach
    void cleanUp() {
        for (Integer id : createdUserIds) {
            userRepository.findById(id).ifPresent(userRepository::delete);
        }
        createdUserIds.clear();
    }

    @Test
    @DisplayName("Danh sách người dùng: hiện đúng tài khoản theo từ khoá và bộ lọc vai trò/trạng thái")
    void list_showsAndFilters() throws Exception {
        User customer = createUser("khach.loc", UserRole.CUSTOMER, UserStatus.ACTIVE);
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/nguoi-dung").session(adminSession)
                        .param("keyword", customer.getEmail()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Quản lý người dùng")))
                .andExpect(content().string(containsString(customer.getEmail())))
                .andExpect(content().string(containsString(customer.getFullName())));

        mockMvc.perform(get("/admin/nguoi-dung").session(adminSession)
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE")
                        .param("keyword", customer.getEmail()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(customer.getEmail())));

        mockMvc.perform(get("/admin/nguoi-dung").session(adminSession)
                        .param("role", "ADMIN")
                        .param("keyword", customer.getEmail()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Không có tài khoản nào phù hợp bộ lọc.")));
    }

    @Test
    @DisplayName("Tạo tài khoản: lưu mật khẩu BCrypt và chặn email trùng")
    void create_storesBcryptPasswordAndRejectsDuplicate() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        String email = "tao.moi." + suffix + "@example.com";

        mockMvc.perform(post("/admin/nguoi-dung").with(csrf()).session(adminSession)
                        .param("fullName", "Khách hàng mới")
                        .param("email", email)
                        .param("phone", "0912345678")
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(redirectedUrl("/admin/nguoi-dung"))
                .andExpect(flash().attribute("successMessage", containsString(email)));

        User created = userRepository.findByEmail(email).orElseThrow();
        createdUserIds.add(created.getId());
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
        assertThat(created.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);

        mockMvc.perform(post("/admin/nguoi-dung").with(csrf()).session(adminSession)
                        .param("fullName", "Tài khoản trùng")
                        .param("email", email)
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE")
                        .param("password", PASSWORD)
                        .param("confirmPassword", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("đã được sử dụng")));
    }

    @Test
    @DisplayName("Tạo tài khoản thiếu mật khẩu ⇒ báo lỗi ngay cạnh ô nhập, không lưu")
    void create_withoutPassword_showsFieldError() throws Exception {
        MockHttpSession adminSession = loginSession(admin);
        String email = "thieu.mat.khau." + suffix + "@example.com";

        mockMvc.perform(post("/admin/nguoi-dung").with(csrf()).session(adminSession)
                        .param("fullName", "Khách thiếu mật khẩu")
                        .param("email", email)
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE")
                        .param("password", "")
                        .param("confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng nhập mật khẩu cho tài khoản mới.")));

        assertThat(userRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("Sửa tài khoản: đổi vai trò và đặt lại mật khẩu mới")
    void update_changesRoleAndPassword() throws Exception {
        User customer = createUser("khach.sua", UserRole.CUSTOMER, UserStatus.ACTIVE);
        MockHttpSession adminSession = loginSession(admin);
        String newPassword = "NewSecret123";

        mockMvc.perform(post("/admin/nguoi-dung/" + customer.getId()).with(csrf()).session(adminSession)
                        .param("fullName", "Nhân viên quản trị")
                        .param("email", customer.getEmail())
                        .param("phone", "0987654321")
                        .param("role", "ADMIN")
                        .param("status", "ACTIVE")
                        .param("password", newPassword)
                        .param("confirmPassword", newPassword))
                .andExpect(redirectedUrl("/admin/nguoi-dung"))
                .andExpect(flash().attribute("successMessage", containsString(customer.getEmail())));

        User updated = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(updated.getFullName()).isEqualTo("Nhân viên quản trị");
        assertThat(updated.getPhone()).isEqualTo("0987654321");
        assertThat(passwordEncoder.matches(newPassword, updated.getPasswordHash())).isTrue();

        // Sửa lần nữa nhưng để trống mật khẩu ⇒ giữ mật khẩu vừa đặt
        mockMvc.perform(post("/admin/nguoi-dung/" + customer.getId()).with(csrf()).session(adminSession)
                        .param("fullName", "Nhân viên quản trị")
                        .param("email", customer.getEmail())
                        .param("role", "ADMIN")
                        .param("status", "ACTIVE")
                        .param("password", "")
                        .param("confirmPassword", ""))
                .andExpect(redirectedUrl("/admin/nguoi-dung"));

        User unchangedPassword = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, unchangedPassword.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Khoá tài khoản: chặn tự khoá; khoá khách thì khách không đăng nhập được, mở khoá lại được")
    void toggleLock_blocksSelfAndControlsLogin() throws Exception {
        User customer = createUser("khach.khoa", UserRole.CUSTOMER, UserStatus.ACTIVE);
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(post("/admin/nguoi-dung/" + admin.getId() + "/khoa")
                        .with(csrf()).session(adminSession))
                .andExpect(redirectedUrl("/admin/nguoi-dung"))
                .andExpect(flash().attribute("errorMessage", containsString("tự khoá")));
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);

        mockMvc.perform(post("/admin/nguoi-dung/" + customer.getId() + "/khoa")
                        .with(csrf()).session(adminSession))
                .andExpect(redirectedUrl("/admin/nguoi-dung"))
                .andExpect(flash().attribute("successMessage", containsString("Đã khoá")));
        assertThat(userRepository.findById(customer.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.LOCKED);

        // Tài khoản LOCKED không đăng nhập được (Spring Security trả về trang đăng nhập kèm ?error)
        mockMvc.perform(post("/dang-nhap").with(csrf())
                        .param("email", customer.getEmail())
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/dang-nhap?error"));

        mockMvc.perform(post("/admin/nguoi-dung/" + customer.getId() + "/khoa")
                        .with(csrf()).session(adminSession))
                .andExpect(redirectedUrl("/admin/nguoi-dung"))
                .andExpect(flash().attribute("successMessage", containsString("Đã mở khoá")));
        assertThat(userRepository.findById(customer.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Form sửa tài khoản: hiện thông tin, cảnh báo khi sửa chính mình và chặn tự hạ quyền")
    void editForm_rendersAndBlocksSelfDemotion() throws Exception {
        MockHttpSession adminSession = loginSession(admin);

        mockMvc.perform(get("/admin/nguoi-dung/" + admin.getId() + "/sua").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đây là tài khoản bạn đang đăng nhập")))
                .andExpect(content().string(containsString(admin.getEmail())));

        User customer = createUser("khach.form", UserRole.CUSTOMER, UserStatus.ACTIVE);
        mockMvc.perform(get("/admin/nguoi-dung/" + customer.getId() + "/sua").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thông tin thêm")))
                .andExpect(content().string(containsString(customer.getEmail())));

        mockMvc.perform(post("/admin/nguoi-dung/" + admin.getId()).with(csrf()).session(adminSession)
                        .param("fullName", admin.getFullName())
                        .param("email", admin.getEmail())
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE")
                        .param("password", "")
                        .param("confirmPassword", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tự hạ quyền")));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    // ==================== HÀM HỖ TRỢ ====================

    private User createUser(String label, UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(label + "." + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Người dùng " + label);
        user.setPhone("0912345678");
        user.setRole(role);
        user.setStatus(status);
        User saved = userRepository.save(user);
        createdUserIds.add(saved.getId());
        return saved;
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
}
