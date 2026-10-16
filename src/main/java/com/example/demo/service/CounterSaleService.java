package com.example.demo.service;

import com.example.demo.config.ShopProperties;
import com.example.demo.dto.admin.CounterSaleForm;
import com.example.demo.dto.admin.CounterSaleResult;
import com.example.demo.dto.admin.CustomerLookupView;
import com.example.demo.dto.admin.ManualOrderForm;
import com.example.demo.dto.admin.PosPriceRequest;
import com.example.demo.dto.admin.PosPriceResult;
import com.example.demo.dto.admin.PosProductView;
import com.example.demo.entity.Order;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.util.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Nghiệp vụ BÁN HÀNG TẠI QUẦY (POS).
 *
 * Điểm quan trọng: quầy KHÔNG tự tính tiền riêng mà tái sử dụng nguyên luồng tạo đơn thủ công
 * của {@link OrderService#createManualOrder} – nhờ vậy đơn bán tại quầy vẫn:
 *   – là đơn MANUAL do admin tạo, khách có thể là khách vãng lai (user_id = NULL);
 *   – xác nhận ngay ⇒ TRỪ KHO bằng câu UPDATE có điều kiện (không thể bán quá tồn);
 *   – giảm giá bằng voucher (nếu quầy nhập) và ghi đủ lịch sử trạng thái;
 *   – thanh toán tiền mặt ⇒ đánh dấu PAID ngay, chuyển khoản ⇒ admin tích "đã nhận tiền".
 *
 * Riêng phần quầy: PHÍ GIAO HÀNG = 0 (khách xách hàng về), tiền khách đưa/tiền thừa tính ở server,
 * và khách quen được tra theo số điện thoại để gắn hoá đơn vào tài khoản của họ.
 */
@Service
@RequiredArgsConstructor
public class CounterSaleService {

    /** Số kết quả tối đa của ô tìm kiếm sản phẩm ở quầy. */
    public static final int SEARCH_LIMIT = 20;

    /** Khách không để lại tên/SĐT vẫn phải có thông tin trên hoá đơn (đơn hàng bắt buộc có người nhận). */
    public static final String WALK_IN_NAME = "Khách lẻ tại quầy";

    public static final String WALK_IN_PHONE = "0000000000";

    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final VoucherService voucherService;
    private final ShopProperties shopProperties;

    /** Tìm biến thể đang bán theo SKU/tên sản phẩm (màn POS gọi liên tục nên chỉ đọc, không ghi). */
    @Transactional(readOnly = true)
    public List<PosProductView> searchForPos(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        List<ProductVariant> variants = productVariantRepository
                .searchActiveForSale(normalized, PageRequest.of(0, SEARCH_LIMIT));
        return variants.stream().map(PosProductView::of).toList();
    }

    /** Tra khách quen theo SĐT để quầy xác nhận đúng người trước khi gắn hoá đơn. */
    @Transactional(readOnly = true)
    public CustomerLookupView lookupCustomer(String phone) {
        String normalized = trimToNull(phone);
        if (normalized == null) {
            return CustomerLookupView.notFound();
        }
        return userRepository.findFirstByPhoneOrderByIdAsc(normalized)
                .map(user -> new CustomerLookupView(true, user.getFullName(), user.getEmail(), user.getPhone()))
                .orElseGet(CustomerLookupView::notFound);
    }

    /**
     * Tính tiền TẠM THỜI cho hoá đơn quầy đang lập: tra giá từng SKU + áp voucher (nếu quầy nhập).
     *
     * Không ghi gì vào CSDL. Dòng nào sai SKU / ngừng bán / vượt tồn kho thì trả về `error` riêng
     * để quầy thấy ngay trước khi bấm thu tiền. Khi lưu hoá đơn, OrderService vẫn tính lại lần nữa.
     */
    @Transactional(readOnly = true)
    public PosPriceResult priceCart(PosPriceRequest request) {
        List<PosPriceResult.LineView> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        if (request != null && request.lines() != null) {
            for (PosPriceRequest.Line line : request.lines()) {
                if (line == null || !StringUtils.hasText(line.sku())) {
                    continue;
                }
                String sku = line.sku().trim();
                int quantity = Math.max(1, line.quantity());
                Optional<ProductVariant> found = productVariantRepository.findBySkuWithProduct(sku);
                if (found.isEmpty()) {
                    lines.add(new PosPriceResult.LineView(sku, quantity, null, null, null, null, 0, null,
                            "Không tìm thấy SKU \"" + sku + "\" trong hệ thống."));
                    continue;
                }
                ProductVariant variant = found.get();
                BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(quantity));
                String error = null;
                if (variant.getStatus() != VariantStatus.ACTIVE) {
                    error = "Phiên bản này đang ngừng bán.";
                } else if (variant.getStockQuantity() < quantity) {
                    error = "Trong kho chỉ còn " + variant.getStockQuantity() + " sản phẩm.";
                }
                if (error == null) {
                    subtotal = subtotal.add(lineTotal);
                }
                lines.add(new PosPriceResult.LineView(sku, quantity, variant.getProduct().getName(),
                        variant.getColor() + " / " + variant.getWeightClass() + " / " + variant.getGripSize(),
                        variant.getPrice(), lineTotal, variant.getStockQuantity(), variant.getImageUrl(), error));
            }
        }

        BigDecimal discount = BigDecimal.ZERO;
        String voucherCode = null;
        String voucherDescription = null;
        String voucherError = null;
        String rawCode = request == null ? null : voucherService.normalizeCode(request.voucherCode());
        if (rawCode != null) {
            if (subtotal.signum() <= 0) {
                voucherError = "Chưa có sản phẩm nào trong hoá đơn để áp mã giảm giá.";
            } else {
                try {
                    Voucher voucher = voucherService.requireUsable(rawCode, subtotal);
                    discount = voucherService.discountOf(voucher, subtotal);
                    voucherCode = voucher.getCode();
                    voucherDescription = voucherService.describe(voucher);
                } catch (BusinessException ex) {
                    voucherError = ex.getMessage();
                }
            }
        }
        return new PosPriceResult(subtotal, discount, subtotal.subtract(discount),
                voucherCode, voucherDescription, voucherError, lines);
    }

    /**
     * Bán hàng tại quầy: tạo đơn MANUAL, xác nhận + trừ kho ngay, thu tiền và trả về hoá đơn.
     * Ném {@link BusinessException} nếu thiếu tồn kho, SKU sai, voucher không dùng được…
     * (transaction rollback nên không để lại đơn rác).
     */
    @Transactional
    public CounterSaleResult sell(CounterSaleForm form, Integer adminUserId) {
        PaymentMethod paymentMethod = parsePaymentMethod(form.getPaymentMethod());
        User customer = findCustomer(form);

        ManualOrderForm manual = new ManualOrderForm();
        manual.setLines(form.getLines());
        manual.setCustomerEmail(customer == null ? null : customer.getEmail());
        manual.setReceiverName(resolveReceiverName(form, customer));
        manual.setReceiverPhone(resolveReceiverPhone(form, customer));
        manual.setShippingAddress(shopProperties.pickupAddress());
        manual.setPaymentMethod(paymentMethod.name());
        manual.setShippingFee(BigDecimal.ZERO);
        manual.setVoucherCode(form.getVoucherCode());
        manual.setConfirmNow(true);
        manual.setNote(buildNote(form, customer));

        Order order = orderService.createManualOrder(manual, adminUserId);

        BigDecimal received = null;
        BigDecimal change = null;
        if (paymentMethod == PaymentMethod.CASH) {
            received = normalizedCash(form.getCashReceived(), order.getTotalAmount());
            if (received.compareTo(order.getTotalAmount()) < 0) {
                BigDecimal missing = order.getTotalAmount().subtract(received);
                // Ném lỗi ở đây ⇒ cả transaction rollback, quầy thu thêm rồi bấm lại (không tạo đơn rác).
                throw new BusinessException("Tiền khách đưa (" + Money.format(received)
                        + ") chưa đủ hoá đơn " + Money.format(order.getTotalAmount())
                        + ". Cần thu thêm " + Money.format(missing) + ".");
            }
            change = received.subtract(order.getTotalAmount());
            order.setNote(appendNote(order.getNote(),
                    "Khách đưa " + Money.format(received) + ", thừa " + Money.format(change)));
        } else if (form.isTransferConfirmed()) {
            // Khách quét mã chuyển khoản thành công tại quầy ⇒ xác nhận thanh toán luôn.
            orderService.confirmPayment(order.getId(), null, adminUserId);
        }
        return new CounterSaleResult(order, received, change);
    }

    // ------------------------------------------------------------------ nội bộ

    /** Tiền khách đưa: bỏ trống coi như đưa đúng số tiền của hoá đơn. */
    private BigDecimal normalizedCash(BigDecimal cashReceived, BigDecimal totalAmount) {
        if (cashReceived == null || cashReceived.signum() <= 0) {
            return totalAmount;
        }
        return cashReceived.setScale(0, RoundingMode.HALF_UP);
    }

    /** Khách quen: ưu tiên SĐT, sau đó tới email đã tra ở ô tìm khách. */
    private User findCustomer(CounterSaleForm form) {
        String phone = firstNonBlank(form.getCustomerPhone(), form.getReceiverPhone());
        if (phone != null) {
            Optional<User> byPhone = userRepository.findFirstByPhoneOrderByIdAsc(phone);
            if (byPhone.isPresent()) {
                return byPhone.get();
            }
        }
        String email = trimToNull(form.getCustomerEmail());
        if (email == null) {
            return null;
        }
        return userRepository.findByEmail(email).orElse(null);
    }

    private String resolveReceiverName(CounterSaleForm form, User customer) {
        String name = trimToNull(form.getReceiverName());
        if (name != null) {
            return name;
        }
        if (customer != null && StringUtils.hasText(customer.getFullName())) {
            return customer.getFullName();
        }
        return WALK_IN_NAME;
    }

    private String resolveReceiverPhone(CounterSaleForm form, User customer) {
        String phone = firstNonBlank(form.getReceiverPhone(), form.getCustomerPhone());
        if (phone != null) {
            return phone;
        }
        if (customer != null && trimToNull(customer.getPhone()) != null) {
            return customer.getPhone();
        }
        return WALK_IN_PHONE;
    }

    private String buildNote(CounterSaleForm form, User customer) {
        List<String> parts = new ArrayList<>();
        parts.add("Bán hàng tại quầy");
        parts.add(customer == null ? "khách lẻ" : "khách quen " + customer.getEmail());
        String note = trimToNull(form.getNote());
        if (note != null) {
            parts.add(note);
        }
        return String.join(" – ", parts);
    }

    private String appendNote(String currentNote, String extra) {
        return currentNote == null || currentNote.isBlank() ? extra : currentNote + " – " + extra;
    }

    /** Quầy chỉ thu tiền mặt hoặc chuyển khoản (COD là nghiệp vụ giao hàng, không phải bán tại quầy). */
    private PaymentMethod parsePaymentMethod(String rawMethod) {
        String value = trimToNull(rawMethod);
        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(value == null ? "" : value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Vui lòng chọn phương thức thanh toán cho hoá đơn tại quầy.");
        }
        if (method == PaymentMethod.COD) {
            throw new BusinessException("Hoá đơn tại quầy chỉ thanh toán tiền mặt hoặc chuyển khoản.");
        }
        return method;
    }

    private String firstNonBlank(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
