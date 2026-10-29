package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sinh mã đơn duy nhất dạng "BS" + yyMMddHHmmss + 3 chữ số ngẫu nhiên (≤ 30 ký tự, khớp unique index
 * orders.order_code). Dùng chung cho đơn online và đơn admin tạo thủ công để chỉ có một nơi sinh mã.
 */
@Component
@RequiredArgsConstructor
public class OrderCodeGenerator {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final int MAX_ATTEMPTS = 5;

    /** Định dạng mã do {@link #next()} sinh ra: "BS" + 12 số thời gian + 3 số ngẫu nhiên. */
    private static final Pattern CODE_FORMAT = Pattern.compile("BS\\d{15}");

    private final OrderRepository orderRepository;

    public String next() {
        String datePart = LocalDateTime.now().format(STAMP);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = "BS" + datePart + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
            if (orderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        throw new BusinessException("Hệ thống đang bận tạo mã đơn, vui lòng thử lại sau vài giây.");
    }

    /**
     * Dùng lại mã đã cấp trước (POS cấp mã để in QR trước khi tạo đơn) nếu còn đúng định dạng
     * và chưa có đơn nào dùng; ngược lại sinh mã mới. Mã từ trình duyệt không bao giờ được tin ngay.
     */
    public String validatedOrNext(String candidate) {
        if (candidate != null && CODE_FORMAT.matcher(candidate).matches()
                && orderRepository.findByOrderCode(candidate).isEmpty()) {
            return candidate;
        }
        return next();
    }
}
