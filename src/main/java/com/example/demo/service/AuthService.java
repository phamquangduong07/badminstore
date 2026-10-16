package com.example.demo.service;

import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ tài khoản: đăng ký khách hàng mới. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Đăng ký tài khoản khách hàng. Mọi kiểm tra đều làm ở server:
     * email chưa tồn tại, mật khẩu xác nhận khớp, mật khẩu được băm bằng BCrypt.
     * Tài khoản mới luôn là CUSTOMER/ACTIVE – khách không thể tự phong quyền ADMIN.
     */
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email " + email + " đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác.");
        }
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("Mật khẩu xác nhận không khớp. Vui lòng nhập lại.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName().trim());
        user.setPhone(hasText(request.getPhone()) ? request.getPhone().trim() : null);
        user.setRole(UserRole.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);

        return userRepository.save(user);
    }

    /** Email lưu và đăng nhập đều được chuẩn hoá (bỏ khoảng trắng, viết thường). */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
