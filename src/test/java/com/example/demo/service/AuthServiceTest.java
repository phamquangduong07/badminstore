package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test nghiệp vụ đăng ký tài khoản – chạy thuần Mockito, không cần SQL Server. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request(String email, String password, String confirmPassword) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setFullName("  Nguyễn Văn A  ");
        request.setPhone("0912345678");
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    @Test
    @DisplayName("Đăng ký thành công: chuẩn hoá email, băm mật khẩu, luôn là CUSTOMER/ACTIVE")
    void register_success() {
        given(userRepository.existsByEmail("nguyenvana@gmail.com")).willReturn(false);
        given(passwordEncoder.encode("secret123")).willReturn("$2a$10$hashed");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User saved = authService.register(request("  NguyenVanA@Gmail.com  ", "secret123", "secret123"));

        assertThat(saved.getEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashed");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(saved.getFullName()).isEqualTo("Nguyễn Văn A");
        assertThat(saved.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Email đã tồn tại: báo lỗi tiếng Việt và KHÔNG lưu gì")
    void register_duplicateEmail() {
        given(userRepository.existsByEmail("nguyenvana@gmail.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request("nguyenvana@gmail.com", "secret123", "secret123")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được sử dụng");

        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Mật khẩu xác nhận không khớp: báo lỗi và KHÔNG lưu gì")
    void register_passwordMismatch() {
        given(userRepository.existsByEmail("nguyenvana@gmail.com")).willReturn(false);

        assertThatThrownBy(() -> authService.register(request("nguyenvana@gmail.com", "secret123", "secret999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không khớp");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Email rỗng/khoảng trắng vẫn được chuẩn hoá trước khi kiểm tra trùng")
    void register_normalisesEmailBeforeCheckingDuplicates() {
        given(userRepository.existsByEmail("a@b.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(request("  A@B.com ", "secret123", "secret123")))
                .isInstanceOf(BusinessException.class);

        verify(userRepository).existsByEmail("a@b.com");
    }
}
