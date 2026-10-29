package com.example.demo.controller.admin;

import com.example.demo.dto.admin.ManualOrderForm;
import com.example.demo.dto.admin.GhnSyncResult;
import com.example.demo.dto.ghn.GhnApiException;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.dto.shop.OrderDetailView;
import com.example.demo.dto.shop.OrderSummaryView;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.UserRole;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.GhnAddressService;
import com.example.demo.service.GhnShippingService;
import com.example.demo.service.OrderService;
import com.example.demo.service.QrPaymentService;
import com.example.demo.util.Money;
import com.example.demo.util.Pagination;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản lý đơn hàng (admin): danh sách có lọc trạng thái + tìm kiếm, chi tiết có timeline,
 * đổi trạng thái theo máy trạng thái, xác nhận thanh toán, và nút xác nhận nhanh ở danh sách.
 *
 * Mọi thao tác ghi đều là POST + xác nhận SweetAlert2 + flash message + redirect (Post-Redirect-Get).
 * Nghiệp vụ (trừ/cộng kho, voucher, payment) nằm ở OrderService – controller chỉ điều hướng.
 */
@Controller
@RequestMapping("/admin/don-hang")
@RequiredArgsConstructor
public class AdminOrderController {

    private static final int PAGE_LINK_WINDOW = 5;

    /** Giới hạn số gợi ý hiển thị trong datalist của form tạo đơn thủ công. */
    private static final int MAX_SUGGESTIONS = 100;

    private final OrderService orderService;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final GhnShippingService ghnShippingService;
    private final GhnAddressService ghnAddressService;
    private final QrPaymentService qrPaymentService;

    @GetMapping
    public String list(@RequestParam(name = "status", required = false) OrderStatus status,
                       @RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        Page<OrderSummaryView> orders = orderService.searchForAdmin(status, keyword, page);
        if (orders.getTotalPages() > 0 && page > orders.getTotalPages()) {
            orders = orderService.searchForAdmin(status, keyword, orders.getTotalPages());
        }

        model.addAttribute("orders", orders);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("statusFilter", status);
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("pageNumbers", pageNumbers(orders));
        model.addAttribute("adminMenu", "orders");
        return "admin/orders";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Integer id, Model model) {
        var detail = orderService.adminOrderDetail(id);
        model.addAttribute("detail", detail);
        qrPaymentService.forOrder(detail).ifPresent(qr -> model.addAttribute("paymentQr", qr));
        // Chỉ hiện các trạng thái mà máy trạng thái cho phép chuyển tới từ trạng thái hiện tại
        model.addAttribute("nextStatuses", detail.getOrder().getOrderStatus().nextStatuses());
        model.addAttribute("ghnEnabled", ghnAddressService.isAvailable());
        model.addAttribute("ghnMock", ghnAddressService.isMock());
        model.addAttribute("ghnFeeEstimate", ghnShippingService.estimatedFee(detail.getOrder()));
        model.addAttribute("ghnWeightGram", ghnShippingService.weightOf(detail.getOrder()));
        model.addAttribute("adminMenu", "orders");
        return "admin/order-detail";
    }

    /** Đẩy đơn sang GHN để lấy mã vận đơn (đơn phải đã xác nhận – đã trừ kho). */
    @PostMapping("/{id}/ghn/tao-van-don")
    public String pushToGhn(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        try {
            GhnOrderResult result = ghnShippingService.pushOrder(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo vận đơn GHN " + result.orderCode() + ", phí vận chuyển "
                            + Money.format(BigDecimal.valueOf(result.totalFee())) + ".");
        } catch (BusinessException | GhnApiException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tạo được vận đơn GHN: " + ex.getMessage());
        }
        return "redirect:/admin/don-hang/" + id;
    }

    /** Kéo trạng thái vận đơn từ GHN về đơn của shop (giao xong ⇒ DELIVERED, hoàn hàng ⇒ RETURNED/CANCELLED). */
    @PostMapping("/{id}/ghn/dong-bo")
    public String syncGhn(@PathVariable("id") Integer id,
                          @AuthenticationPrincipal CustomUserDetails admin,
                          RedirectAttributes redirectAttributes) {
        try {
            GhnSyncResult result = ghnShippingService.syncStatus(id, admin.getUserId());
            if (result.changed()) {
                redirectAttributes.addFlashAttribute("successMessage", result.message());
            } else {
                redirectAttributes.addFlashAttribute("successMessage", "Đã kiểm tra GHN: " + result.message());
            }
        } catch (BusinessException | GhnApiException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không đồng bộ được trạng thái GHN: "
                    + ex.getMessage());
        }
        return "redirect:/admin/don-hang/" + id;
    }

    /** Xác nhận nhanh từ danh sách: PENDING → CONFIRMED (kèm trừ kho). */
    @PostMapping("/{id}/xac-nhan")
    public String quickConfirm(@PathVariable("id") Integer id,
                               @AuthenticationPrincipal CustomUserDetails admin,
                               RedirectAttributes redirectAttributes) {
        orderService.changeStatusByAdmin(id, OrderStatus.CONFIRMED, admin.getUserId(),
                "Admin xác nhận đơn từ danh sách");
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xác nhận đơn và trừ kho. Đơn chuyển sang trạng thái \"Đã xác nhận\".");
        return "redirect:/admin/don-hang/" + id;
    }

    /** Đổi trạng thái đơn (xác nhận / giao hàng / giao thành công / huỷ / hoàn trả). */
    @PostMapping("/{id}/trang-thai")
    public String changeStatus(@PathVariable("id") Integer id,
                               @RequestParam("target") OrderStatus target,
                               @RequestParam(name = "note", required = false) String note,
                               @AuthenticationPrincipal CustomUserDetails admin,
                               RedirectAttributes redirectAttributes) {
        orderService.changeStatusByAdmin(id, target, admin.getUserId(), note);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã chuyển đơn sang trạng thái \"" + target.getLabel() + "\".");
        return "redirect:/admin/don-hang/" + id;
    }

    /** Xác nhận đã nhận tiền (chuyển khoản / tiền mặt tại quầy). */
    @PostMapping("/{id}/xac-nhan-thanh-toan")
    public String confirmPayment(@PathVariable("id") Integer id,
                                 @RequestParam(name = "transactionCode", required = false) String transactionCode,
                                 @AuthenticationPrincipal CustomUserDetails admin,
                                 RedirectAttributes redirectAttributes) {
        orderService.confirmPayment(id, transactionCode, admin.getUserId());
        redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận thanh toán cho đơn hàng.");
        return "redirect:/admin/don-hang/" + id;
    }

    // ==================== TẠO ĐƠN THỦ CÔNG ====================

    /** Form tạo đơn thủ công (bán tại quầy / khách đặt qua điện thoại). */
    @GetMapping("/tao-don")
    public String createForm(Model model) {
        prepareCreateModel(model);
        model.addAttribute("form", new ManualOrderForm());
        return "admin/order-create";
    }

    @PostMapping("/tao-don")
    public String create(@ModelAttribute("form") ManualOrderForm form,
                         Model model,
                         @AuthenticationPrincipal CustomUserDetails admin,
                         RedirectAttributes redirectAttributes) {
        try {
            Order order = orderService.createManualOrder(form, admin.getUserId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã tạo đơn thủ công " + order.getOrderCode() + ".");
            return "redirect:/admin/don-hang/" + order.getId();
        } catch (BusinessException ex) {
            // Lỗi nghiệp vụ (SKU sai, thiếu tồn kho, thiếu địa chỉ…) ⇒ hiện lại form kèm lý do,
            // giữ nguyên dữ liệu admin vừa nhập để sửa ngay tại chỗ.
            model.addAttribute("form", form);
            model.addAttribute("formError", ex.getMessage());
            prepareCreateModel(model);
            return "admin/order-create";
        }
    }

    /** Dữ liệu gợi ý cho form tạo đơn: SKU đang bán và email khách hàng (datalist). */
    private void prepareCreateModel(Model model) {
        List<ProductVariant> variants = productVariantRepository.findActiveWithProduct();
        model.addAttribute("variants", variants.size() > MAX_SUGGESTIONS
                ? variants.subList(0, MAX_SUGGESTIONS) : variants);

        List<User> customers = userRepository.findByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER);
        model.addAttribute("customers", customers.size() > MAX_SUGGESTIONS
                ? customers.subList(0, MAX_SUGGESTIONS) : customers);

        model.addAttribute("paymentMethods", List.of(PaymentMethod.CASH, PaymentMethod.COD, PaymentMethod.BANK_TRANSFER));
        model.addAttribute("adminMenu", "orders");
    }

    /** Cửa sổ số trang – dùng tiện ích chung để mọi trang quản trị hiển thị giống nhau. */
    private List<Integer> pageNumbers(Page<?> result) {
        return Pagination.pageNumbers(result, PAGE_LINK_WINDOW);
    }
}
