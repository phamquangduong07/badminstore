package com.example.demo.controller.shop;

import com.example.demo.exception.BusinessException;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gửi đánh giá sản phẩm (mức Khá).
 * Đường dẫn này KHÔNG nằm trong danh sách công khai nên bắt buộc đăng nhập (SecurityConfig).
 * Quyền đánh giá ("đã mua & đã nhận hàng") do ReviewService kiểm tra lại ở phía server.
 */
@Controller
@RequestMapping("/danh-gia")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public String add(@RequestParam("productId") Integer productId,
                      @RequestParam(name = "rating", required = false) Integer rating,
                      @RequestParam(name = "comment", required = false) String comment,
                      @AuthenticationPrincipal CustomUserDetails currentUser,
                      RedirectAttributes redirectAttributes) {
        if (currentUser == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn cần đăng nhập để đánh giá sản phẩm.");
            return "redirect:/dang-nhap";
        }
        try {
            reviewService.addReview(productId, currentUser.getUserId(),
                    rating == null ? 0 : rating.byteValue(), comment);
            redirectAttributes.addFlashAttribute("successMessage", "Cảm ơn bạn đã đánh giá sản phẩm!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/san-pham/" + productId + "#danh-gia";
    }
}
