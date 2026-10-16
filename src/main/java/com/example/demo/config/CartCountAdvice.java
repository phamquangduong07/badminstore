package com.example.demo.config;

import com.example.demo.service.CartService;
import com.example.demo.service.CompareService;
import com.example.demo.service.CustomUserDetails;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gắn dữ liệu header vào MỌI trang:
 * – {@code cartCount}: số sản phẩm trong giỏ (chỉ truy vấn DB khi đã đăng nhập);
 * – {@code compareCount}: số sản phẩm đang so sánh (đọc từ session, khách vãng lai vẫn có).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class CartCountAdvice {

    private final CartService cartService;
    private final CompareService compareService;
    private final ShopProperties shopProperties;

    /** Thông tin cửa hàng (tên, địa chỉ, hotline) cho header/footer và phiếu bán hàng. */
    @ModelAttribute("shop")
    public ShopProperties shop() {
        return shopProperties;
    }

    @ModelAttribute("cartCount")
    public long cartCount(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails currentUser) {
            return cartService.countItems(currentUser.getUserId());
        }
        return 0L;
    }

    @ModelAttribute("compareCount")
    public int compareCount(HttpSession session) {
        return compareService.count(session);
    }
}
