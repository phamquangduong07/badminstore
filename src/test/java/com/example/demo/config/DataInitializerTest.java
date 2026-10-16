package com.example.demo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Test việc tạo tài khoản ADMIN lúc khởi động (DataInitializer). */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private DataInitializer initializer(String email, String password) {
        DataInitializer initializer = new DataInitializer(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "adminEmail", email);
        ReflectionTestUtils.setField(initializer, "adminPassword", password);
        return initializer;
    }

    @Test
    @DisplayName("Chưa có ADMIN: tạo mới với mật khẩu đã băm BCrypt và vai trò ADMIN")
    void createsAdminWhenMissing() {
        given(userRepository.existsByEmail("admin@badstore.local")).willReturn(false);
        given(passwordEncoder.encode("Admin@123")).willReturn("$2a$10$adminHash");

        initializer("admin@badstore.local", "Admin@123").run();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(admin.getPasswordHash()).isEqualTo("$2a$10$adminHash");
        assertThat(admin.getEmail()).isEqualTo("admin@badstore.local");
    }

    @Test
    @DisplayName("ADMIN đã tồn tại: không tạo trùng")
    void doesNotCreateAdminTwice() {
        given(userRepository.existsByEmail("admin@badstore.local")).willReturn(true);

        initializer("admin@badstore.local", "Admin@123").run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    @DisplayName("Chưa cấu hình mật khẩu ADMIN: bỏ qua và không đụng tới DB")
    void skipsWhenPasswordMissing() {
        initializer("admin@badstore.local", "   ").run();

        verifyNoInteractions(userRepository);
    }
}
