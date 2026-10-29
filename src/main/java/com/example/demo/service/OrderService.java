package com.example.demo.service;

import com.example.demo.dto.admin.ManualOrderForm;
import com.example.demo.dto.admin.ManualOrderLineForm;
import com.example.demo.dto.shop.OrderDetailView;
import com.example.demo.dto.shop.OrderSummaryView;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Payment;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAddress;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Vòng đời đơn hàng: xem đơn của khách, huỷ/hoàn trả, và (cho admin) đổi trạng thái + xác nhận thanh toán.
 *
 * Quy tắc bất di bất dịch (AGENTS.md mục 4):
 * – mỗi lần đổi trạng thái là 1 transaction, luôn ghi 1 dòng order_status_histories;
 * – máy trạng thái nằm ở enum OrderStatus, chuyển sai ⇒ BusinessException;
 * – PENDING → CONFIRMED mới TRỪ KHO (UPDATE có điều kiện, duyệt theo product_variant_id tăng dần);
 * – huỷ (từ CONFIRMED/SHIPPING) hoặc hoàn trả ⇒ CỘNG LẠI KHO, trả lượt voucher, payment đã PAID ⇒ REFUNDED;
 * – stock_deducted luôn set cùng lúc với trạng thái để khớp ràng buộc CK_orders_stock.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /** Số đơn trên 1 trang của danh sách đơn (khách và admin dùng chung). */
    public static final int PAGE_SIZE = 10;

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final VoucherService voucherService;
    private final OrderCodeGenerator orderCodeGenerator;

    // ==================== phía KHÁCH ====================

    @Transactional(readOnly = true)
    public Page<OrderSummaryView> myOrders(Integer userId, int pageNumber) {
        return orderRepository.findMyOrderSummaries(userId, pageRequest(pageNumber));
    }

    @Transactional(readOnly = true)
    public OrderDetailView myOrderDetail(Integer userId, Integer orderId) {
        return toDetailView(requireOwnOrder(userId, orderId));
    }

    /** Khách tự huỷ đơn khi đơn còn PENDING/CONFIRMED (đang giao thì phải gọi hotline). */
    @Transactional
    public void cancelByCustomer(Integer userId, Integer orderId, String reason) {
        Order order = requireOwnOrder(userId, orderId);
        if (order.getOrderStatus() == OrderStatus.SHIPPING) {
            throw new BusinessException("Đơn hàng đang trên đường giao nên không thể tự huỷ. "
                    + "Bạn gọi hotline 1900 0000 để được hỗ trợ nhé.");
        }
        String note = requireReason(reason, "huỷ đơn");
        changeStatus(order, OrderStatus.CANCELLED, userRepository.getReferenceById(userId),
                "Khách huỷ đơn: " + note);
    }

    /** Khách gửi yêu cầu hoàn trả 100% cho đơn đã giao. */
    @Transactional
    public void returnByCustomer(Integer userId, Integer orderId, String reason) {
        Order order = requireOwnOrder(userId, orderId);
        String note = requireReason(reason, "hoàn trả");
        changeStatus(order, OrderStatus.RETURNED, userRepository.getReferenceById(userId),
                "Khách yêu cầu hoàn trả: " + note);
    }

    // ==================== phía ADMIN ====================

    @Transactional(readOnly = true)
    public Page<OrderSummaryView> searchForAdmin(OrderStatus status, String keyword, int pageNumber) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return orderRepository.findOrderSummariesForAdmin(status, normalizedKeyword, pageRequest(pageNumber));
    }

    @Transactional(readOnly = true)
    public OrderDetailView adminOrderDetail(Integer orderId) {
        Order order = orderRepository.findDetailForAdmin(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cần xem."));
        return toDetailView(order);
    }

    /** Admin đổi trạng thái đơn (xác nhận, giao hàng, giao thành công, huỷ, hoàn trả). */
    @Transactional
    public void changeStatusByAdmin(Integer orderId, OrderStatus target, Integer adminUserId, String note) {
        Order order = orderRepository.findDetailForAdmin(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cần cập nhật."));
        changeStatus(order, target, userRepository.getReferenceById(adminUserId), trimToNull(note));
    }

    /**
     * HỆ THỐNG tự đổi trạng thái – dùng cho việc đồng bộ vận đơn GHN hoặc job định kỳ.
     * Người thực hiện ưu tiên lấy admin tạo đơn, nếu không có thì lấy một tài khoản ADMIN đầu tiên
     * (cột changed_by cho phép NULL nhưng ghi được admin nào thì timeline rõ ràng hơn).
     */
    @Transactional
    public void changeStatusBySystem(Integer orderId, OrderStatus target, String note) {
        Order order = orderRepository.findDetailForAdmin(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cần cập nhật."));
        changeStatus(order, target, systemActor(order), trimToNull(note));
    }

    private User systemActor(Order order) {
        if (order.getCreatedBy() != null) {
            return order.getCreatedBy();
        }
        return userRepository.findByRoleOrderByCreatedAtDesc(UserRole.ADMIN).stream().findFirst().orElse(null);
    }

    /** Admin xác nhận đã nhận tiền (chuyển khoản/tiền mặt). */
    @Transactional
    public void confirmPayment(Integer orderId, String transactionCode, Integer adminUserId) {
        Order order = orderRepository.findDetailForAdmin(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cần xác nhận thanh toán."));
        Payment payment = order.getPayment();
        if (payment == null) {
            throw new BusinessException("Đơn hàng này chưa có thông tin thanh toán.");
        }
        if (payment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new BusinessException("Đơn hàng này đã được xác nhận thanh toán trước đó.");
        }
        if (order.getOrderStatus().isFinal()) {
            throw new BusinessException("Đơn hàng đã huỷ hoặc đã hoàn trả nên không thể xác nhận thanh toán.");
        }

        payment.markPaid(userRepository.getReferenceById(adminUserId));
        payment.setTransactionCode(trimToNull(transactionCode));
        paymentRepository.save(payment);
    }

    // ==================== ADMIN TẠO ĐƠN THỦ CÔNG ====================

    /**
     * Tạo đơn thủ công (bán tại quầy / khách đặt qua điện thoại) theo AGENTS.md mục 4.6:
     * {@code order_source = MANUAL}, {@code created_by = admin}, {@code user_id} có thể NULL (khách vãng lai),
     * và có thể xác nhận ngay (trừ kho luôn) trong cùng transaction.
     *
     * An toàn dữ liệu: kiểm tra đủ tồn kho TRƯỚC khi ghi, và nếu xác nhận ngay thì việc trừ kho vẫn
     * dùng UPDATE có điều kiện ở {@link #changeStatus} nên không thể bán quá kho.
     */
    @Transactional
    public Order createManualOrder(ManualOrderForm form, Integer adminUserId) {
        User admin = userRepository.getReferenceById(adminUserId);

        // 1) Gom dòng hàng theo SKU (nhập trùng SKU thì cộng dồn vào một dòng)
        Map<Integer, ProductVariant> variantById = new LinkedHashMap<>();
        Map<Integer, Integer> quantityByVariantId = new LinkedHashMap<>();
        int rowNumber = 0;
        for (ManualOrderLineForm line : form.getLines()) {
            rowNumber++;
            if (line == null || !StringUtils.hasText(line.getSku())) {
                continue;   // ô trống: bỏ qua để admin không phải xoá hết các dòng thừa
            }
            String sku = line.getSku().trim();
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            if (quantity < 1) {
                throw new BusinessException("Dòng " + rowNumber + ": số lượng phải từ 1 trở lên.");
            }
            int lineNumber = rowNumber;   // bản sao final để dùng trong lambda báo lỗi
            ProductVariant variant = productVariantRepository.findBySku(sku)
                    .orElseThrow(() -> new BusinessException(
                            "Dòng " + lineNumber + ": không tìm thấy SKU \"" + sku + "\" trong hệ thống."));
            if (variant.getStatus() != VariantStatus.ACTIVE) {
                throw new BusinessException("Dòng " + rowNumber + ": SKU " + variant.getSku()
                        + " đang ở trạng thái ngừng bán, hãy kích hoạt lại trước khi bán.");
            }
            variantById.put(variant.getId(), variant);
            quantityByVariantId.merge(variant.getId(), quantity, Integer::sum);
        }
        if (quantityByVariantId.isEmpty()) {
            throw new BusinessException("Đơn thủ công phải có ít nhất 1 sản phẩm: hãy nhập SKU và số lượng.");
        }

        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : quantityByVariantId.entrySet()) {
            ProductVariant variant = variantById.get(entry.getKey());
            int quantity = entry.getValue();
            if (variant.getStockQuantity() < quantity) {
                throw new BusinessException("SKU " + variant.getSku() + " chỉ còn " + variant.getStockQuantity()
                        + " sản phẩm nhưng đơn cần " + quantity + ".");
            }
            items.add(OrderItem.from(variant, quantity));
        }

        // 2) Khách hàng: theo email (nếu admin nhập) hoặc khách vãng lai
        User customer = null;
        if (StringUtils.hasText(form.getCustomerEmail())) {
            String email = form.getCustomerEmail().trim();
            customer = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException(
                            "Không tìm thấy tài khoản khách hàng với email \"" + email + "\"."));
        }

        // 3) Thông tin nhận hàng: để trống thì lấy từ tài khoản khách và địa chỉ mặc định của khách
        String receiverName = trimToNull(form.getReceiverName());
        String receiverPhone = trimToNull(form.getReceiverPhone());
        String shippingAddress = trimToNull(form.getShippingAddress());
        if (customer != null) {
            if (receiverName == null) {
                receiverName = customer.getFullName();
            }
            if (receiverPhone == null) {
                receiverPhone = trimToNull(customer.getPhone());
            }
            if (shippingAddress == null) {
                Integer customerId = customer.getId();
                shippingAddress = addressRepository
                        .findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(customerId)
                        .map(UserAddress::getFullAddress)
                        .orElse(null);
            }
        }
        if (receiverName == null) {
            throw new BusinessException("Vui lòng nhập tên người nhận hàng.");
        }
        if (receiverPhone == null) {
            throw new BusinessException("Vui lòng nhập số điện thoại người nhận hàng.");
        }
        if (shippingAddress == null) {
            throw new BusinessException("Vui lòng nhập địa chỉ giao hàng "
                    + "(khách này chưa có địa chỉ mặc định trong sổ địa chỉ).");
        }

        PaymentMethod paymentMethod = parseManualPaymentMethod(form.getPaymentMethod());
        BigDecimal shippingFee = form.getShippingFee() == null ? BigDecimal.ZERO : form.getShippingFee();
        if (shippingFee.signum() < 0 || shippingFee.compareTo(new BigDecimal("1000000")) > 0) {
            throw new BusinessException("Phí vận chuyển phải trong khoảng 0 – 1.000.000 ₫.");
        }
        shippingFee = shippingFee.setScale(0, RoundingMode.HALF_UP);

        Order order = new Order();
        String requestedCode = trimToNull(form.getOrderCode());
        order.setOrderCode(requestedCode == null
                ? orderCodeGenerator.next()
                : orderCodeGenerator.validatedOrNext(requestedCode));
        order.setOrderSource(OrderSource.MANUAL);
        order.setCreatedBy(admin);
        order.setUser(customer);
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setShippingAddress(shippingAddress);
        order.setOrderStatus(OrderStatus.PENDING);
        order.setStockDeducted(false);
        order.setNote(trimToNull(form.getNote()));
        items.forEach(order::addItem);

        BigDecimal subtotal = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        if (StringUtils.hasText(form.getVoucherCode())) {
            voucher = voucherService.requireUsable(form.getVoucherCode(), subtotal);
            discount = voucherService.discountOf(voucher, subtotal);
        }
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setDiscountAmount(discount);
        order.setVoucher(voucher);
        order.setTotalAmount(subtotal.add(shippingFee).subtract(discount));

        Order saved = orderRepository.save(order);
        if (voucher != null) {
            voucherService.consume(voucher);
        }

        Payment payment = new Payment();
        payment.setOrder(saved);
        payment.setPaymentMethod(paymentMethod);
        // Bán tại quầy thu tiền ngay ⇒ ghi nhận đã thanh toán luôn; COD/chuyển khoản thì còn chờ.
        boolean paidAtCounter = paymentMethod == PaymentMethod.CASH;
        payment.setPaymentStatus(paidAtCounter ? PaymentStatus.PAID : PaymentStatus.PENDING);
        payment.setAmount(saved.getTotalAmount());
        if (paidAtCounter) {
            payment.markPaid(admin);
        }
        paymentRepository.save(payment);
        saved.setPayment(payment);   // giữ liên kết hai chiều trong bộ nhớ cho người gọi

        historyRepository.save(OrderStatusHistory.record(saved, null, OrderStatus.PENDING, admin,
                customer == null ? "Admin tạo đơn thủ công (khách mua tại quầy)" : "Admin tạo đơn thủ công"));

        // 4) Xác nhận ngay: trừ kho + thêm 1 dòng lịch sử, tất cả trong transaction này
        if (form.isConfirmNow()) {
            changeStatus(saved, OrderStatus.CONFIRMED, admin, "Admin xác nhận ngay khi tạo đơn");
        }
        return saved;
    }

    /** Đơn thủ công cho phép cả tiền mặt tại quầy (CASH), khác đơn online. */
    private PaymentMethod parseManualPaymentMethod(String rawMethod) {
        String value = trimToNull(rawMethod);
        if (value == null) {
            throw new BusinessException("Vui lòng chọn phương thức thanh toán cho đơn hàng.");
        }
        return switch (value.toUpperCase()) {
            case "CASH" -> PaymentMethod.CASH;
            case "COD" -> PaymentMethod.COD;
            case "BANK_TRANSFER" -> PaymentMethod.BANK_TRANSFER;
            default -> throw new BusinessException("Phương thức thanh toán không hợp lệ.");
        };
    }

    // ==================== phần dùng chung ====================

    /**
     * Đổi trạng thái đơn theo đúng máy trạng thái, kèm trừ/cộng kho, cập nhật thanh toán
     * và ghi lịch sử. Chạy trong transaction của phương thức gọi (mỗi lần đổi = 1 transaction).
     */
    private void changeStatus(Order order, OrderStatus target, User actor, String note) {
        OrderStatus current = order.getOrderStatus();
        if (current == target) {
            throw new BusinessException("Đơn hàng đang ở trạng thái \"" + target.getLabel() + "\" rồi.");
        }
        if (!current.canTransitionTo(target)) {
            throw new BusinessException("Không thể chuyển đơn từ \"" + current.getLabel()
                    + "\" sang \"" + target.getLabel() + "\".");
        }

        // 1) Kho: xác nhận đơn ⇒ trừ kho; huỷ/hoàn trả ⇒ cộng lại kho + trả lượt voucher
        if (target == OrderStatus.CONFIRMED) {
            deductStock(order);
        } else if (target == OrderStatus.CANCELLED || target == OrderStatus.RETURNED) {
            restoreStock(order);
            Voucher voucher = order.getVoucher();
            voucherService.release(voucher == null ? null : voucher.getId());
            order.setCancelReason(note);
        }

        // 2) Thanh toán: COD giao thành công ⇒ đã thanh toán; hoàn trả đơn đã PAID ⇒ hoàn tiền
        Payment payment = order.getPayment();
        if (payment != null) {
            if (target == OrderStatus.DELIVERED && payment.getPaymentMethod() == PaymentMethod.COD
                    && payment.getPaymentStatus() == PaymentStatus.PENDING) {
                payment.markPaid(actor);
            } else if (target == OrderStatus.RETURNED && payment.getPaymentStatus() == PaymentStatus.PAID) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
            }
            paymentRepository.save(payment);
        }

        // 3) Trạng thái + cờ trừ kho (khớp CK_orders_stock)
        order.setOrderStatus(target);
        order.setStockDeducted(target.isStockDeducted());
        orderRepository.save(order);

        // 4) Mỗi lần đổi trạng thái BẮT BUỘC ghi 1 dòng lịch sử
        historyRepository.save(OrderStatusHistory.record(order, current, target, actor, note));
    }

    /** Trừ kho theo product_variant_id TĂNG DẦN (tránh deadlock khi 2 đơn cùng lúc); thiếu hàng ⇒ rollback. */
    private void deductStock(Order order) {
        for (OrderItem item : sortedItems(order)) {
            int updated = productVariantRepository.deductStock(item.getProductVariant().getId(), item.getQuantity());
            if (updated == 0) {
                throw new BusinessException("Không đủ tồn kho cho SKU " + item.getSku() + " (" + item.getProductName()
                        + ") nên chưa thể xác nhận đơn. Bạn kiểm tra lại kho hoặc huỷ đơn này nhé.");
            }
        }
    }

    /** Cộng lại kho – chỉ chạy khi đơn từng trừ kho (CONFIRMED/SHIPPING/DELIVERED). */
    private void restoreStock(Order order) {
        if (!order.isStockDeducted()) {
            return;
        }
        for (OrderItem item : sortedItems(order)) {
            productVariantRepository.restoreStock(item.getProductVariant().getId(), item.getQuantity());
        }
    }

    private List<OrderItem> sortedItems(Order order) {
        return order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProductVariant().getId()))
                .toList();
    }

    private Order requireOwnOrder(Integer userId, Integer orderId) {
        return orderRepository.findDetailForUser(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng này trong tài khoản của bạn."));
    }

    private OrderDetailView toDetailView(Order order) {
        List<OrderStatusHistory> timeline = historyRepository.findTimeline(order.getId());
        Voucher voucher = order.getVoucher();
        String voucherLabel = voucher == null ? null : voucher.getCode() + " – " + voucherService.describe(voucher);
        return new OrderDetailView(order, timeline, voucherLabel);
    }

    private String requireReason(String reason, String action) {
        String trimmed = trimToNull(reason);
        if (trimmed == null || trimmed.length() < 10) {
            throw new BusinessException("Vui lòng nhập lý do " + action + " (ít nhất 10 ký tự) để BadStore hỗ trợ bạn tốt hơn.");
        }
        return trimmed;
    }

    private String normalizeKeyword(String keyword) {
        String trimmed = trimToNull(keyword);
        return trimmed == null ? null : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PageRequest pageRequest(int pageNumber) {
        return PageRequest.of(Math.max(pageNumber, 1) - 1, PAGE_SIZE);
    }
}
