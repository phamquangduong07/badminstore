package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.admin.GhnSyncResult;
import com.example.demo.dto.ghn.GhnCreateOrderRequest;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Payment;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử nghiệp vụ vận chuyển GHN (Mockito):
 *  – đẩy đơn sang GHN chỉ khi đơn đã xác nhận + có mã Quận/Huyện của GHN;
 *  – GHN báo "đã giao" khi đơn còn CONFIRMED thì phải đi qua SHIPPING (đúng máy trạng thái);
 *  – GHN báo hoàn hàng khi đang giao ⇒ huỷ đơn (cộng lại kho), đã giao rồi ⇒ hoàn trả.
 */
@ExtendWith(MockitoExtension.class)
class GhnShippingServiceTest {

    private static final Integer ORDER_ID = 12;
    private static final Integer ADMIN_ID = 3;

    @Mock
    private GhnClient ghnClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    private GhnProperties ghnProperties;
    private GhnShippingService ghnShippingService;

    @BeforeEach
    void setUp() {
        ghnProperties = new GhnProperties();
        ghnProperties.setEnabled(true);
        ghnProperties.setToken("token-test");
        ghnProperties.setShopId(123456);
        ghnProperties.setFromDistrictId(1442);
        ghnShippingService = new GhnShippingService(ghnClient, ghnProperties, orderRepository, orderService);
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderCode("BS261016000001");
        order.setOrderStatus(status);
        order.setStockDeducted(status.isStockDeducted());
        order.setReceiverName("Nguyễn Văn A");
        order.setReceiverPhone("0987654321");
        order.setShippingAddress("123 Nguyễn Trãi");
        order.setSubtotal(new BigDecimal("1490000"));
        order.setShippingFee(new BigDecimal("30000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("1520000"));
        return order;
    }

    private OrderItem item(Order order) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setSku("SKU-1");
        item.setProductName("Vợt Yonex Astrox 88D");
        item.setColor("Đen");
        item.setWeightClass("4U");
        item.setGripSize("G5");
        item.setUnitPrice(new BigDecimal("1490000"));
        item.setQuantity(1);
        item.setSubtotal(new BigDecimal("1490000"));
        order.getItems().add(item);
        return item;
    }

    private void stubOrder(Order order) {
        when(orderRepository.findDetailForAdmin(ORDER_ID)).thenReturn(Optional.of(order));
    }

    private void stubGhnStatus(Order order, String rawStatus) {
        order.setGhnOrderCode("GHN999888");
        when(ghnClient.orderDetail("GHN999888"))
                .thenReturn(new GhnOrderDetail("GHN999888", rawStatus, 34500, null, null, null, null, null));
    }

    @Test
    @DisplayName("Đường đi trong máy trạng thái: CONFIRMED ⇒ DELIVERED phải qua SHIPPING, không tới được RETURNED")
    void pathTo_followsStateMachine() {
        assertThat(GhnShippingService.pathTo(OrderStatus.CONFIRMED, OrderStatus.DELIVERED))
                .containsExactly(OrderStatus.SHIPPING, OrderStatus.DELIVERED);
        assertThat(GhnShippingService.pathTo(OrderStatus.SHIPPING, OrderStatus.DELIVERED))
                .containsExactly(OrderStatus.DELIVERED);
        assertThat(GhnShippingService.pathTo(OrderStatus.CONFIRMED, OrderStatus.CANCELLED))
                .containsExactly(OrderStatus.CANCELLED);
        assertThat(GhnShippingService.pathTo(OrderStatus.DELIVERED, OrderStatus.RETURNED))
                .containsExactly(OrderStatus.RETURNED);
        // Chưa giao thì không thể nhảy thẳng sang "hoàn trả" trong 1 bước, phải đi cả hành trình:
        assertThat(GhnShippingService.pathTo(OrderStatus.PENDING, OrderStatus.RETURNED))
                .containsExactly(OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.DELIVERED,
                        OrderStatus.RETURNED);
        // Đơn đã kết thúc thì không đi đâu được nữa
        assertThat(GhnShippingService.pathTo(OrderStatus.CANCELLED, OrderStatus.DELIVERED)).isEmpty();
    }

    @Test
    @DisplayName("GHN báo ĐÃ GIAO khi đơn còn CONFIRMED ⇒ lần lượt chuyển SHIPPING rồi DELIVERED")
    void syncStatus_deliveredFromConfirmed_appliesEveryStep() {
        Order order = order(OrderStatus.CONFIRMED);
        stubOrder(order);
        stubGhnStatus(order, "delivered");

        GhnSyncResult result = ghnShippingService.syncStatus(ORDER_ID, ADMIN_ID);

        assertThat(result.changed()).isTrue();
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(result.ghnStatusLabel()).isEqualTo("Đã giao hàng");
        assertThat(result.message()).contains("Giao thành công");
        verify(orderService).changeStatusByAdmin(eq(ORDER_ID), eq(OrderStatus.SHIPPING), eq(ADMIN_ID), anyString());
        verify(orderService).changeStatusByAdmin(eq(ORDER_ID), eq(OrderStatus.DELIVERED), eq(ADMIN_ID), anyString());
    }

    @Test
    @DisplayName("GHN mới ở bước lấy hàng ⇒ không cần đổi trạng thái đơn")
    void syncStatus_pickingMeansNoChange() {
        Order order = order(OrderStatus.CONFIRMED);
        stubOrder(order);
        stubGhnStatus(order, "picking");

        GhnSyncResult result = ghnShippingService.syncStatus(ORDER_ID, ADMIN_ID);

        assertThat(result.changed()).isFalse();
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.message()).contains("không cần đổi");
        verify(orderService, never()).changeStatusByAdmin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GHN báo hoàn hàng khi đơn ĐANG GIAO ⇒ huỷ đơn để cộng lại kho (không nhảy thẳng về RETURNED)")
    void syncStatus_returnWhileShipping_cancelsOrder() {
        Order order = order(OrderStatus.SHIPPING);
        stubOrder(order);
        stubGhnStatus(order, "returned");

        GhnSyncResult result = ghnShippingService.syncStatus(ORDER_ID, ADMIN_ID);

        assertThat(result.changed()).isTrue();
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderService).changeStatusByAdmin(eq(ORDER_ID), eq(OrderStatus.CANCELLED), eq(ADMIN_ID), anyString());
    }

    @Test
    @DisplayName("GHN báo hoàn hàng khi đơn ĐÃ GIAO ⇒ hoàn trả 100% (cộng kho, hoàn tiền)")
    void syncStatus_returnAfterDelivered_returnsOrder() {
        Order order = order(OrderStatus.DELIVERED);
        stubOrder(order);
        stubGhnStatus(order, "returned");

        GhnSyncResult result = ghnShippingService.syncStatus(ORDER_ID, ADMIN_ID);

        assertThat(result.changed()).isTrue();
        assertThat(result.orderStatus()).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    @DisplayName("Job định kỳ (không có admin) dùng changeStatusBySystem để ghi lịch sử là Hệ thống")
    void syncStatus_systemActorUsesSystemChange() {
        Order order = order(OrderStatus.SHIPPING);
        stubOrder(order);
        stubGhnStatus(order, "delivered");

        ghnShippingService.syncStatus(ORDER_ID, null);

        verify(orderService).changeStatusBySystem(eq(ORDER_ID), eq(OrderStatus.DELIVERED), anyString());
        verify(orderService, never()).changeStatusByAdmin(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Đơn chưa có mã vận đơn thì không đồng bộ được")
    void syncStatus_requiresShippingCode() {
        Order order = order(OrderStatus.CONFIRMED);
        stubOrder(order);

        assertThatThrownBy(() -> ghnShippingService.syncStatus(ORDER_ID, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có vận đơn GHN");
    }

    @Test
    @DisplayName("Chỉ đẩy sang GHN khi đơn đã xác nhận (đã trừ kho)")
    void pushOrder_requiresConfirmedStatus() {
        Order order = order(OrderStatus.PENDING);
        stubOrder(order);
        when(ghnClient.isShippingAvailable()).thenReturn(true);

        assertThatThrownBy(() -> ghnShippingService.pushOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xác nhận");
    }

    @Test
    @DisplayName("Chưa khai báo shopId/kho lấy hàng ⇒ báo rõ thiếu cấu hình thay vì gọi GHN")
    void pushOrder_requiresShippingConfiguration() {
        Order order = order(OrderStatus.CONFIRMED);
        stubOrder(order);
        when(ghnClient.isShippingAvailable()).thenReturn(false);

        assertThatThrownBy(() -> ghnShippingService.pushOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chưa cấu hình đủ GHN");
        verify(ghnClient, never()).createOrder(any());
    }

    @Test
    @DisplayName("Đơn thiếu mã Quận/Huyện của GHN (khách nhập địa chỉ tay) thì GHN không nhận")
    void pushOrder_requiresGhnDistrictCodes() {
        Order order = order(OrderStatus.CONFIRMED);
        stubOrder(order);
        when(ghnClient.isShippingAvailable()).thenReturn(true);

        assertThatThrownBy(() -> ghnShippingService.pushOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa có mã Quận/Huyện");
    }

    @Test
    @DisplayName("Đẩy đơn thành công: gửi đúng người nhận/khối lượng/COD và LƯU mã vận đơn vào đơn")
    void pushOrder_savesOrderCode() {
        Order order = order(OrderStatus.CONFIRMED);
        order.setShippingDistrictId(1454);
        order.setShippingWardCode("1A0808");
        item(order);
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setPaymentMethod(PaymentMethod.COD);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(order.getTotalAmount());
        order.setPayment(payment);
        stubOrder(order);
        when(ghnClient.isShippingAvailable()).thenReturn(true);
        when(ghnClient.createOrder(any(GhnCreateOrderRequest.class)))
                .thenReturn(new GhnOrderResult("GHN777666", 34500, null, null, null));

        GhnOrderResult result = ghnShippingService.pushOrder(ORDER_ID);

        assertThat(result.orderCode()).isEqualTo("GHN777666");
        assertThat(order.getGhnOrderCode()).isEqualTo("GHN777666");
        verify(orderRepository).save(order);

        ArgumentCaptor<GhnCreateOrderRequest> captor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnClient).createOrder(captor.capture());
        GhnCreateOrderRequest request = captor.getValue();
        assertThat(request.getClientOrderCode()).isEqualTo("BS261016000001");
        assertThat(request.getToName()).isEqualTo("Nguyễn Văn A");
        assertThat(request.getToDistrictId()).isEqualTo(1454);
        assertThat(request.getToWardCode()).isEqualTo("1A0808");
        // Đơn COD chưa thu tiền ⇒ GHN thu hộ đúng số tiền của đơn
        assertThat(request.getCodAmount()).isEqualTo(1520000L);
        assertThat(request.getPaymentTypeId()).isEqualTo(GhnShippingService.PAYMENT_TYPE_BUYER_PAYS);
        assertThat(request.getWeight()).isEqualTo(1500);
        assertThat(request.getItems()).hasSize(1);
        assertThat(request.getItems().get(0).getCode()).isEqualTo("SKU-1");
    }

    @Test
    @DisplayName("Đơn đã có mã vận đơn thì không đẩy lại (tránh tạo trùng vận đơn)")
    void pushOrder_rejectsDuplicate() {
        Order order = order(OrderStatus.CONFIRMED);
        order.setGhnOrderCode("GHN999888");
        stubOrder(order);
        when(ghnClient.isShippingAvailable()).thenReturn(true);

        assertThatThrownBy(() -> ghnShippingService.pushOrder(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã có vận đơn GHN");
        verify(ghnClient, never()).createOrder(any());
    }

    @Test
    @DisplayName("Khối lượng kiện hàng: 1 cây 1.500g, mỗi cây thêm 300g")
    void weightOf_countsItems() {
        Order order = order(OrderStatus.CONFIRMED);
        item(order);
        OrderItem second = item(order);
        second.setQuantity(2);
        second.setSubtotal(new BigDecimal("2980000"));

        assertThat(ghnShippingService.weightOf(order)).isEqualTo(1500 + 2 * 300);
        assertThat(ghnProperties.getWeightGram()).isEqualTo(1500);
    }
}
