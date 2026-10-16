package com.example.demo.controller.shop;

import com.example.demo.dto.shop.AddressForm;
import com.example.demo.service.AddressService;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.GhnAddressService;
import jakarta.validation.Valid;
import java.util.Set;
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
 * Sổ địa chỉ giao hàng của khách. Mọi thao tác ghi theo Post-Redirect-Get + flash message.
 * Form thêm địa chỉ validate 2 lớp và hiển thị lỗi cạnh từng ô ngay tại chỗ.
 */
@Controller
@RequestMapping("/dia-chi")
@RequiredArgsConstructor
public class AddressController {

    /** Chỉ quay về các trang nội bộ này sau khi thao tác (chặn open-redirect). */
    private static final Set<String> ALLOWED_NEXT = Set.of("/dia-chi", "/thanh-toan");

    private final AddressService addressService;
    private final GhnAddressService ghnAddressService;

    @GetMapping
    public String list(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        model.addAttribute("addresses", addressService.list(currentUser.getUserId()));
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new AddressForm());
        }
        prepareGhnModel(model);
        return "shop/addresses";
    }

    @PostMapping("/them")
    public String create(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @Valid @ModelAttribute("form") AddressForm form,
                         BindingResult bindingResult,
                         @RequestParam(name = "next", required = false) String next,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            // Chưa ghi CSDL nên hiển thị lại form kèm lỗi, không cần redirect.
            model.addAttribute("addresses", addressService.list(currentUser.getUserId()));
            model.addAttribute("openForm", true);
            prepareGhnModel(model);
            return "shop/addresses";
        }

        addressService.create(currentUser.getUserId(), form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã lưu địa chỉ của \"" + form.getReceiverName().trim() + "\".");
        return "redirect:" + safeNext(next);
    }

    @PostMapping("/mac-dinh")
    public String setDefault(@AuthenticationPrincipal CustomUserDetails currentUser,
                             @RequestParam("addressId") Integer addressId,
                             @RequestParam(name = "next", required = false) String next,
                             RedirectAttributes redirectAttributes) {
        addressService.setDefault(currentUser.getUserId(), addressId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã đặt làm địa chỉ mặc định.");
        return "redirect:" + safeNext(next);
    }

    @PostMapping("/xoa")
    public String delete(@AuthenticationPrincipal CustomUserDetails currentUser,
                         @RequestParam("addressId") Integer addressId,
                         @RequestParam(name = "next", required = false) String next,
                         RedirectAttributes redirectAttributes) {
        addressService.delete(currentUser.getUserId(), addressId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xoá địa chỉ giao hàng.");
        return "redirect:" + safeNext(next);
    }

    /**
     * Dữ liệu cho khối "Chọn địa chỉ theo GHN": danh sách tỉnh/thành + cờ bật/tắt.
     * GHN chưa bật hoặc đang lỗi thì `ghnEnabled = false` ⇒ giao diện chỉ hiện ô nhập tay.
     */
    private void prepareGhnModel(Model model) {
        model.addAttribute("ghnEnabled", ghnAddressService.isAvailable());
        model.addAttribute("ghnMock", ghnAddressService.isMock());
        model.addAttribute("ghnProvinces", ghnAddressService.provinces());
    }

    private String safeNext(String next) {
        return (next != null && ALLOWED_NEXT.contains(next)) ? next : "/dia-chi";
    }
}
