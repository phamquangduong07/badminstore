package com.example.demo.controller.shop;

import com.example.demo.dto.shop.CartLine;
import com.example.demo.service.CartService;
import com.example.demo.service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Giỏ hàng của khách (yêu cầu đăng nhập – xem SecurityConfig).
 * Mọi thao tác ghi đều theo Post-Redirect-Get + flash message (SweetAlert2 bật toast ở layout).
 * Lỗi nghiệp vụ (hết hàng, vượt tồn, sản phẩm ngừng bán) do GlobalExceptionHandler bắt
 * và quay lại đúng trang vừa bấm kèm thông báo đỏ.
 */
@Controller
@RequestMapping("/gio-hang")
@RequiredArgsConstructor
public class CartController {

    /** Chỉ cho phép quay lại trang chi tiết sản phẩm nội bộ (chặn open-redirect). */
    private static final String PRODUCT_PATH_PREFIX = "/san-pham/";

    private final CartService cartService;

    @GetMapping
    public String view(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("cart", cartService.getCart(currentUser.getUserId()));
        return "shop/cart";
    }

    /** Thêm vào giỏ từ trang chi tiết sản phẩm. */
    @PostMapping("/them")
    public String add(@AuthenticationPrincipal CustomUserDetails currentUser,
                      @RequestParam("variantId") Integer variantId,
                      @RequestParam(name = "quantity", defaultValue = "1") Integer quantity,
                      @RequestParam(name = "back", required = false) String back,
                      RedirectAttributes redirectAttributes) {
        CartLine line = cartService.addToCart(currentUser.getUserId(), variantId, quantity);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thêm \"" + line.getProductName() + "\" ("
                + line.getVariantLabel() + ") vào giỏ hàng – số lượng trong giỏ: " + line.getQuantity() + ".");
        return "redirect:" + safeBack(back);
    }

    /** Sửa số lượng một dòng trong trang giỏ hàng. */
    @PostMapping("/cap-nhat")
    public String update(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @RequestParam("cartItemId") Integer cartItemId,
                         @RequestParam("quantity") Integer quantity,
                         RedirectAttributes redirectAttributes) {
        CartLine line = cartService.updateQuantity(currentUser.getUserId(), cartItemId, quantity);
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật \"" + line.getProductName() + "\" ("
                + line.getVariantLabel() + ") thành " + line.getQuantity() + " sản phẩm.");
        return "redirect:/gio-hang";
    }

    /** Xoá một dòng. */
    @PostMapping("/xoa")
    public String remove(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @RequestParam("cartItemId") Integer cartItemId,
                         RedirectAttributes redirectAttributes) {
        cartService.removeItem(currentUser.getUserId(), cartItemId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xoá sản phẩm khỏi giỏ hàng.");
        return "redirect:/gio-hang";
    }

    /** Xoá toàn bộ giỏ. */
    @PostMapping("/xoa-tat-ca")
    public String clear(@AuthenticationPrincipal CustomUserDetails currentUser,
                        RedirectAttributes redirectAttributes) {
        int removed = cartService.clearCart(currentUser.getUserId());
        if (removed > 0) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xoá toàn bộ " + removed + " sản phẩm khỏi giỏ hàng.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Giỏ hàng của bạn đang trống.");
        }
        return "redirect:/gio-hang";
    }

    /** Không nhận URL ngoài hệ thống để tránh lỗi open-redirect. */
    private String safeBack(String back) {
        if (back != null && back.startsWith(PRODUCT_PATH_PREFIX)
                && !back.contains("//") && back.length() <= 100) {
            return back;
        }
        return "/gio-hang";
    }
}
