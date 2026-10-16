package com.example.demo.controller.shop;

import com.example.demo.exception.BusinessException;
import com.example.demo.service.CompareService;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * So sánh sản phẩm (mức Khá): thêm/xoá trong SESSION nên không cần đăng nhập và không ghi CSDL.
 * Mọi thao tác theo Post-Redirect-Get; thêm xong quay lại đúng trang khách đang xem.
 */
@Controller
@RequestMapping("/so-sanh")
@RequiredArgsConstructor
public class CompareController {

    private final CompareService compareService;

    @GetMapping
    public String compare(HttpSession session, Model model) {
        model.addAttribute("items", compareService.compareItems(session));
        model.addAttribute("maxItems", CompareService.MAX_ITEMS);
        return "shop/compare";
    }

    @PostMapping("/them")
    public String add(@RequestParam("productId") Integer productId,
                      HttpSession session,
                      RedirectAttributes redirectAttributes,
                      @RequestHeader(name = "Referer", required = false) String referer) {
        try {
            CompareService.AddResult result = compareService.add(session, productId);
            redirectAttributes.addFlashAttribute(result.added() ? "successMessage" : "errorMessage",
                    result.message());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + safeBackPath(referer);
    }

    @PostMapping("/xoa")
    public String remove(@RequestParam("productId") Integer productId,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        compareService.remove(session, productId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã bỏ sản phẩm khỏi danh sách so sánh.");
        return "redirect:/so-sanh";
    }

    @PostMapping("/xoa-het")
    public String clear(HttpSession session, RedirectAttributes redirectAttributes) {
        compareService.clear(session);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xoá toàn bộ danh sách so sánh.");
        return "redirect:/so-sanh";
    }

    /**
     * Quay lại trang khách vừa xem (giữ cả bộ lọc trên URL).
     * Chỉ nhận ĐƯỜNG DẪN nội bộ để tránh open-redirect ra site khác.
     */
    private String safeBackPath(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/so-sanh";
        }
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/")) {
                return "/so-sanh";
            }
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (IllegalArgumentException ex) {
            return "/so-sanh";
        }
    }
}
