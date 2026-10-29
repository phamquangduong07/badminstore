package com.example.demo.service;

import com.example.demo.config.PaymentBankProperties;
import com.example.demo.dto.shop.OrderDetailView;
import com.example.demo.dto.shop.PaymentQrView;
import com.example.demo.enums.PaymentMethod;
import com.example.demo.enums.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Dựng mã QR chuyển khoản VietQR cho đơn hàng (online) và hoá đơn quầy (POS).
 *
 * – Ảnh QR lấy từ dịch vụ VietQR (URL chứa mã ngân hàng, số tài khoản, số tiền, nội dung);
 * – số tiền = tổng tiền của đơn do server tính; nội dung chuyển khoản = mã đơn để admin đối chiếu;
 * – chưa cấu hình / cấu hình sai ⇒ trả rỗng, giao diện tự ẩn khối QR chứ không báo lỗi.
 * Không ghi gì vào CSDL: xác nhận đã nhận tiền vẫn do admin bấm (OrderService#confirmPayment).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrPaymentService {

    private static final Pattern BANK_CODE = Pattern.compile("[A-Za-z0-9]{2,12}");
    private static final Pattern ACCOUNT_NUMBER = Pattern.compile("\\d{6,20}");
    private static final Pattern TEMPLATE = Pattern.compile("[A-Za-z0-9_]{1,20}");
    private static final int MAX_CONTENT_LENGTH = 25;

    private final PaymentBankProperties bank;

    /** Đã cấu hình đủ và hợp lệ để hiện QR chưa. */
    public boolean isAvailable() {
        if (!bank.isEnabled()) {
            return false;
        }
        boolean valid = notBlank(bank.getBankCode()) && BANK_CODE.matcher(bank.getBankCode().trim()).matches()
                && notBlank(bank.getAccountNumber()) && ACCOUNT_NUMBER.matcher(bank.getAccountNumber().trim()).matches()
                && notBlank(bank.getTemplate()) && TEMPLATE.matcher(bank.getTemplate().trim()).matches()
                && notBlank(bank.getImageBaseUrl());
        if (!valid) {
            log.warn("Cấu hình app.payment.bank không hợp lệ – tạm ẩn mã QR chuyển khoản.");
        }
        return valid;
    }

    /** QR cho một khoản tiền + nội dung bất kỳ (POS dùng khi đơn chưa được tạo). */
    public Optional<PaymentQrView> build(String orderCode, BigDecimal amount) {
        if (!isAvailable() || amount == null || amount.signum() <= 0 || !notBlank(orderCode)) {
            return Optional.empty();
        }
        String content = sanitizeContent(orderCode);
        if (content.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal vnd = amount.setScale(0, RoundingMode.HALF_UP);
        String url = UriComponentsBuilder.fromUriString(bank.getImageBaseUrl().trim())
                .pathSegment(bank.getBankCode().trim() + "-" + bank.getAccountNumber().trim()
                        + "-" + bank.getTemplate().trim() + ".png")
                .queryParam("amount", vnd.toPlainString())
                .queryParam("addInfo", content)
                .queryParam("accountName", bank.getAccountName() == null ? "" : bank.getAccountName().trim())
                .encode()
                .build()
                .toUriString();
        return Optional.of(new PaymentQrView(url, bank.getBankName(), bank.getAccountNumber().trim(),
                bank.getAccountName(), vnd, content));
    }

    /**
     * QR của một đơn đã tạo: chỉ khi thanh toán là chuyển khoản, đang chờ thanh toán
     * và đơn chưa bị huỷ/hoàn trả.
     */
    public Optional<PaymentQrView> forOrder(OrderDetailView detail) {
        if (detail == null
                || detail.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || detail.getPaymentStatus() != PaymentStatus.PENDING
                || detail.isFinished()) {
            return Optional.empty();
        }
        return build(detail.getOrder().getOrderCode(), detail.getOrder().getTotalAmount());
    }

    /** Nội dung chuyển khoản: chỉ chữ + số (ngân hàng hay cắt/loại ký tự lạ), tối đa 25 ký tự. */
    static String sanitizeContent(String raw) {
        String cleaned = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9]", "");
        return cleaned.length() > MAX_CONTENT_LENGTH ? cleaned.substring(0, MAX_CONTENT_LENGTH) : cleaned;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
