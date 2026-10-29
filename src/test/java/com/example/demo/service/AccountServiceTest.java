package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.shop.ChangePasswordForm;
import com.example.demo.dto.shop.ProfileForm;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Kiểm thử nghiệp vụ tài khoản phía khách: hồ sơ, email duy nhất và đổi mật khẩu. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserAddressRepository userAddressRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("Cập nhật hồ sơ: chuẩn hoá họ tên/email, bỏ khoảng trắng số điện thoại")
    void updateProfile_normalizesInput() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("moi@example.com", 5)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileForm form = profileForm();
        form.setFullName("  Nguyễn   Văn   B  ");
        form.setEmail("  Moi@Example.com ");
        form.setPhone("  0912345678  ");

        User updated = accountService.updateProfile(5, form);

        assertThat(updated.getFullName()).isEqualTo("Nguyễn Văn B");
        assertThat(updated.getEmail()).isEqualTo("moi@example.com");
        assertThat(updated.getPhone()).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: email đã thuộc tài khoản khác ⇒ chặn")
    void updateProfile_rejectsDuplicateEmail() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("da.dung@example.com", 5)).thenReturn(true);

        ProfileForm form = profileForm();
        form.setEmail("da.dung@example.com");

        assertThatThrownBy(() -> accountService.updateProfile(5, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được tài khoản khác sử dụng");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật hồ sơ: họ tên để trống ⇒ chặn")
    void updateProfile_rejectsBlankName() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("khach@example.com", 5)).thenReturn(false);

        ProfileForm form = profileForm();
        form.setFullName("   ");

        assertThatThrownBy(() -> accountService.updateProfile(5, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vui lòng nhập họ tên");
    }

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu hiện tại sai ⇒ chặn, không đổi hash")
    void changePassword_rejectsWrongCurrentPassword() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("SaiMatKhau", "OLD_HASH")).thenReturn(false);

        assertThatThrownBy(() -> accountService.changePassword(5, passwordForm("SaiMatKhau", "NewSecret", "NewSecret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mật khẩu hiện tại không đúng");

        assertThat(user.getPasswordHash()).isEqualTo("OLD_HASH");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đổi mật khẩu: xác nhận không khớp ⇒ chặn")
    void changePassword_rejectsMismatchedConfirmation() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldSecret", "OLD_HASH")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword(5, passwordForm("OldSecret", "NewSecret", "Khac123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khớp");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đổi mật khẩu: mật khẩu mới trùng mật khẩu cũ ⇒ chặn")
    void changePassword_rejectsSamePassword() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldSecret", "OLD_HASH")).thenReturn(true);

        assertThatThrownBy(() -> accountService.changePassword(5, passwordForm("OldSecret", "OldSecret", "OldSecret")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("phải khác mật khẩu hiện tại");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đổi mật khẩu hợp lệ: băm BCrypt mật khẩu mới và lưu")
    void changePassword_encodesNewPassword() {
        User user = user(5);
        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldSecret", "OLD_HASH")).thenReturn(true);
        when(passwordEncoder.matches("NewSecret", "OLD_HASH")).thenReturn(false);
        when(passwordEncoder.encode("NewSecret")).thenReturn("NEW_HASH");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        accountService.changePassword(5, passwordForm("OldSecret", "NewSecret", "NewSecret"));

        assertThat(user.getPasswordHash()).isEqualTo("NEW_HASH");
        verify(passwordEncoder).encode("NewSecret");
    }

    // ==================== DỮ LIỆU MẪU ====================

    private ProfileForm profileForm() {
        ProfileForm form = new ProfileForm();
        form.setFullName("Khách hàng");
        form.setEmail("khach@example.com");
        form.setPhone("0912345678");
        return form;
    }

    private ChangePasswordForm passwordForm(String current, String newPassword, String confirm) {
        ChangePasswordForm form = new ChangePasswordForm();
        form.setCurrentPassword(current);
        form.setNewPassword(newPassword);
        form.setConfirmPassword(confirm);
        return form;
    }

    private User user(Integer id) {
        User user = new User();
        user.setId(id);
        user.setEmail("khach@example.com");
        user.setFullName("Khách hàng");
        user.setPasswordHash("OLD_HASH");
        return user;
    }
}
