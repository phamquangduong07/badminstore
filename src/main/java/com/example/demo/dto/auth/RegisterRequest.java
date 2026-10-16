package com.example.demo.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dữ liệu form đăng ký – KHÔNG dùng entity trực tiếp trên form.
 * Validate 2 lớp: HTML (required/pattern ở template) + Bean Validation ở đây.
 */
@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @Pattern(regexp = "^$|^(0|\\+84)\\d{8,10}$", message = "Số điện thoại không hợp lệ (ví dụ: 0912345678)")
    private String phone;

    /** BCrypt chỉ dùng 72 byte đầu của mật khẩu nên chặn luôn ở 72 ký tự. */
    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu")
    private String confirmPassword;
}
