package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
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
}
