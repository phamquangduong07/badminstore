package com.example.demo.dto.shop;

import java.math.BigDecimal;

/**
 * Mọi thứ giao diện cần để hiện khối "Chuyển khoản bằng mã QR": ảnh QR + thông tin tài khoản để khách
 * chuyển tay nếu không quét được. Số tiền và nội dung luôn do server tính, không nhận từ trình duyệt.
 */
public record PaymentQrView(String qrImageUrl,
                            String bankName,
                            String accountNumber,
                            String accountName,
                            BigDecimal amount,
                            String transferContent) {

    // Getter kiểu JavaBean để SpEL/Thymeleaf đọc được như entity khác trong dự án.
    public String getQrImageUrl() { return qrImageUrl; }
    public String getBankName() { return bankName; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }
    public BigDecimal getAmount() { return amount; }
    public String getTransferContent() { return transferContent; }
}
