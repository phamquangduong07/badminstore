package com.example.demo.controller.shop;

import com.example.demo.dto.shop.OrderSummaryView;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.OrderService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Đơn hàng của khách: danh sách (có phân trang), chi tiết kèm timeline trạng thái,
 * tự huỷ đơn (khi còn PENDING/CONFIRMED) và gửi yêu cầu hoàn trả 100% (đơn đã giao).
 * Khách chỉ xem được đơn của chính mình – xem OrderService#requireOwnOrder.
 */
@Controller
@RequestMapping("/don-hang")
@RequiredArgsConstructor
public class OrderController {

    private static final int PAGE_LINK_WINDOW = 5;

    private final OrderService orderService;

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails currentUser,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        Integer userId = currentUser.getUserId();
        Page<OrderSummaryView> orders = orderService.myOrders(userId, page);

        // Khách tự sửa page vượt số trang ⇒ lùi về trang cuối thay vì hiện danh sách rỗng.
        if (orders.getTotalPages() > 0 && page > orders.getTotalPages()) {
            orders = orderService.myOrders(userId, orders.getTotalPages());
        }

        model.addAttribute("orders", orders);
        model.addAttribute("pageNumbers", pageNumbers(orders));
        return "shop/orders";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @PathVariable("id") Integer id,
                         Model model) {
        model.addAttribute("detail", orderService.myOrderDetail(currentUser.getUserId(), id));
        return "shop/order-detail";
    }

    @PostMapping("/{id}/huy")
    public String cancel(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @PathVariable("id") Integer id,
                         @RequestParam("reason") String reason,
                         RedirectAttributes redirectAttributes) {
        orderService.cancelByCustomer(currentUser.getUserId(), id, reason);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã huỷ đơn hàng thành công. Nếu đơn đã trừ kho, hàng sẽ được trả lại ngay.");
        return "redirect:/don-hang/" + id;
    }

    @PostMapping("/{id}/hoan-tra")
    public String requestReturn(@AuthenticationPrincipal CustomUserDetails currentUser,
                                @PathVariable("id") Integer id,
                                @RequestParam("reason") String reason,
                                RedirectAttributes redirectAttributes) {
        orderService.returnByCustomer(currentUser.getUserId(), id, reason);
        redirectAttributes.addFlashAttribute("successMessage",
                "BadStore đã ghi nhận yêu cầu hoàn trả 100% cho đơn hàng này.");
        return "redirect:/don-hang/" + id;
    }

    /** Cửa sổ số trang (giống trang danh sách sản phẩm) – tính ở Controller cho template gọn. */
    private List<Integer> pageNumbers(Page<?> result) {
        if (result.getTotalPages() <= 1) {
            return List.of();
        }
        int total = result.getTotalPages();
        int start = Math.max(0, result.getNumber() - PAGE_LINK_WINDOW / 2);
        int end = Math.min(total - 1, start + PAGE_LINK_WINDOW - 1);
        start = Math.max(0, end - PAGE_LINK_WINDOW + 1);

        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
