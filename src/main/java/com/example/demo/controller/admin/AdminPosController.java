package com.example.demo.controller.admin;

import com.example.demo.dto.admin.CounterSaleForm;
import com.example.demo.dto.admin.CounterSaleResult;
import com.example.demo.dto.admin.CustomerLookupView;
import com.example.demo.dto.admin.PosPriceRequest;
import com.example.demo.dto.admin.PosPriceResult;
import com.example.demo.dto.admin.PosQrRequest;
import com.example.demo.dto.admin.PosQrResult;
import com.example.demo.dto.admin.PosProductView;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.CounterSaleService;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.OrderService;
import com.example.demo.service.PosQrService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MÀN HÌNH BÁN HÀNG TẠI QUẦY (POS) – dành cho nhân viên đứng quầy:
 * tìm/quét SKU → dựng hoá đơn → thu tiền (tiền mặt/chuyển khoản) → in phiếu.
 *
 * Nghiệp vụ nằm ở {@link CounterSaleService}; controller chỉ điều hướng + trả JSON cho JavaScript.
 * Ghi CSDL duy nhất ở POST /admin/ban-hang (có xác nhận SweetAlert2 + flash message + redirect).
 */
@Controller
@RequestMapping("/admin/ban-hang")
@RequiredArgsConstructor
public class AdminPosController {

    /** Quầy chỉ thu tiền mặt hoặc chuyển khoản. */
    private static final List<PaymentMethod> POS_PAYMENT_METHODS =
            List.of(PaymentMethod.CASH, PaymentMethod.BANK_TRANSFER);

    /** Số hoá đơn gần đây hiện ở cột phải để in lại khi khách quay lại. */
    private static final int RECENT_LIMIT = 5;

    private final CounterSaleService counterSaleService;
    private final OrderService orderService;
    private final PosQrService posQrService;

    /** Màn hình quầy: ô tìm sản phẩm + hoá đơn đang lập. */
    @GetMapping
    public String screen(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CounterSaleForm());
        }
        prepareModel(model);
        return "admin/pos";
    }

    /** Tìm biến thể đang bán theo SKU/tên sản phẩm (JS gọi khi quầy gõ hoặc quét mã vạch). */
    @GetMapping("/tim")
    @ResponseBody
    public List<PosProductView> search(@RequestParam(name = "keyword", required = false) String keyword) {
        return counterSaleService.searchForPos(keyword);
    }

    /** Tra khách quen theo SĐT để gắn hoá đơn vào tài khoản khách. */
    @GetMapping("/khach")
    @ResponseBody
    public CustomerLookupView lookupCustomer(@RequestParam(name = "phone", required = false) String phone) {
        return counterSaleService.lookupCustomer(phone);
    }

    /** Tính tiền tạm thời (giá từ DB + voucher) để quầy đọc đúng số tiền phải thu. */
    @PostMapping("/tinh-tien")
    @ResponseBody
    public PosPriceResult price(@RequestBody PosPriceRequest request) {
        return counterSaleService.priceCart(request);
    }

    /** Dựng mã QR chuyển khoản (VietQR) cho hoá đơn đang lập; số tiền do server tính lại. */
    @PostMapping("/ma-qr")
    @ResponseBody
    public PosQrResult qr(@RequestBody PosQrRequest request) {
        return posQrService.qrFor(request);
    }

    /** Chốt hoá đơn: tạo đơn MANUAL + xác nhận + trừ kho + ghi nhận thanh toán. */
    @PostMapping
    public String sell(@ModelAttribute("form") CounterSaleForm form,
                       Model model,
                       @AuthenticationPrincipal CustomUserDetails admin,
                       RedirectAttributes redirectAttributes) {
        try {
            CounterSaleResult result = counterSaleService.sell(form, admin.getUserId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã bán hoá đơn " + result.order().getOrderCode() + ": đã trừ kho và ghi nhận thanh toán.");
            if (result.isCash()) {
                redirectAttributes.addFlashAttribute("cashReceived", result.cashReceived());
                redirectAttributes.addFlashAttribute("changeAmount", result.change());
            }
            return "redirect:/admin/ban-hang/phieu/" + result.order().getId();
        } catch (BusinessException ex) {
            // Giữ nguyên hoá đơn quầy vừa lập để nhân viên sửa (đổi số lượng, thu thêm tiền…) rồi bấm lại.
            model.addAttribute("form", form);
            model.addAttribute("formError", ex.getMessage());
            prepareModel(model);
            return "admin/pos";
        }
    }

    /** Phiếu bán hàng để in cho khách (kèm tiền khách đưa/tiền thừa nếu vừa bán xong). */
    @GetMapping("/phieu/{id}")
    public String receipt(@PathVariable("id") Integer id, Model model) {
        model.addAttribute("detail", orderService.adminOrderDetail(id));
        model.addAttribute("adminMenu", "pos");
        return "admin/pos-receipt";
    }

    private void prepareModel(Model model) {
        model.addAttribute("paymentMethods", POS_PAYMENT_METHODS);
        model.addAttribute("recentOrders", orderService.searchForAdmin(null, null, 1).getContent().stream()
                .limit(RECENT_LIMIT).toList());
        model.addAttribute("adminMenu", "pos");
    }
}
