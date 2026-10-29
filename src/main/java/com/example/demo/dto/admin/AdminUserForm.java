package com.example.demo.dto.admin;

import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Form admin thêm/sửa tài khoản người dùng.
 *
 * Mật khẩu dùng {@link Pattern} thay vì {@code @Size} vì khi SỬA, ô mật khẩu được phép để trống
 * (giữ mật khẩu cũ); chỉ khi có nhập mới bắt buộc 6–72 ký tự.
 * Việc bắt buộc mật khẩu khi TẠO và so khớp xác nhận do Controller/Service kiểm tra để báo lỗi đúng ô.
 */
@Getter
@Setter
public class AdminUserForm {

    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không hợp lệ.")
    @Size(max = 100, message = "Email tối đa 100 ký tự.")
    private String email;

    @Pattern(regexp = "^$|^(0|\\+84)\\d{8,10}$", message = "Số điện thoại không hợp lệ (ví dụ: 0912345678).")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
    private String phone;

    @NotNull(message = "Vui lòng chọn vai trò.")
    private UserRole role = UserRole.CUSTOMER;

    @NotNull(message = "Vui lòng chọn trạng thái.")
    private UserStatus status = UserStatus.ACTIVE;

    /** Để trống khi sửa = giữ mật khẩu hiện tại. */
    @Pattern(regexp = "^$|^.{6,72}$", message = "Mật khẩu phải từ 6 đến 72 ký tự.")
    private String password;

    /** Nhập lại mật khẩu – chỉ dùng để so khớp, không lưu xuống CSDL. */
    private String confirmPassword;
}
