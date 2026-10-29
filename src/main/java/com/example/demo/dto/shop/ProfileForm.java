package com.example.demo.dto.shop;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form cập nhật hồ sơ của khách đang đăng nhập – KHÔNG dùng entity trực tiếp trên form.
 * Email là tên đăng nhập nên vẫn cho đổi nhưng phải duy nhất (kiểm tra ở Service).
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileForm {

    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Pattern(regexp = "^$|^(0|\\+84)\\d{8,10}$",
            message = "Số điện thoại không hợp lệ (ví dụ: 0912345678)")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String phone;
}
