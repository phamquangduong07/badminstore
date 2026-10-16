package com.example.demo.service;

import com.example.demo.dto.shop.AppliedVoucher;
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
import com.example.demo.entity.User;
import com.example.demo.entity.UserAddress;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.OrderSource;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CartItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bước đặt hàng online: xem trước đơn và tạo đơn thật.
 *
 * Nguyên tắc (AGENTS.md mục 4.5):
 * – tất cả tiền (đơn giá, tạm tính, phí ship, giảm giá, tổng) đều tính lại từ DB, KHÔNG nhận từ form;
 * – kiểm tra biến thể + sản phẩm còn bán và còn đủ tồn kho ngay lúc đặt;
 * – trong 1 transaction: orders + order_items (snapshot) + payments + order_status_histories
 *   + tăng lượt voucher (atomic) + xoá cart_items;
 * – đơn mới ở PENDING, stock_deducted = 0 (kho chỉ bị trừ khi admin xác nhận đơn).
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    /** Phí vận chuyển cố định (chưa tích hợp đơn vị vận chuyển). */
    public static final BigDecimal SHIPPING_FEE = new BigDecimal("30000");

    /** Đơn từ mức này trở lên được miễn phí vận chuyển. */
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("5000000");

    private final CartService cartService;
    private final CartItemRepository cartItemRepository;
    private final VoucherService voucherService;
    private final OrderCodeGenerator orderCodeGenerator;
    private final AddressService addressService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final UserRepository userRepository;
    private final GhnAddressService ghnAddressService;

    /**
     * Xem trước đơn hàng cho trang thanh toán. Mã giảm giá sai KHÔNG ném lỗi mà
     * trả về {@code voucherError} để hiển thị ngay trên trang (URL do khách sửa được).
     */
    @Transactional(readOnly = true)
    public CheckoutSummary preview(Integer userId, String rawVoucherCode) {
        CartView cart = cartService.getCart(userId);
        String code = voucherService.normalizeCode(rawVoucherCode);

        AppliedVoucher applied = null;
        String voucherError = null;
        if (code != null && !cart.isEmpty()) {
            try {
                Voucher voucher = voucherService.requireUsable(code, cart.getSubtotal());
                applied = new AppliedVoucher(voucher.getCode(), voucher.getName(), voucherService.describe(voucher),
                        voucherService.discountOf(voucher, cart.getSubtotal()));
            } catch (BusinessException ex) {
                voucherError = ex.getMessage();
            }
        }

        return new CheckoutSummary(cart, shippingFeeFor(cart.getSubtotal()), FREE_SHIPPING_THRESHOLD,
                applied, voucherError);
    }

    /** Tạo đơn hàng online: kiểm tra giỏ + địa chỉ + tồn kho, lưu đơn và xoá giỏ trong 1 transaction. */
    @Transactional
    public Order placeOrder(Integer userId, CheckoutForm form) {
        List<CartItem> cartItems = cartItemRepository.findCartByUserId(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException("Giỏ hàng của bạn đang trống nên chưa thể đặt hàng.");
        }

        UserAddress address = addressService.requireOwned(userId, form.getAddressId());
        PaymentMethod paymentMethod = parsePaymentMethod(form.getPaymentMethod());

        // Kiểm tra TẤT CẢ dòng hàng trước khi ghi bất cứ thứ gì: sản phẩm còn bán + còn đủ tồn kho.
        List<OrderItem> items = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            ProductVariant variant = cartItem.getProductVariant();
            requireSellable(variant, cartItem.getQuantity());
            items.add(OrderItem.from(variant, cartItem.getQuantity()));
        }

        User user = userRepository.getReferenceById(userId);
        Order order = new Order();
        order.setOrderCode(orderCodeGenerator.next());
        order.setOrderSource(OrderSource.ONLINE);
        order.setUser(user);
        order.setAddress(address);
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getReceiverPhone());
        order.setShippingAddress(address.getFullAddress());
        order.setShippingProvinceId(address.getProvinceId());
        order.setShippingDistrictId(address.getDistrictId());
        order.setShippingWardCode(address.getWardCode());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setStockDeducted(false);
        order.setNote(trimToNull(form.getNote()));
        items.forEach(order::addItem);

        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQuantity = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFeeFor(subtotal, address, totalQuantity));

        BigDecimal discount = BigDecimal.ZERO;
        Voucher voucher = null;
        String code = voucherService.normalizeCode(form.getVoucherCode());
        if (code != null) {
            voucher = voucherService.requireUsable(code, subtotal);
            discount = voucherService.discountOf(voucher, subtotal);
        }
        order.setDiscountAmount(discount);
        order.setVoucher(voucher);
        order.setTotalAmount(subtotal.add(order.getShippingFee()).subtract(discount));

        Order saved = orderRepository.save(order);
        if (voucher != null) {
            voucherService.consume(voucher);
        }

        Payment payment = new Payment();
        payment.setOrder(saved);
        payment.setPaymentMethod(paymentMethod);
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment.setAmount(saved.getTotalAmount());
        paymentRepository.save(payment);

        orderStatusHistoryRepository.save(OrderStatusHistory.record(saved, null, OrderStatus.PENDING, user,
                "Khách đặt hàng trên website"));

        cartItemRepository.deleteAll(cartItems);
        return saved;
    }

    /** Phí vận chuyển theo tạm tính: đơn lớn được miễn phí. */
    public static BigDecimal shippingFeeFor(BigDecimal subtotal) {
        return subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0 ? BigDecimal.ZERO : SHIPPING_FEE;
    }

    /**
     * Phí vận chuyển THẬT của đơn:
     * – đơn đủ ngưỡng miễn phí ⇒ 0 ₫;
     * – địa chỉ có mã quận của GHN và GHN đang bật ⇒ phí theo API GHN (dữ liệu đã cache nên không gọi lại nhiều lần);
     * – còn lại (GHN tắt, địa chỉ nhập tay, GHN lỗi) ⇒ phí cố định của shop.
     */
    private BigDecimal shippingFeeFor(BigDecimal subtotal, UserAddress address, int totalQuantity) {
        BigDecimal defaultFee = shippingFeeFor(subtotal);
        if (defaultFee.signum() == 0 || address == null) {
            return defaultFee;
        }
        BigDecimal ghnFee = ghnAddressService.feeOrNull(address.getDistrictId(), address.getWardCode(),
                ghnAddressService.shippingWeight(totalQuantity));
        return ghnFee == null ? defaultFee : ghnFee;
    }

    /** Đơn online chỉ dùng COD hoặc chuyển khoản (CASH dành cho đơn tạo tại quầy). */
    private PaymentMethod parsePaymentMethod(String rawMethod) {
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(rawMethod == null ? "" : rawMethod.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Phương thức thanh toán không hợp lệ. Bạn chọn lại giúp mình nhé.");
        }
        if (method == PaymentMethod.CASH) {
            throw new BusinessException("Đơn đặt online chỉ hỗ trợ COD hoặc chuyển khoản ngân hàng.");
        }
        return method;
    }

    /** Kiểm tra lại lần cuối trước khi ghi đơn: sản phẩm còn bán và còn đủ tồn kho. */
    private void requireSellable(ProductVariant variant, int quantity) {
        Product product = variant.getProduct();
        if (product.getStatus() != ProductStatus.ACTIVE || product.isDeleted()) {
            throw new BusinessException("Sản phẩm \"" + product.getName()
                    + "\" đã ngừng bán. Bạn xoá sản phẩm này khỏi giỏ rồi đặt lại nhé.");
        }
        if (variant.getStatus() != VariantStatus.ACTIVE) {
            throw new BusinessException("Phiên bản " + variant.getColor() + " " + variant.getWeightClass() + " "
                    + variant.getGripSize() + " đã ngừng bán. Bạn xoá khỏi giỏ rồi đặt lại nhé.");
        }
        if (quantity < 1 || quantity > variant.getStockQuantity()) {
            throw new BusinessException("Không đủ tồn kho cho SKU " + variant.getSku() + ": bạn đặt " + quantity
                    + " nhưng hiện chỉ còn " + variant.getStockQuantity()
                    + " sản phẩm. Vui lòng cập nhật lại giỏ hàng.");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Tiện ích cho trang thanh toán: địa chỉ mặc định (nếu có) để tích sẵn. */
    @Transactional(readOnly = true)
    public Optional<UserAddress> defaultAddress(Integer userId) {
        return addressService.defaultAddress(userId);
    }
}
