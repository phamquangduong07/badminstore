package com.example.demo.dto.admin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Form màn hình BÁN HÀNG TẠI QUẦY (POS).
 *
 * Khác form tạo đơn thủ công: nghiệp vụ quầy luôn bán ngay (trừ kho luôn), không phí giao hàng,
 * có tiền khách đưa/tiền thừa, và tra được khách quen theo số điện thoại.
 */
@Getter
@Setter
public class CounterSaleForm {

    /** Các dòng hàng; JS ở màn POS dựng lại danh sách này theo chỉ số lines[i]. */
    private List<ManualOrderLineForm> lines = new ArrayList<>(List.of(new ManualOrderLineForm()));

    /** Số điện thoại khách để tra tài khoản (bỏ trống = khách lẻ). */
    private String customerPhone;

    /** Email khách (POS tra theo SĐT rồi điền vào đây, gửi kèm khi submit). */
    private String customerEmail;

    /** Tên khách hiện trên hoá đơn; bỏ trống ⇒ lấy theo tài khoản hoặc "Khách lẻ". */
    private String receiverName;

    private String receiverPhone;

    /** CASH (tiền mặt tại quầy) | BANK_TRANSFER (khách quét mã chuyển khoản). */
    private String paymentMethod = "CASH";

    /** Tiền khách đưa (chỉ dùng khi thanh toán tiền mặt) – server tính tiền thừa. */
    private BigDecimal cashReceived;

    /** Khách chuyển khoản: admin tích xác nhận đã nhận được tiền ⇒ payment PAID luôn. */
    private boolean transferConfirmed;

    private String voucherCode;

    private String note;

    /** Mã đơn đã cấp cho mã QR chuyển khoản (POS gửi lại); server kiểm tra lại trước khi dùng. */
    private String orderCode;
}
