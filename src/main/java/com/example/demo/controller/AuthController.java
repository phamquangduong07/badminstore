package com.example.demo.controller;

import com.example.demo.dto.auth.RegisterRequest;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trang đăng nhập (Spring Security xử lý POST /dang-nhap) và đăng ký tài khoản khách. */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;

    @GetMapping("/dang-nhap")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/dang-ky")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registerRequest")) {
            model.addAttribute("registerRequest", new RegisterRequest());
        }
        return "auth/register";
    }

    /**
     * Đăng ký tài khoản (pattern Post-Redirect-Get): thành công thì chuyển sang trang đăng nhập
     * kèm flash xanh; dữ liệu sai thì render lại form để hiển thị lỗi từng ô.
     */
    @PostMapping("/dang-ky")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest request,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.register(request);
        } catch (BusinessException ex) {
            // Lỗi nghiệp vụ (email trùng, mật khẩu xác nhận sai) hiển thị ngay trên form, giữ dữ liệu đã nhập.
            bindingResult.reject("register.failed", ex.getMessage());
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                messageSource.getMessage("msg.register.success", null, null));
        return "redirect:/dang-nhap";
    }
}
