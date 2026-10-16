package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.admin.ManualOrderForm;
import com.example.demo.dto.admin.ManualOrderLineForm;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.UserAddress;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử MÁY TRẠNG THÁI đơn hàng bằng Mockito:
 * trừ kho khi xác nhận, cộng lại kho khi huỷ/hoàn trả, trả lượt voucher, cập nhật thanh toán
 * và điều kiện bắt buộc "mỗi lần đổi trạng thái phải ghi 1 dòng lịch sử".
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Integer ORDER_ID = 100;
    private static final Integer USER_ID = 7;
    private static final Integer ADMIN_ID = 1;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserAddressRepository addressRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @InjectMocks
    private OrderService orderService;

    @Test
    @DisplayName("Xác nhận đơn: trừ kho theo product_variant_id tăng dần, set stock_deducted = 1, ghi lịch sử")
    void confirmOrder_deductsStockAndWritesHistory() {
        Order order = order(OrderStatus.PENDING, false, payment(PaymentMethod.COD, PaymentStatus.PENDING),
                item(12, 1), item(11, 2));
        stubLookup(order, null);
        when(productVariantRepository.deductStock(11, 2)).thenReturn(1);
        when(productVariantRepository.deductStock(12, 1)).thenReturn(1);

        orderService.changeStatusByAdmin(ORDER_ID, OrderStatus.CONFIRMED, ADMIN_ID, "Duyệt đơn");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.isStockDeducted()).isTrue();

        InOrder inOrder = inOrder(productVariantRepository);
        inOrder.verify(productVariantRepository).deductStock(11, 2);
        inOrder.verify(productVariantRepository).deductStock(12, 1);

        ArgumentCaptor<OrderStatusHistory> captor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(captor.getValue().getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(captor.getValue().getNote()).isEqualTo("Duyệt đơn");
        verify(voucherService, never()).release(any());
    }

    @Test
    @DisplayName("Thiếu tồn kho lúc xác nhận: báo lỗi nêu rõ SKU, không đổi trạng thái, không ghi lịch sử")
    void confirmOrder_rollsBackWhenStockNotEnough() {
        Order order = order(OrderStatus.PENDING, false, payment(PaymentMethod.COD, PaymentStatus.PENDING), item(11, 3));
        stubLookup(order, null);
        when(productVariantRepository.deductStock(11, 3)).thenReturn(0);

        assertThatThrownBy(() -> orderService.changeStatusByAdmin(ORDER_ID, OrderStatus.CONFIRMED, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không đủ tồn kho cho SKU YX-AX100ZZ-01");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.isStockDeducted()).isFalse();
        verify(historyRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Khách huỷ đơn đã xác nhận: cộng lại kho + trả lượt voucher + ghi lý do")
    void cancelConfirmedOrder_restoresStockAndVoucher() {
        Order order = order(OrderStatus.CONFIRMED, true, payment(PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID),
                item(11, 2));
        order.setVoucher(voucher(9));
        stubLookup(order, USER_ID);
        when(productVariantRepository.restoreStock(11, 2)).thenReturn(1);

        orderService.cancelByCustomer(USER_ID, ORDER_ID, "Tôi đổi ý, muốn mua mẫu vợt khác");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.isStockDeducted()).isFalse();
        assertThat(order.getCancelReason()).contains("đổi ý");
        verify(productVariantRepository).restoreStock(11, 2);
        verify(voucherService).release(9);
        verify(historyRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Huỷ đơn còn PENDING: không đụng kho nhưng vẫn trả lại lượt voucher")
    void cancelPendingOrder_doesNotTouchStock() {
        Order order = order(OrderStatus.PENDING, false, payment(PaymentMethod.COD, PaymentStatus.PENDING), item(11, 2));
        order.setVoucher(voucher(9));
        stubLookup(order, USER_ID);

        orderService.cancelByCustomer(USER_ID, ORDER_ID, "Tôi muốn huỷ đơn hàng này");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(productVariantRepository, never()).restoreStock(any(), anyInt());
        verify(voucherService).release(9);
    }

    @Test
    @DisplayName("Giao thành công đơn COD: đánh dấu đã thanh toán (có paid_at)")
    void deliverCodOrder_marksPaymentPaid() {
        Payment payment = payment(PaymentMethod.COD, PaymentStatus.PENDING);
        Order order = order(OrderStatus.SHIPPING, true, payment, item(11, 1));
        stubLookup(order, null);

        orderService.changeStatusByAdmin(ORDER_ID, OrderStatus.DELIVERED, ADMIN_ID, "Giao thành công");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.isStockDeducted()).isTrue();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Hoàn trả đơn đã giao: cộng lại kho, trả lượt voucher, payment đã PAID ⇒ REFUNDED")
    void returnDeliveredOrder_restoresStockAndRefunds() {
        Payment payment = payment(PaymentMethod.COD, PaymentStatus.PAID);
        Order order = order(OrderStatus.DELIVERED, true, payment, item(11, 2));
        order.setVoucher(voucher(9));
        stubLookup(order, USER_ID);
        when(productVariantRepository.restoreStock(11, 2)).thenReturn(1);

        orderService.returnByCustomer(USER_ID, ORDER_ID, "Vợt không đúng mô tả như trên web");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.RETURNED);
        assertThat(order.isStockDeducted()).isFalse();
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(productVariantRepository).restoreStock(11, 2);
        verify(voucherService).release(9);
    }

    @Test
    @DisplayName("Chuyển trạng thái sai luật (đã giao ⇒ đã xác nhận) bị chặn")
    void invalidTransition_isRejected() {
        Order order = order(OrderStatus.DELIVERED, true, payment(PaymentMethod.COD, PaymentStatus.PAID), item(11, 1));
        stubLookup(order, null);

        assertThatThrownBy(() -> orderService.changeStatusByAdmin(ORDER_ID, OrderStatus.CONFIRMED, ADMIN_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không thể chuyển đơn từ \"Giao thành công\" sang \"Đã xác nhận\"");

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("Khách huỷ: bắt buộc nhập lý do ≥ 10 ký tự, đơn đang giao thì phải gọi hotline")
    void customerCancel_validatesReasonAndStatus() {
        Order confirmed = order(OrderStatus.CONFIRMED, true, payment(PaymentMethod.COD, PaymentStatus.PENDING),
                item(11, 1));
        stubLookup(confirmed, USER_ID);

        assertThatThrownBy(() -> orderService.cancelByCustomer(USER_ID, ORDER_ID, "abc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ít nhất 10 ký tự");
        assertThat(confirmed.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);

        Order shipping = order(OrderStatus.SHIPPING, true, payment(PaymentMethod.COD, PaymentStatus.PENDING),
                item(11, 1));
        when(orderRepository.findDetailForUser(ORDER_ID, USER_ID)).thenReturn(Optional.of(shipping));

        assertThatThrownBy(() -> orderService.cancelByCustomer(USER_ID, ORDER_ID, "Muốn huỷ đơn đang giao"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang trên đường giao");
        assertThat(shipping.getOrderStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("Xác nhận thanh toán: ghi mã giao dịch, và chặn xác nhận lần hai / đơn đã kết thúc")
    void confirmPayment_validatesState() {
        Payment payment = payment(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING);
        Order order = order(OrderStatus.CONFIRMED, true, payment, item(11, 1));
        stubLookup(order, null);

        orderService.confirmPayment(ORDER_ID, "FT20261016", ADMIN_ID);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getTransactionCode()).isEqualTo("FT20261016");
        verify(paymentRepository).save(payment);

        assertThatThrownBy(() -> orderService.confirmPayment(ORDER_ID, "FT999999", ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xác nhận thanh toán");

        Order cancelled = order(OrderStatus.CANCELLED, false, payment(PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING),
                item(11, 1));
        when(orderRepository.findDetailForAdmin(ORDER_ID)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> orderService.confirmPayment(ORDER_ID, "FT111", ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã huỷ hoặc đã hoàn trả");
    }

    @Test
    @DisplayName("Không xem được đơn của người khác ⇒ 404")
    void myOrderDetail_throwsWhenNotOwner() {
        when(orderRepository.findDetailForUser(ORDER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.myOrderDetail(USER_ID, ORDER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Không tìm thấy đơn hàng");
    }

    // ==================== TẠO ĐƠN THỦ CÔNG (ADMIN) ====================

    @Test
    @DisplayName("Đơn thủ công khách vãng lai, tiền mặt tại quầy: MANUAL + PAID + xác nhận ngay ⇒ trừ kho")
    void createManualOrder_walkInCash_confirmsAndDeductsStock() {
        ManualOrderForm form = manualForm("CASH", true, line("YX-AX100ZZ-01", 2));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variantWithStock(11, 5)));
        when(orderCodeGenerator.next()).thenReturn("BS260101000000999");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productVariantRepository.deductStock(11, 2)).thenReturn(1);

        Order created = orderService.createManualOrder(form, ADMIN_ID);

        assertThat(created.getOrderSource()).isEqualTo(OrderSource.MANUAL);
        assertThat(created.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(created.isStockDeducted()).isTrue();
        assertThat(created.getUser()).isNull();                 // khách vãng lai
        assertThat(created.getCreatedBy()).isNotNull();         // có admin tạo
        assertThat(created.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(created.getShippingFee()).isEqualByComparingTo("0");
        assertThat(created.getTotalAmount()).isEqualByComparingTo("2980000");
        assertThat(created.getPayment().getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(created.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(created.getPayment().getPaidAt()).isNotNull();

        verify(productVariantRepository).deductStock(11, 2);
        // 2 dòng lịch sử: tạo đơn (NULL → PENDING) và xác nhận ngay (PENDING → CONFIRMED)
        verify(historyRepository, times(2)).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Đơn thủ công COD chưa xác nhận: ở PENDING, payment PENDING, KHÔNG đụng kho")
    void createManualOrder_pendingCod_doesNotTouchStock() {
        ManualOrderForm form = manualForm("COD", false, line("YX-AX100ZZ-01", 1));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variantWithStock(11, 5)));
        when(orderCodeGenerator.next()).thenReturn("BS260101000000888");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createManualOrder(form, ADMIN_ID);

        assertThat(created.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(created.isStockDeducted()).isFalse();
        assertThat(created.getPayment().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(productVariantRepository, never()).deductStock(any(), anyInt());
        verify(historyRepository).save(any(OrderStatusHistory.class));
    }

    @Test
    @DisplayName("Đơn thủ công: nhập trùng SKU thì cộng dồn thành 1 dòng hàng")
    void createManualOrder_mergesDuplicateSkuLines() {
        ManualOrderForm form = manualForm("CASH", false,
                line("YX-AX100ZZ-01", 1), line("yx-ax100zz-01 ", 2), line("   ", 5));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        // CSDL thật dùng collation không phân biệt hoa/thường; mock phải khai báo cả hai cách viết.
        ProductVariant variant = variantWithStock(11, 5);
        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variant));
        when(productVariantRepository.findBySku("yx-ax100zz-01")).thenReturn(Optional.of(variant));
        when(orderCodeGenerator.next()).thenReturn("BS260101000000666");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createManualOrder(form, ADMIN_ID);

        assertThat(created.getItems()).hasSize(1);
        assertThat(created.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(created.getSubtotal()).isEqualByComparingTo("4470000");
    }

    @Test
    @DisplayName("Đơn thủ công: SKU lạ hoặc vượt tồn kho bị chặn trước khi ghi DB")
    void createManualOrder_rejectsUnknownSkuAndMissingStock() {
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        when(productVariantRepository.findBySku("KHONG-CO-SKU")).thenReturn(Optional.empty());

        ManualOrderForm unknownSku = manualForm("CASH", true, line("KHONG-CO-SKU", 1));
        assertThatThrownBy(() -> orderService.createManualOrder(unknownSku, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không tìm thấy SKU");

        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variantWithStock(11, 5)));
        ManualOrderForm tooMany = manualForm("CASH", true, line("YX-AX100ZZ-01", 6));
        assertThatThrownBy(() -> orderService.createManualOrder(tooMany, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ còn 5 sản phẩm");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đơn thủ công theo tài khoản khách: tự điền người nhận + địa chỉ mặc định")
    void createManualOrder_fillsReceiverFromCustomerAccount() {
        ManualOrderForm form = manualForm("BANK_TRANSFER", false, line("YX-AX100ZZ-01", 1));
        form.setCustomerEmail("khach@example.com");
        form.setReceiverName(null);
        form.setReceiverPhone(null);
        form.setShippingAddress(null);

        User customer = customer();
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        when(userRepository.findByEmail("khach@example.com")).thenReturn(Optional.of(customer));
        when(addressRepository.findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(USER_ID))
                .thenReturn(Optional.of(address()));
        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variantWithStock(11, 5)));
        when(orderCodeGenerator.next()).thenReturn("BS260101000000777");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order created = orderService.createManualOrder(form, ADMIN_ID);

        assertThat(created.getUser()).isSameAs(customer);
        assertThat(created.getReceiverName()).isEqualTo("Nguyễn Văn Khách");
        assertThat(created.getReceiverPhone()).isEqualTo("0912345678");
        assertThat(created.getShippingAddress()).contains("123 Nguyễn Trãi").contains("Phường Bến Thành");
        assertThat(created.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("Đơn thủ công cho khách chưa có địa chỉ mặc định ⇒ bắt nhập địa chỉ, không ghi DB")
    void createManualOrder_requiresAddressWhenCustomerHasNone() {
        ManualOrderForm form = manualForm("BANK_TRANSFER", false, line("YX-AX100ZZ-01", 1));
        form.setCustomerEmail("khach@example.com");
        form.setReceiverName(null);
        form.setReceiverPhone(null);
        form.setShippingAddress(null);

        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin());
        when(userRepository.findByEmail("khach@example.com")).thenReturn(Optional.of(customer()));
        when(addressRepository.findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(USER_ID))
                .thenReturn(Optional.empty());
        when(productVariantRepository.findBySku("YX-AX100ZZ-01")).thenReturn(Optional.of(variantWithStock(11, 5)));

        assertThatThrownBy(() -> orderService.createManualOrder(form, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có địa chỉ mặc định");

        verify(orderRepository, never()).save(any());
    }

    // ==================== dữ liệu giả ====================

    /** userId = null ⇒ luồng admin (xem mọi đơn); ngược lại ⇒ đúng chủ đơn mới xem được. */
    private void stubLookup(Order order, Integer userId) {
        if (userId == null) {
            when(orderRepository.findDetailForAdmin(ORDER_ID)).thenReturn(Optional.of(order));
        } else {
            when(orderRepository.findDetailForUser(ORDER_ID, userId)).thenReturn(Optional.of(order));
        }
    }

    private Order order(OrderStatus status, boolean stockDeducted, Payment payment, OrderItem... items) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("BS260101000000123");
        order.setOrderStatus(status);
        order.setStockDeducted(stockDeducted);
        for (OrderItem item : items) {
            order.addItem(item);
        }
        order.setPayment(payment);
        if (payment != null) {
            payment.setOrder(order);
        }
        return order;
    }

    private OrderItem item(int variantId, int quantity) {
        Product product = new Product();
        product.setId(1);
        product.setName("Vợt cầu lông Yonex Astrox 100 ZZ");

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSku("YX-AX100ZZ-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));

        OrderItem item = OrderItem.from(variant, quantity);
        item.setId(variantId * 100);
        return item;
    }

    private Payment payment(PaymentMethod method, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(1);
        payment.setPaymentMethod(method);
        payment.setPaymentStatus(status);
        payment.setAmount(new BigDecimal("1490000"));
        return payment;
    }

    private Voucher voucher(int id) {
        Voucher voucher = new Voucher();
        voucher.setId(id);
        voucher.setCode("GIAM10");
        voucher.setName("Giảm 10%");
        voucher.setQuantity(10);
        return voucher;
    }

    private ProductVariant variantWithStock(int id, int stock) {
        Product product = new Product();
        product.setId(1);
        product.setName("Vợt cầu lông Yonex Astrox 100 ZZ");

        ProductVariant variant = new ProductVariant();
        variant.setId(id);
        variant.setProduct(product);
        variant.setSku("YX-AX100ZZ-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal("1490000"));
        variant.setStockQuantity(stock);
        variant.setStatus(VariantStatus.ACTIVE);
        return variant;
    }

    private ManualOrderForm manualForm(String paymentMethod, boolean confirmNow, ManualOrderLineForm... lines) {
        ManualOrderForm form = new ManualOrderForm();
        form.setPaymentMethod(paymentMethod);
        form.setConfirmNow(confirmNow);
        form.setReceiverName("Khách lẻ tại quầy");
        form.setReceiverPhone("0900000000");
        form.setShippingAddress("Mua trực tiếp tại cửa hàng BadStore");
        form.setLines(new ArrayList<>(List.of(lines)));
        return form;
    }

    private ManualOrderLineForm line(String sku, int quantity) {
        ManualOrderLineForm line = new ManualOrderLineForm();
        line.setSku(sku);
        line.setQuantity(quantity);
        return line;
    }

    private User admin() {
        User admin = new User();
        admin.setId(ADMIN_ID);
        admin.setEmail("admin@badstore.local");
        admin.setFullName("Quản trị viên");
        return admin;
    }

    private User customer() {
        User customer = new User();
        customer.setId(USER_ID);
        customer.setEmail("khach@example.com");
        customer.setFullName("Nguyễn Văn Khách");
        customer.setPhone("0912345678");
        return customer;
    }

    private UserAddress address() {
        UserAddress address = new UserAddress();
        address.setId(50);
        address.setReceiverName("Nguyễn Văn Khách");
        address.setReceiverPhone("0912345678");
        address.setProvinceName("TP. Hồ Chí Minh");
        address.setDistrictName("Quận 1");
        address.setWardName("Phường Bến Thành");
        address.setAddressDetail("123 Nguyễn Trãi");
        return address;
    }
}
