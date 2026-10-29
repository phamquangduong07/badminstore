package com.example.demo.dto.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Form admin tạo đơn thủ công (bán tại quầy / khách đặt qua điện thoại).
 *
 * – customerEmail để trống = khách vãng lai (orders.user_id = NULL, đúng thiết kế CK cho MANUAL);
 * – các ô người nhận để trống sẽ được điền sẵn từ tài khoản khách (nếu có email);
 * – confirmNow = true thì đơn được xác nhận luôn trong cùng transaction (trừ kho ngay).
 */
@Getter
@Setter
public class ManualOrderForm {

    private String customerEmail;

    private String receiverName;

    private String receiverPhone;

    private String shippingAddress;

    /** CASH (thu tại quầy) | COD | BANK_TRANSFER. */
    private String paymentMethod = "CASH";

    private String voucherCode;

    private BigDecimal shippingFee = BigDecimal.ZERO;

    private String note;

    private boolean confirmNow = true;

    /** Mã đơn cấp trước (POS in QR chuyển khoản trước khi tạo đơn); trống/không hợp lệ ⇒ server tự sinh. */
    private String orderCode;

    /** Các dòng hàng; khởi tạo 1 dòng trống để trang form luôn có sẵn ô nhập. */
    private List<ManualOrderLineForm> lines = new ArrayList<>(List.of(new ManualOrderLineForm()));
}
