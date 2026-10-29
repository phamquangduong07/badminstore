package com.example.demo.controller.shop;

import com.example.demo.dto.shop.CheckoutForm;
import com.example.demo.dto.shop.CheckoutSummary;
import com.example.demo.entity.Order;
import com.example.demo.entity.UserAddress;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.service.AddressService;
import com.example.demo.service.CheckoutService;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.GhnAddressService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bước thanh toán: chọn địa chỉ + phương thức thanh toán + mã giảm giá, rồi đặt hàng.
 * Giá và tổng tiền luôn được CheckoutService tính lại từ DB.
 */
@Controller
@RequestMapping("/thanh-toan")
@RequiredArgsConstructor
public class CheckoutController {

    /** Đơn online chỉ dùng 2 phương thức này (CASH dành cho đơn admin tạo tại quầy). */
    private static final List<PaymentMethod> ONLINE_PAYMENT_METHODS =
            List.of(PaymentMethod.COD, PaymentMethod.BANK_TRANSFER);

    private final CheckoutService checkoutService;
    private final AddressService addressService;
    private final GhnAddressService ghnAddressService;

    @GetMapping
    public String view(@AuthenticationPrincipal CustomUserDetails currentUser,
                       @RequestParam(name = "voucher", required = false) String voucherCode,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        Integer userId = currentUser.getUserId();
        CheckoutSummary summary = checkoutService.preview(userId, voucherCode);

        if (summary.cart().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Giỏ hàng của bạn đang trống nên chưa thể thanh toán.");
            return "redirect:/gio-hang";
        }

        model.addAttribute("summary", summary);
        model.addAttribute("addresses", addressService.list(userId));
        model.addAttribute("paymentMethods", ONLINE_PAYMENT_METHODS);
        prepareGhnModel(model, summary);

        if (!model.containsAttribute("form")) {
            CheckoutForm form = new CheckoutForm();
            checkoutService.defaultAddress(userId).map(UserAddress::getId).ifPresent(form::setAddressId);
            if (summary.voucher() != null) {
                form.setVoucherCode(summary.voucher().code());
            }
            model.addAttribute("form", form);
        }
        return "shop/checkout";
    }

    @PostMapping
    public String placeOrder(@AuthenticationPrincipal CustomUserDetails currentUser,
                             @Valid @ModelAttribute("form") CheckoutForm form,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Chưa ghi CSDL: hiển thị lại trang kèm lỗi từng ô.
            Integer userId = currentUser.getUserId();
            CheckoutSummary summary = checkoutService.preview(userId, form.getVoucherCode());
            model.addAttribute("summary", summary);
            model.addAttribute("addresses", addressService.list(userId));
            model.addAttribute("paymentMethods", ONLINE_PAYMENT_METHODS);
            prepareGhnModel(model, summary);
            return "shop/checkout";
        }

        Order order = checkoutService.placeOrder(currentUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đặt hàng thành công! Mã đơn hàng của bạn là " + order.getOrderCode() + ".");
        return "redirect:/don-hang/" + order.getId();
    }

    /**
     * Thông tin GHN cho trang thanh toán: bật/tắt, khối lượng kiện hàng và tồn kho tạm tính
     * để JavaScript xem trước phí vận chuyển khi khách đổi địa chỉ (server vẫn là nơi tính phí thật).
     */
    private void prepareGhnModel(Model model, CheckoutSummary summary) {
        model.addAttribute("ghnEnabled", ghnAddressService.isAvailable());
        model.addAttribute("ghnShippingEnabled", ghnAddressService.isShippingAvailable());
        model.addAttribute("ghnMock", ghnAddressService.isMock());
        model.addAttribute("ghnWeightGram", ghnAddressService.shippingWeight(summary.getTotalQuantity()));
        model.addAttribute("ghnWarehouse", ghnAddressService.warehouseAddress());
    }
}
