package com.example.demo.controller.shop;

import com.example.demo.dto.shop.ChangePasswordForm;
import com.example.demo.dto.shop.ProfileForm;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AccountService;
import com.example.demo.service.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Khu vực tài khoản của khách đang đăng nhập: xem/sửa hồ sơ và đổi mật khẩu.
 *
 * Sau khi đổi họ tên/email, principal trong session được làm mới để header hiển thị
 * đúng thông tin ngay, không phải đăng xuất rồi đăng nhập lại.
 */
@Controller
@RequestMapping("/tai-khoan")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public String profile(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        User user = accountService.requireUser(currentUser.getUserId());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", toForm(user));
        }
        prepareAccountModel(model, user);
        return "shop/account";
    }

    @PostMapping
    public String updateProfile(@AuthenticationPrincipal CustomUserDetails currentUser,
                                @Valid @ModelAttribute("form") ProfileForm form,
                                BindingResult bindingResult,
                                Model model,
                                HttpServletRequest request,
                                HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareAccountModel(model, accountService.requireUser(currentUser.getUserId()));
            return "shop/account";
        }
        try {
            User updated = accountService.updateProfile(currentUser.getUserId(), form);
            refreshAuthentication(updated, request, response);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật thông tin tài khoản.");
            return "redirect:/tai-khoan";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareAccountModel(model, accountService.requireUser(currentUser.getUserId()));
            return "shop/account";
        }
    }

    @GetMapping("/doi-mat-khau")
    public String passwordForm(@AuthenticationPrincipal CustomUserDetails currentUser, Model model) {
        if (!model.containsAttribute("passwordForm")) {
            model.addAttribute("passwordForm", new ChangePasswordForm());
        }
        prepareAccountModel(model, accountService.requireUser(currentUser.getUserId()));
        return "shop/change-password";
    }

    @PostMapping("/doi-mat-khau")
    public String changePassword(@AuthenticationPrincipal CustomUserDetails currentUser,
                                 @Valid @ModelAttribute("passwordForm") ChangePasswordForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasFieldErrors("newPassword")
                && !bindingResult.hasFieldErrors("confirmPassword")
                && !form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Mật khẩu xác nhận không khớp.");
        }
        if (bindingResult.hasErrors()) {
            prepareAccountModel(model, accountService.requireUser(currentUser.getUserId()));
            return "shop/change-password";
        }
        try {
            accountService.changePassword(currentUser.getUserId(), form);
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareAccountModel(model, accountService.requireUser(currentUser.getUserId()));
            return "shop/change-password";
        }
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã đổi mật khẩu thành công. Hãy dùng mật khẩu mới ở lần đăng nhập sau.");
        return "redirect:/tai-khoan";
    }

    // ==================== HÀM NỘI BỘ ====================

    private ProfileForm toForm(User user) {
        ProfileForm form = new ProfileForm();
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setPhone(user.getPhone());
        return form;
    }

    /** Dữ liệu dùng chung cho sidebar tài khoản (thông tin + số đơn + số địa chỉ). */
    private void prepareAccountModel(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("orderCount", accountService.countOrders(user.getId()));
        model.addAttribute("addressCount", accountService.countAddresses(user.getId()));
    }

    /**
     * Thay principal trong session bằng bản mới sau khi đổi họ tên/email.
     * Spring Security 6/7 chỉ lưu SecurityContext khi được yêu cầu rõ ràng,
     * nên phải gọi repository.saveContext thì request sau mới thấy dữ liệu mới.
     */
    private void refreshAuthentication(User user, HttpServletRequest request, HttpServletResponse response) {
        CustomUserDetails principal = CustomUserDetails.from(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    }
}
