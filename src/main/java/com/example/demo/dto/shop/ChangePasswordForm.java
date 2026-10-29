package com.example.demo.dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form đổi mật khẩu của khách đang đăng nhập.
 * Phải nhập đúng mật khẩu hiện tại (kiểm tra bằng BCrypt ở Service) để tránh
 * trường hợp người khác dùng phiên đăng nhập đang mở đổi mật khẩu.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordForm {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    private String currentPassword;

    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu nên chặn luôn ở 72 ký tự. */
    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 72, message = "Mật khẩu mới phải từ 6 đến 72 ký tự")
    private String newPassword;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu mới")
    private String confirmPassword;
}
