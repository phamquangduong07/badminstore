package com.example.demo.service;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.admin.GhnSyncResult;
import com.example.demo.dto.ghn.GhnApiException;
import com.example.demo.dto.ghn.GhnCreateOrderRequest;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Payment;
import com.example.demo.enums.GhnStatus;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * NGHIỆP VỤ VẬN CHUYỂN GHN cho đơn hàng:
 *  1. Đẩy đơn (đã xác nhận, đã trừ kho) sang GHN để lấy MÃ VẬN ĐƠN → lưu vào orders.ghn_order_code.
 *  2. Đồng bộ TRẠNG THÁI vận đơn về hệ thống: GHN giao xong ⇒ đơn DELIVERED;
 *     GHN hoàn hàng ⇒ đơn RETURNED (nếu khách đã nhận) hoặc CANCELLED (nếu đang giao) – cộng lại kho.
 *
 * Mọi thay đổi trạng thái đều đi qua OrderService nên vẫn đúng máy trạng thái, vẫn trừ/cộng kho,
 * trả lượt voucher và ghi order_status_histories (bắt buộc theo AGENTS.md mục 4).
 */
@Service
@RequiredArgsConstructor
public class GhnShippingService {

    private static final Logger log = LoggerFactory.getLogger(GhnShippingService.class);

    /** GHN: 2 = người nhận trả tiền khi nhận hàng (dùng cho đơn COD). */
    public static final int PAYMENT_TYPE_BUYER_PAYS = 2;

    private final GhnClient ghnClient;
    private final GhnProperties ghnProperties;
    private final OrderRepository orderRepository;
    private final OrderService orderService;

    /** Đẩy đơn sang GHN và lưu mã vận đơn. */
    @Transactional
    public GhnOrderResult pushOrder(Integer orderId) {
        Order order = requireOrder(orderId);
        if (!ghnClient.isShippingAvailable()) {
            throw new BusinessException("Chưa cấu hình đủ GHN để tạo vận đơn: cần bật GHN, có token, "
                    + "shop-id và kho lấy hàng (from-district-id). Kiểm tra lại cấu hình app.ghn rồi thử lại.");
        }
        if (StringUtils.hasText(order.getGhnOrderCode())) {
            throw new BusinessException("Đơn " + order.getOrderCode() + " đã có vận đơn GHN "
                    + order.getGhnOrderCode() + " rồi.");
        }
        if (order.getOrderStatus() != OrderStatus.CONFIRMED && order.getOrderStatus() != OrderStatus.SHIPPING) {
            throw new BusinessException("Chỉ đẩy đơn sang GHN khi đơn đã được xác nhận (đã trừ kho). Đơn này đang ở \""
                    + order.getOrderStatus().getLabel() + "\".");
        }
        if (order.getShippingDistrictId() == null || !StringUtils.hasText(order.getShippingWardCode())) {
            throw new BusinessException("Đơn này chưa có mã Quận/Huyện – Phường/Xã của GHN nên GHN không nhận. "
                    + "Khách cần chọn lại địa chỉ theo GHN (ở bước thanh toán) rồi tạo vận đơn.");
        }
        if (StringUtils.hasText(order.getReceiverPhone()) && order.getReceiverPhone().length() < 10) {
            throw new BusinessException("Số điện thoại người nhận chưa hợp lệ để tạo vận đơn GHN.");
        }

        GhnOrderResult result = ghnClient.createOrder(buildRequest(order));
        if (!StringUtils.hasText(result.orderCode())) {
            throw new BusinessException("GHN không trả về mã vận đơn. Vui lòng thử lại sau.");
        }
        order.setGhnOrderCode(result.orderCode());
        orderRepository.save(order);
        log.info("Đã tạo vận đơn GHN {} cho đơn {} (phí {} VND)",
                result.orderCode(), order.getOrderCode(), result.totalFee());
        return result;
    }

    /**
     * Đồng bộ trạng thái vận đơn GHN về đơn của shop.
     * {@code actorUserId = null} nghĩa là job định kỳ tự chạy (lịch sử ghi "Hệ thống").
     */
    @Transactional
    public GhnSyncResult syncStatus(Integer orderId, Integer actorUserId) {
        Order order = requireOrder(orderId);
        String orderCode = order.getGhnOrderCode();
        if (!StringUtils.hasText(orderCode)) {
            throw new BusinessException("Đơn chưa có vận đơn GHN. Hãy bấm \"Đẩy sang GHN\" trước.");
        }

        GhnOrderDetail detail = ghnClient.orderDetail(orderCode);
        GhnStatus ghnStatus = detail.statusEnum();
        String label = ghnStatus.getLabel();
        OrderStatus current = order.getOrderStatus();
        OrderStatus target = ghnStatus.getTargetStatus();

        // GHN trả hàng về khi đơn đang giao ⇒ coi như huỷ đơn (cộng lại kho); đã giao rồi mới trả ⇒ hoàn trả.
        if (target == OrderStatus.RETURNED && current != OrderStatus.DELIVERED) {
            target = OrderStatus.CANCELLED;
        }
        if (target == null || target == current) {
            return new GhnSyncResult(orderCode, detail.status(), label, current, false,
                    "GHN báo \"" + label + "\" – đơn vẫn ở \"" + current.getLabel() + "\", không cần đổi.");
        }

        // Máy trạng thái đi từng bước (CONFIRMED → SHIPPING → DELIVERED) nên GHN báo "đã giao" khi đơn
        // còn ở CONFIRMED thì phải đi qua đủ các bước trung gian. Không bắt lỗi ở đây: nếu một bước
        // không hợp lệ thì BusinessException ném ra ngoài ⇒ cả transaction rollback, đơn giữ nguyên.
        List<OrderStatus> path = pathTo(current, target);
        if (path.isEmpty()) {
            return new GhnSyncResult(orderCode, detail.status(), label, current, false,
                    "GHN báo \"" + label + "\" nhưng máy trạng thái không cho chuyển đơn từ \""
                            + current.getLabel() + "\" sang \"" + target.getLabel() + "\".");
        }

        OrderStatus lastApplied = current;
        for (int index = 0; index < path.size(); index++) {
            OrderStatus step = path.get(index);
            boolean lastStep = index == path.size() - 1;
            String note = lastStep
                    ? "Đồng bộ vận đơn GHN (" + orderCode + "): " + label
                    : "Đồng bộ vận đơn GHN (" + orderCode + "): cập nhật theo hành trình giao hàng";
            if (actorUserId == null) {
                orderService.changeStatusBySystem(orderId, step, note);
            } else {
                orderService.changeStatusByAdmin(orderId, step, actorUserId, note);
            }
            lastApplied = step;
        }
        return new GhnSyncResult(orderCode, detail.status(), label, lastApplied, true,
                "Đã cập nhật đơn sang \"" + lastApplied.getLabel() + "\" theo trạng thái GHN \"" + label + "\".");
    }

    /**
     * Đường đi ngắn nhất trong máy trạng thái từ {@code from} tới {@code to}
     * (rỗng = không tới được, ví dụ CONFIRMED → RETURNED vì phải giao xong mới hoàn trả được).
     */
    static List<OrderStatus> pathTo(OrderStatus from, OrderStatus to) {
        if (from == to) {
            return List.of();
        }
        Deque<List<OrderStatus>> queue = new ArrayDeque<>();
        queue.add(List.of(from));
        Set<OrderStatus> visited = EnumSet.of(from);
        while (!queue.isEmpty()) {
            List<OrderStatus> path = queue.poll();
            for (OrderStatus next : path.get(path.size() - 1).nextStatuses()) {
                if (!visited.add(next)) {
                    continue;
                }
                List<OrderStatus> extended = new ArrayList<>(path);
                extended.add(next);
                if (next == to) {
                    return List.copyOf(extended.subList(1, extended.size()));
                }
                queue.add(extended);
            }
        }
        return List.of();
    }

    /** Phí dự kiến GHN sẽ thu cho đơn này (null nếu GHN tắt hoặc đơn chưa có mã quận GHN). */
    @Transactional(readOnly = true)
    public BigDecimal estimatedFee(Order order) {
        if (!ghnClient.isShippingAvailable() || order.getShippingDistrictId() == null) {
            return null;
        }
        try {
            return ghnClient.fee(order.getShippingDistrictId(), order.getShippingWardCode(), weightOf(order))
                    .totalAmount();
        } catch (GhnApiException ex) {
            log.warn("Không lấy được phí GHN cho đơn {}: {}", order.getOrderCode(), ex.getMessage());
            return null;
        }
    }

    /** Khối lượng kiện hàng của đơn: mỗi cây vợt một kiện, cộng theo cấu hình trong application.yml. */
    public int weightOf(Order order) {
        int totalQuantity = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        int base = ghnProperties.getWeightGram();
        return base + Math.max(0, totalQuantity - 1) * 300;
    }

    // ------------------------------------------------------------------ nội bộ

    private GhnCreateOrderRequest buildRequest(Order order) {
        GhnCreateOrderRequest request = new GhnCreateOrderRequest();
        request.setClientOrderCode(order.getOrderCode());
        request.setToName(order.getReceiverName());
        request.setToPhone(order.getReceiverPhone());
        request.setToAddress(order.getShippingAddress());
        request.setToDistrictId(order.getShippingDistrictId());
        request.setToWardCode(order.getShippingWardCode());
        request.setWeight(weightOf(order));
        request.setLength(ghnProperties.getLengthCm());
        request.setWidth(ghnProperties.getWidthCm());
        request.setHeight(ghnProperties.getHeightCm());
        request.setInsuranceValue(ghnProperties.getInsuranceValue());
        request.setServiceTypeId(ghnProperties.getServiceTypeId());
        request.setNote(order.getNote());
        request.setContent("Vợt cầu lông BadStore");

        // Đơn COD thì GHN thu tiền hộ (người nhận trả), còn lại shop trả phí vận chuyển.
        if (isCodPending(order)) {
            request.setPaymentTypeId(PAYMENT_TYPE_BUYER_PAYS);
            request.setCodAmount(order.getTotalAmount().longValue());
        } else {
            request.setPaymentTypeId(ghnProperties.getPaymentTypeId());
            request.setCodAmount(0);
        }

        List<GhnCreateOrderRequest.GhnOrderItem> items = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            String name = item.getProductName() + " (" + item.getColor() + "/" + item.getWeightClass()
                    + "/" + item.getGripSize() + ")";
            items.add(new GhnCreateOrderRequest.GhnOrderItem(name, item.getSku(), item.getQuantity(),
                    item.getUnitPrice().longValue(), Math.max(200, weightOf(order) / Math.max(1, order.getItems().size()))));
        }
        request.setItems(items);
        return request;
    }

    /** Đơn COD chưa thu tiền ⇒ GHN phải thu hộ tiền khi giao. */
    private boolean isCodPending(Order order) {
        Payment payment = order.getPayment();
        return payment != null && payment.getPaymentMethod() == PaymentMethod.COD
                && payment.getPaymentStatus() == PaymentStatus.PENDING;
    }

    private Order requireOrder(Integer orderId) {
        return orderRepository.findDetailForAdmin(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng cần thao tác với GHN."));
    }
}
