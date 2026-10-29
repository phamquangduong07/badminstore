package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.config.PaymentBankProperties;
import com.example.demo.dto.shop.PaymentQrView;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit test dựng URL VietQR: số tiền, nội dung, mã hoá, và các trường hợp phải ẩn QR. */
class QrPaymentServiceTest {

    private PaymentBankProperties bank;
    private QrPaymentService service;

    @BeforeEach
    void setUp() {
        bank = new PaymentBankProperties();
        bank.setBankCode("MB");
        bank.setBankName("MB Bank");
        bank.setAccountNumber("0123456789");
        bank.setAccountName("CONG TY BADSTORE DEMO");
        bank.setTemplate("compact2");
        service = new QrPaymentService(bank);
    }

    @Test
    @DisplayName("URL QR chứa đúng ngân hàng, số tài khoản, số tiền nguyên VND và nội dung = mã đơn")
    void build_createsVietQrUrl() {
        PaymentQrView view = service.build("BS260101120000123", new BigDecimal("2980000")).orElseThrow();

        assertThat(view.qrImageUrl())
                .startsWith("https://img.vietqr.io/image/MB-0123456789-compact2.png?")
                .contains("amount=2980000")
                .contains("addInfo=BS260101120000123")
                .contains("accountName=CONG%20TY%20BADSTORE%20DEMO");
        assertThat(view.amount()).isEqualByComparingTo("2980000");
        assertThat(view.transferContent()).isEqualTo("BS260101120000123");
        assertThat(view.accountNumber()).isEqualTo("0123456789");
    }

    @Test
    @DisplayName("Số tiền lẻ được làm tròn về VND; nội dung chỉ giữ chữ + số, tối đa 25 ký tự")
    void build_normalizesAmountAndContent() {
        PaymentQrView view = service.build("BS-26 01/01 <script>", new BigDecimal("1000.6")).orElseThrow();

        assertThat(view.amount()).isEqualByComparingTo("1001");
        assertThat(view.transferContent()).isEqualTo("BS260101script");
        assertThat(view.qrImageUrl()).doesNotContain("<").doesNotContain(">");

        assertThat(QrPaymentService.sanitizeContent("A".repeat(40))).hasSize(25);
    }

    @Test
    @DisplayName("Không dựng QR khi số tiền ≤ 0, thiếu mã đơn, tắt cấu hình hoặc cấu hình sai")
    void build_returnsEmptyWhenNotPossible() {
        assertThat(service.build("BS1", BigDecimal.ZERO)).isEmpty();
        assertThat(service.build("BS1", new BigDecimal("-5"))).isEmpty();
        assertThat(service.build("BS1", null)).isEmpty();
        assertThat(service.build("  ", new BigDecimal("1000"))).isEmpty();

        bank.setEnabled(false);
        assertThat(service.isAvailable()).isFalse();
        assertThat(service.build("BS1", new BigDecimal("1000"))).isEmpty();

        bank.setEnabled(true);
        bank.setAccountNumber("abc");                       // số tài khoản phải toàn chữ số
        assertThat(service.build("BS1", new BigDecimal("1000"))).isEqualTo(Optional.empty());

        bank.setAccountNumber("0123456789");
        bank.setBankCode("MB/../evil");                     // mã ngân hàng không được chứa ký tự đường dẫn
        assertThat(service.build("BS1", new BigDecimal("1000"))).isEmpty();
    }
}
