package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.shop.CartLine;
import com.example.demo.dto.shop.CartView;
import com.example.demo.dto.shop.CheckoutForm;
import com.example.demo.dto.shop.CheckoutSummary;
import com.example.demo.entity.CartItem;
import com.example.demo.entity.Order;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.OrderStatusHistory;
import com.example.demo.entity.Payment;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.UserAddress;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.DiscountType;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.enums.VoucherStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử nghiệp vụ ĐẶT HÀNG (Mockito): tiền phải tính ở server, đơn mới ở PENDING chưa trừ kho,
 * voucher trừ lượt atomic, và mọi thứ nằm trong 1 transaction (orders + items + payments + history + xoá giỏ).
 */
@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final Integer USER_ID = 7;
    private static final Integer ADDRESS_ID = 3;

    @Mock
    private CartService cartService;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private AddressService addressService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderStatusHistoryRepository historyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VoucherRepository voucherRepository;

    /** GHN tắt trong unit test ⇒ phí ship dùng mức mặc định của shop. */
    @Mock
    private GhnAddressService ghnAddressService;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        // Dùng VoucherService THẬT để kiểm chứng cách tính giảm giá, chỉ giả VoucherRepository.
        // OrderCodeGenerator cũng dùng thật với orderRepository đã mock (findByOrderCode ⇒ rỗng).
        checkoutService = new CheckoutService(cartService, cartItemRepository, new VoucherService(voucherRepository),
                new OrderCodeGenerator(orderRepository), addressService, orderRepository, paymentRepository,
                historyRepository, userRepository, ghnAddressService);
    }

    @Test
    @DisplayName("Đặt hàng: tạo đơn PENDING chưa trừ kho + item snapshot + payment + history, xoá giỏ")
    void placeOrder_createsPendingOrder() {
        CartItem cartItem = cartItem(50, variant(11, 10, "1490000"), 2);
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem));
        when(addressService.requireOwned(USER_ID, ADDRESS_ID)).thenReturn(address("Nguyễn Văn A", "0987654321"));
        when(orderRepository.findByOrderCode(anyString())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = checkoutService.placeOrder(USER_ID, form("COD", null));

        assertThat(order.getOrderCode()).startsWith("BS");
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getOrderSource()).isEqualTo(OrderSource.ONLINE);
        assertThat(order.isStockDeducted()).isFalse();
        assertThat(order.getItems()).hasSize(1);

        OrderItem item = order.getItems().get(0);
        assertThat(item.getProductName()).isEqualTo("Vợt cầu lông Yonex Astrox 100 ZZ");
        assertThat(item.getSku()).isEqualTo("YX-AX100ZZ-01");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("1490000");
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getSubtotal()).isEqualByComparingTo("2980000");

        // 2.980.000 < 5.000.000 nên còn phí ship 30.000
        assertThat(order.getSubtotal()).isEqualByComparingTo("2980000");
        assertThat(order.getShippingFee()).isEqualByComparingTo("30000");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("3010000");

        // Payment: đúng số tiền của đơn, đang chờ thanh toán
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentCaptor.getValue().getAmount()).isEqualByComparingTo("3010000");

        // Lịch sử: 1 dòng từ null ⇒ PENDING
        ArgumentCaptor<OrderStatusHistory> historyCaptor = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isNull();
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING);

        verify(cartItemRepository).deleteAll(List.of(cartItem));
    }

    @Test
    @DisplayName("Voucher phần trăm bị cắt theo mức giảm tối đa và đơn lớn được miễn phí ship")
    void placeOrder_appliesVoucherWithCapAndFreeShipping() {
        CartItem cartItem = cartItem(50, variant(11, 10, "3000000"), 2);   // 6.000.000
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem));
        when(addressService.requireOwned(USER_ID, ADDRESS_ID)).thenReturn(address("Trần Thị B", "0912345678"));
        when(orderRepository.findByOrderCode(anyString())).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Voucher voucher = voucher(DiscountType.PERCENT, "10", "1000000", "200000", 5);
        when(voucherRepository.findByCode("GIAM10")).thenReturn(Optional.of(voucher));
        when(voucherRepository.increaseUsedQuantity(any(), any(LocalDateTime.class))).thenReturn(1);

        Order order = checkoutService.placeOrder(USER_ID, form("BANK_TRANSFER", "giam10"));

        assertThat(order.getShippingFee()).isEqualByComparingTo("0");            // ≥ 5.000.000 ⇒ miễn phí ship
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("200000");    // 10% = 600.000 ⇒ cắt còn 200.000
        assertThat(order.getTotalAmount()).isEqualByComparingTo("5800000");
        assertThat(order.getVoucher()).isSameAs(voucher);
        verify(voucherRepository).increaseUsedQuantity(any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Voucher đã hết hạn ⇒ chặn đặt hàng, không ghi đơn nào")
    void placeOrder_rejectsExpiredVoucher() {
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem(50, variant(11, 10, "1490000"), 2)));
        when(addressService.requireOwned(USER_ID, ADDRESS_ID)).thenReturn(address("Nguyễn Văn A", "0987654321"));

        Voucher expired = voucher(DiscountType.PERCENT, "10", "0", null, 5);
        expired.setEndAt(LocalDateTime.now().minusDays(1));
        when(voucherRepository.findByCode("HETHAN")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("COD", "hethan")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã hết hạn sử dụng");

        verify(orderRepository, never()).save(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Giỏ hàng trống ⇒ không cho đặt hàng")
    void placeOrder_rejectsEmptyCart() {
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("COD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giỏ hàng của bạn đang trống");
    }

    @Test
    @DisplayName("Đơn online không nhận phương thức tiền mặt tại quầy")
    void placeOrder_rejectsCashPayment() {
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem(50, variant(11, 10, "1490000"), 1)));
        when(addressService.requireOwned(USER_ID, ADDRESS_ID)).thenReturn(address("Nguyễn Văn A", "0987654321"));

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("CASH", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chỉ hỗ trợ COD hoặc chuyển khoản");

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("KHONG_TON_TAI", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không hợp lệ");
    }

    @Test
    @DisplayName("Số lượng vượt tồn kho tại thời điểm đặt ⇒ chặn và báo rõ SKU")
    void placeOrder_rejectsWhenStockNotEnough() {
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem(50, variant(11, 5, "1490000"), 6)));

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("COD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không đủ tồn kho cho SKU YX-AX100ZZ-01")
                .hasMessageContaining("chỉ còn 5 sản phẩm");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sản phẩm/phiên bản ngừng bán tại thời điểm đặt ⇒ chặn đặt hàng")
    void placeOrder_rejectsUnsellableProduct() {
        ProductVariant hidden = variant(11, 10, "1490000");
        hidden.getProduct().setStatus(ProductStatus.HIDDEN);
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem(50, hidden, 1)));

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("COD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã ngừng bán");

        ProductVariant inactive = variant(12, 10, "1490000");
        inactive.setStatus(VariantStatus.INACTIVE);
        when(cartItemRepository.findCartByUserId(USER_ID)).thenReturn(List.of(cartItem(51, inactive, 1)));

        assertThatThrownBy(() -> checkoutService.placeOrder(USER_ID, form("COD", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã ngừng bán");
    }

    @Test
    @DisplayName("Xem trước đơn: mã sai hiện lỗi ngay trên trang chứ không ném ngoại lệ")
    void preview_showsVoucherErrorInsteadOfThrowing() {
        ProductVariant variant = variant(11, 10, "1490000");
        CartLine line = new CartLine(cartItem(50, variant, 2), "/images/no-image.svg", 10);
        when(cartService.getCart(USER_ID)).thenReturn(new CartView(List.of(line)));
        when(voucherRepository.findByCode("HETLUOT")).thenReturn(Optional.of(voucher(DiscountType.FIXED, "50000", "0", null, 0)));

        CheckoutSummary summary = checkoutService.preview(USER_ID, "hetluot");

        assertThat(summary.voucher()).isNull();
        assertThat(summary.voucherError()).contains("đã hết lượt sử dụng");
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("3010000");    // 2.980.000 + 30.000 ship
        assertThat(summary.isFreeShipping()).isFalse();
        assertThat(summary.getAmountToFreeShipping()).isEqualByComparingTo("2020000");
    }

    // ==================== dữ liệu giả ====================

    private CheckoutForm form(String paymentMethod, String voucherCode) {
        CheckoutForm form = new CheckoutForm();
        form.setAddressId(ADDRESS_ID);
        form.setPaymentMethod(paymentMethod);
        form.setVoucherCode(voucherCode);
        return form;
    }

    private ProductVariant variant(int variantId, int stock, String price) {
        Product product = new Product();
        product.setId(1);
        product.setName("Vợt cầu lông Yonex Astrox 100 ZZ");
        product.setProductCode("YX-AX100ZZ");
        product.setStatus(ProductStatus.ACTIVE);

        ProductVariant variant = new ProductVariant();
        variant.setId(variantId);
        variant.setProduct(product);
        variant.setSku("YX-AX100ZZ-01");
        variant.setColor("Xanh đen");
        variant.setWeightClass("4U");
        variant.setGripSize("G5");
        variant.setPrice(new BigDecimal(price));
        variant.setStockQuantity(stock);
        variant.setStatus(VariantStatus.ACTIVE);
        return variant;
    }

    private CartItem cartItem(int id, ProductVariant variant, int quantity) {
        CartItem item = new CartItem();
        item.setId(id);
        item.setProductVariant(variant);
        item.setQuantity(quantity);
        return item;
    }

    private UserAddress address(String receiverName, String phone) {
        UserAddress address = new UserAddress();
        address.setId(ADDRESS_ID);
        address.setReceiverName(receiverName);
        address.setReceiverPhone(phone);
        address.setProvinceName("TP. Hồ Chí Minh");
        address.setDistrictName("Quận 1");
        address.setWardName("Phường Bến Thành");
        address.setAddressDetail("123 Nguyễn Trãi");
        return address;
    }

    private Voucher voucher(DiscountType type, String value, String minOrder, String maxDiscount, int quantity) {
        Voucher voucher = new Voucher();
        voucher.setId(9);
        voucher.setCode("GIAM10");
        voucher.setName("Giảm giá chạy thử");
        voucher.setDiscountType(type);
        voucher.setDiscountValue(new BigDecimal(value));
        voucher.setMinOrderValue(new BigDecimal(minOrder));
        voucher.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        voucher.setQuantity(quantity);
        voucher.setUsedQuantity(0);
        voucher.setStartAt(LocalDateTime.now().minusDays(1));
        voucher.setEndAt(LocalDateTime.now().plusDays(7));
        voucher.setStatus(VoucherStatus.ACTIVE);
        return voucher;
    }
}
