package com.example.demo.controller.admin;

import com.example.demo.dto.admin.VoucherForm;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.VoucherService;
import com.example.demo.util.Pagination;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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
 * Quản trị voucher: danh sách có lọc, tạo mới, sửa, bật/ngừng áp dụng.
 * Không xoá voucher để giữ liên kết với các đơn đã dùng mã (đúng nguyên tắc dữ liệu lịch sử).
 */
@Controller
@RequestMapping("/admin/voucher")
@RequiredArgsConstructor
public class AdminVoucherController {

    private static final int PAGE_LINK_WINDOW = 5;

    private final VoucherService voucherService;

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) VoucherStatus status,
                       @RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        Page<Voucher> vouchers = voucherService.search(keyword, status, page);
        if (vouchers.getTotalPages() > 0 && page > vouchers.getTotalPages()) {
            vouchers = voucherService.search(keyword, status, vouchers.getTotalPages());
        }

        model.addAttribute("vouchers", vouchers);
        model.addAttribute("statuses", VoucherStatus.values());
        model.addAttribute("statusFilter", status);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("now", LocalDateTime.now());   // để tính "đang chạy/hết hạn/hết lượt"
        model.addAttribute("pageNumbers", Pagination.pageNumbers(vouchers, PAGE_LINK_WINDOW));
        model.addAttribute("adminMenu", "vouchers");
        return "admin/vouchers";
    }

    @GetMapping("/tao-moi")
    public String createForm(Model model) {
        VoucherForm form = new VoucherForm();
        form.setStartAt(LocalDateTime.now().withSecond(0).withNano(0));
        form.setEndAt(LocalDateTime.now().plusDays(30).withSecond(0).withNano(0));
        prepareForm(model, form, null);
        return "admin/voucher-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") VoucherForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, form, null);
            return "admin/voucher-form";
        }
        try {
            Voucher created = voucherService.create(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo voucher \"" + created.getCode() + "\".");
            return "redirect:/admin/voucher";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareForm(model, form, null);
            return "admin/voucher-form";
        }
    }

    @GetMapping("/{id}/sua")
    public String editForm(@PathVariable("id") Integer id, Model model) {
        Voucher voucher = voucherService.requireVoucher(id);
        VoucherForm form = new VoucherForm();
        form.setCode(voucher.getCode());
        form.setName(voucher.getName());
        form.setDescription(voucher.getDescription());
        form.setDiscountType(voucher.getDiscountType());
        form.setDiscountValue(voucher.getDiscountValue());
        form.setMinOrderValue(voucher.getMinOrderValue());
        form.setMaxDiscountAmount(voucher.getMaxDiscountAmount());
        form.setQuantity(voucher.getQuantity());
        form.setStartAt(voucher.getStartAt());
        form.setEndAt(voucher.getEndAt());
        form.setStatus(voucher.getStatus());
        prepareForm(model, form, id);
        model.addAttribute("usedQuantity", voucher.getUsedQuantity());
        return "admin/voucher-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Integer id,
                         @Valid @ModelAttribute("form") VoucherForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareForm(model, form, id);
            return "admin/voucher-form";
        }
        try {
            Voucher updated = voucherService.update(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật voucher \"" + updated.getCode() + "\".");
            return "redirect:/admin/voucher";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareForm(model, form, id);
            return "admin/voucher-form";
        }
    }

    /** Bật/ngừng áp dụng – thao tác ghi nên có nút xác nhận ở template. */
    @PostMapping("/{id}/doi-trang-thai")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        Voucher voucher = voucherService.toggleStatus(id);
        redirectAttributes.addFlashAttribute("successMessage",
                voucher.getStatus() == VoucherStatus.ACTIVE
                        ? "Đã bật áp dụng voucher \"" + voucher.getCode() + "\"."
                        : "Đã ngừng áp dụng voucher \"" + voucher.getCode() + "\".");
        return "redirect:/admin/voucher";
    }

    private void prepareForm(Model model, VoucherForm form, Integer voucherId) {
        model.addAttribute("form", form);
        model.addAttribute("voucherId", voucherId);
        model.addAttribute("discountTypes", DiscountType.values());
        model.addAttribute("voucherStatuses", VoucherStatus.values());
        model.addAttribute("adminMenu", "vouchers");
    }
}
