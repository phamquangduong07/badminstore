package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo tài khoản ADMIN đầu tiên khi khởi động (nếu chưa có).
 * Mật khẩu lấy từ cấu hình app.admin.password (application-local.yml / biến môi trường ADMIN_PASSWORD)
 * nên KHÔNG hardcode hash trong file .sql.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@badstore.local}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("Bỏ qua tạo tài khoản ADMIN: chưa cấu hình app.admin.password "
                    + "(đặt trong application-local.yml hoặc biến môi trường ADMIN_PASSWORD).");
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            log.info("Tài khoản ADMIN {} đã tồn tại – không tạo lại.", email);
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName("Quản trị viên");
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userRepository.save(admin);

        log.info("Đã tạo tài khoản ADMIN: {} – hãy đổi mật khẩu sau khi đăng nhập.", email);
    }
}
