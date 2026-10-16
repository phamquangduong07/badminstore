package com.example.demo.dto.admin;

/**
 * Thông tin khách quen tra theo số điện thoại ở màn bán hàng tại quầy.
 * {@code found = false} nghĩa là chưa có tài khoản ⇒ quầy bán như khách lẻ.
 */
public record CustomerLookupView(boolean found, String fullName, String email, String phone) {

    public static CustomerLookupView notFound() {
        return new CustomerLookupView(false, null, null, null);
    }
}
