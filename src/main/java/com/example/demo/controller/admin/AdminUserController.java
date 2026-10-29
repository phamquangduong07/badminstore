package com.example.demo.controller.admin;

import com.example.demo.dto.admin.AdminUserForm;
import com.example.demo.dto.admin.UserRow;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AdminUserService;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.util.Pagination;
import jakarta.validation.Valid;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản trị người dùng: danh sách có lọc, tạo tài khoản, sửa thông tin/vai trò/trạng thái,
 * đặt lại mật khẩu và khoá/mở khoá tài khoản.
 *
 * Không có chức năng xoá vì users còn được đơn hàng, địa chỉ, đánh giá tham chiếu;
 * muốn ngừng sử dụng thì khoá tài khoản.
 */
@Controller
@RequestMapping("/admin/nguoi-dung")
@RequiredArgsConstructor
public class AdminUserController {

    private static final int PAGE_LINK_WINDOW = 5;

    private final AdminUserService adminUserService;

    @GetMapping
    public String list(@RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "role", required = false) UserRole role,
                       @RequestParam(name = "status", required = false) UserStatus status,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model,
                       @AuthenticationPrincipal CustomUserDetails currentUser) {
        Page<UserRow> users = adminUserService.search(keyword, role, status, page);
        if (users.getTotalPages() > 0 && page > users.getTotalPages()) {
            users = adminUserService.search(keyword, role, status, users.getTotalPages());
        }

        model.addAttribute("users", users);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("roleFilter", role);
        model.addAttribute("statusFilter", status);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("currentUserId", currentUser.getUserId());
        model.addAttribute("pageNumbers", Pagination.pageNumbers(users, PAGE_LINK_WINDOW));
        model.addAttribute("adminMenu", "users");
        return "admin/users";
    }

    @GetMapping("/tao-moi")
    public String createForm(Model model) {
        prepareForm(model, new AdminUserForm(), null, null, false);
        return "admin/user-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") AdminUserForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        validatePassword(form, bindingResult, true);
        if (bindingResult.hasErrors()) {
            prepareForm(model, form, null, null, false);
            return "admin/user-form";
        }
        try {
            User created = adminUserService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo tài khoản " + created.getEmail() + ".");
            return "redirect:/admin/nguoi-dung";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareForm(model, form, null, null, false);
            return "admin/user-form";
        }
    }

    @GetMapping("/{id}/sua")
    public String editForm(@PathVariable("id") Integer id,
                           Model model,
                           @AuthenticationPrincipal CustomUserDetails currentUser) {
        User user = adminUserService.requireUser(id);
        AdminUserForm form = new AdminUserForm();
        form.setFullName(user.getFullName());
        form.setEmail(user.getEmail());
        form.setPhone(user.getPhone());
        form.setRole(user.getRole());
        form.setStatus(user.getStatus());
        prepareForm(model, form, id, user, Objects.equals(id, currentUser.getUserId()));
        return "admin/user-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Integer id,
                         @Valid @ModelAttribute("form") AdminUserForm form,
                         BindingResult bindingResult,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         RedirectAttributes redirectAttributes) {
        validatePassword(form, bindingResult, false);
        if (bindingResult.hasErrors()) {
            prepareForm(model, form, id, adminUserService.requireUser(id),
                    Objects.equals(id, currentUser.getUserId()));
            return "admin/user-form";
        }
        try {
            User updated = adminUserService.update(id, form, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật tài khoản " + updated.getEmail() + ".");
            return "redirect:/admin/nguoi-dung";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareForm(model, form, id, adminUserService.requireUser(id),
                    Objects.equals(id, currentUser.getUserId()));
            return "admin/user-form";
        }
    }

    /** Khoá tài khoản đang hoạt động, hoặc mở khoá tài khoản đã bị khoá. */
    @PostMapping("/{id}/khoa")
    public String toggleLock(@PathVariable("id") Integer id,
                             @AuthenticationPrincipal CustomUserDetails currentUser,
                             RedirectAttributes redirectAttributes) {
        try {
            User user = adminUserService.toggleLock(id, currentUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage",
                    user.getStatus() == UserStatus.LOCKED
                            ? "Đã khoá tài khoản " + user.getEmail() + "."
                            : "Đã mở khoá tài khoản " + user.getEmail() + ".");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/nguoi-dung";
    }

    // ==================== HÀM NỘI BỘ ====================

    /**
     * Kiểm tra mật khẩu ở mức ô nhập để hiện lỗi ngay cạnh ô:
     * – tạo mới: bắt buộc nhập;
     * – sửa: để trống cả 2 ô = giữ mật khẩu cũ, có nhập thì phải khớp xác nhận.
     */
    private void validatePassword(AdminUserForm form, BindingResult bindingResult, boolean creating) {
        boolean hasPassword = form.getPassword() != null && !form.getPassword().isBlank();
        boolean hasConfirm = form.getConfirmPassword() != null && !form.getConfirmPassword().isBlank();

        if (creating && !hasPassword) {
            bindingResult.rejectValue("password", "password.required",
                    "Vui lòng nhập mật khẩu cho tài khoản mới.");
        } else if (!hasPassword && hasConfirm) {
            bindingResult.rejectValue("password", "password.required",
                    "Vui lòng nhập mật khẩu mới hoặc để trống cả hai ô.");
        } else if (hasPassword && !Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch",
                    "Mật khẩu xác nhận không khớp.");
        }
    }

    private void prepareForm(Model model, AdminUserForm form, Integer userId, User user, boolean editingSelf) {
        model.addAttribute("form", form);
        model.addAttribute("userId", userId);
        model.addAttribute("user", user);
        model.addAttribute("editingSelf", editingSelf);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("adminMenu", "users");
    }
}
