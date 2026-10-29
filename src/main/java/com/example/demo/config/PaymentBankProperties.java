package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tài khoản ngân hàng nhận tiền chuyển khoản (dùng để dựng mã QR VietQR).
 * Cấu hình ở {@code app.payment.bank} trong application.yml. Các giá trị mặc định chỉ là DỮ LIỆU DEMO –
 * khi chạy thật hãy đặt số tài khoản thật bằng biến môi trường hoặc application-local.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.payment.bank")
@Getter
@Setter
public class PaymentBankProperties {

    /** false ⇒ không hiện mã QR (giao diện tự ẩn, đơn chuyển khoản vẫn đặt được). */
    private boolean enabled = true;

    /** Mã/BIN ngân hàng theo VietQR, ví dụ MB, VCB, TCB, ACB, 970422. */
    private String bankCode = "MB";

    /** Tên ngân hàng hiển thị cho khách. */
    private String bankName = "MB Bank (Ngân hàng Quân đội)";

    private String accountNumber = "0123456789";

    /** Tên chủ tài khoản: in hoa, không dấu (đúng như ngân hàng hiển thị). */
    private String accountName = "CONG TY BADSTORE DEMO";

    /** Mẫu ảnh VietQR: compact2 (có logo + thông tin) | compact | qr_only | print. */
    private String template = "compact2";

    /** Địa chỉ dịch vụ tạo ảnh VietQR (công khai, không cần key). */
    private String imageBaseUrl = "https://img.vietqr.io/image";
}
