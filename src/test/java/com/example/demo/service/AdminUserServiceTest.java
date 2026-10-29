package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.admin.AdminUserForm;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Kiểm thử nghiệp vụ quản trị người dùng: chuẩn hoá dữ liệu, băm mật khẩu,
 * và các chốt an toàn (tự hạ quyền, tự khoá, ADMIN cuối cùng).
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    @DisplayName("Tạo tài khoản: chuẩn hoá email/họ tên, băm mật khẩu và gán vai trò/trạng thái")
    void create_normalizesAndEncodesPassword() {
        AdminUserForm form = form();
        form.setFullName("  Nguyễn   Văn A ");
        form.setEmail("  New.User@Example.com ");
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret123");
        form.setRole(UserRole.CUSTOMER);
        form.setStatus(UserStatus.ACTIVE);

        when(userRepository.existsByEmail("new.user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("BCRYPT_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = adminUserService.create(form);

        assertThat(created.getEmail()).isEqualTo("new.user@example.com");
        assertThat(created.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(created.getPasswordHash()).isEqualTo("BCRYPT_HASH");
        assertThat(created.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Tạo tài khoản: email đã tồn tại ⇒ báo lỗi tiếng Việt, không lưu")
    void create_rejectsDuplicateEmail() {
        AdminUserForm form = form();
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret123");
        when(userRepository.existsByEmail("khach@example.com")).thenReturn(true);

        assertThatThrownBy(() -> adminUserService.create(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được sử dụng");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tạo tài khoản: mật khẩu xác nhận không khớp ⇒ chặn")
    void create_rejectsMismatchedPassword() {
        AdminUserForm form = form();
        form.setPassword("Secret123");
        form.setConfirmPassword("Secret456");
        when(userRepository.existsByEmail("khach@example.com")).thenReturn(false);

        assertThatThrownBy(() -> adminUserService.create(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khớp");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa tài khoản: ADMIN không thể tự hạ quyền chính mình")
    void update_blocksSelfDemotion() {
        User admin = user(1, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(1)).thenReturn(Optional.of(admin));

        AdminUserForm form = form();
        form.setRole(UserRole.CUSTOMER);

        assertThatThrownBy(() -> adminUserService.update(1, form, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tự hạ quyền");
    }

    @Test
    @DisplayName("Sửa tài khoản: ADMIN không thể tự khoá tài khoản đang đăng nhập")
    void update_blocksSelfLock() {
        User admin = user(1, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(1)).thenReturn(Optional.of(admin));

        AdminUserForm form = form();
        form.setRole(UserRole.ADMIN);
        form.setStatus(UserStatus.LOCKED);

        assertThatThrownBy(() -> adminUserService.update(1, form, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tự khoá");
    }

    @Test
    @DisplayName("Sửa tài khoản: không được hạ quyền ADMIN đang hoạt động cuối cùng")
    void update_blocksRemovingLastActiveAdmin() {
        User admin = user(2, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(2)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        AdminUserForm form = form();
        form.setRole(UserRole.CUSTOMER);

        assertThatThrownBy(() -> adminUserService.update(2, form, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất một quản trị viên");
    }

    @Test
    @DisplayName("Sửa tài khoản: còn ADMIN khác đang hoạt động thì được phép hạ quyền")
    void update_allowsDemotionWhenAnotherAdminRemains() {
        User admin = user(2, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(2)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminUserForm form = form();
        form.setRole(UserRole.CUSTOMER);
        form.setStatus(UserStatus.ACTIVE);

        User updated = adminUserService.update(2, form, 1);

        assertThat(updated.getRole()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    @DisplayName("Sửa tài khoản: có nhập mật khẩu mới thì băm lại, để trống thì giữ nguyên")
    void update_changesPasswordOnlyWhenProvided() {
        User customer = user(5, UserRole.CUSTOMER, UserStatus.ACTIVE);
        customer.setPasswordHash("OLD_HASH");
        when(userRepository.findById(5)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("NewSecret")).thenReturn("NEW_HASH");

        AdminUserForm withPassword = form();
        withPassword.setPassword("NewSecret");
        withPassword.setConfirmPassword("NewSecret");

        adminUserService.update(5, withPassword, 1);
        assertThat(customer.getPasswordHash()).isEqualTo("NEW_HASH");

        AdminUserForm blankPassword = form();
        blankPassword.setPassword("");
        blankPassword.setConfirmPassword("");

        adminUserService.update(5, blankPassword, 1);
        assertThat(customer.getPasswordHash()).isEqualTo("NEW_HASH");
        verify(passwordEncoder).encode("NewSecret");
    }

    @Test
    @DisplayName("Sửa tài khoản: email đã thuộc tài khoản khác ⇒ chặn")
    void update_rejectsEmailOfAnotherAccount() {
        User customer = user(5, UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(5)).thenReturn(Optional.of(customer));
        when(userRepository.existsByEmailAndIdNot("khach@example.com", 5)).thenReturn(true);

        AdminUserForm form = form();
        form.setEmail("khach@example.com");

        assertThatThrownBy(() -> adminUserService.update(5, form, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tài khoản khác");
    }

    @Test
    @DisplayName("Khoá tài khoản: không thể tự khoá chính mình")
    void toggleLock_blocksSelf() {
        User admin = user(1, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(1)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.toggleLock(1, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tự khoá");
    }

    @Test
    @DisplayName("Khoá tài khoản: ADMIN đang hoạt động cuối cùng không được khoá")
    void toggleLock_blocksLastActiveAdmin() {
        User admin = user(2, UserRole.ADMIN, UserStatus.ACTIVE);
        when(userRepository.findById(2)).thenReturn(Optional.of(admin));
        when(userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> adminUserService.toggleLock(2, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất một quản trị viên");
    }

    @Test
    @DisplayName("Khoá tài khoản khách hàng ⇒ LOCKED; mở khoá ⇒ ACTIVE")
    void toggleLock_switchesCustomerStatus() {
        User customer = user(5, UserRole.CUSTOMER, UserStatus.ACTIVE);
        when(userRepository.findById(5)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(adminUserService.toggleLock(5, 1).getStatus()).isEqualTo(UserStatus.LOCKED);
        assertThat(adminUserService.toggleLock(5, 1).getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Khoá tài khoản đang INACTIVE ⇒ LOCKED")
    void toggleLock_turnsInactiveIntoLocked() {
        User customer = user(5, UserRole.CUSTOMER, UserStatus.INACTIVE);
        when(userRepository.findById(5)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(adminUserService.toggleLock(5, 1).getStatus()).isEqualTo(UserStatus.LOCKED);
    }

    // ==================== DỮ LIỆU MẪU ====================

    private AdminUserForm form() {
        AdminUserForm form = new AdminUserForm();
        form.setFullName("Khách hàng thử nghiệm");
        form.setEmail("khach@example.com");
        form.setPhone("0912345678");
        form.setRole(UserRole.CUSTOMER);
        form.setStatus(UserStatus.ACTIVE);
        return form;
    }

    private User user(Integer id, UserRole role, UserStatus status) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setFullName("Người dùng " + id);
        user.setPasswordHash("HASH");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
