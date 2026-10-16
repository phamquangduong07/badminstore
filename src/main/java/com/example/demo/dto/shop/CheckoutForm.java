package com.example.demo.dto.shop;

import com.example.demo.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Dữ liệu form đặt hàng ở bước thanh toán. Giá/tổng tiền KHÔNG nhận từ form – luôn tính lại ở server. */
@Getter
@Setter
public class CheckoutForm {

    @NotNull(message = "Vui lòng chọn địa chỉ nhận hàng.")
    private Integer addressId;

    @NotBlank(message = "Vui lòng chọn phương thức thanh toán.")
    private String paymentMethod = PaymentMethod.COD.name();

    @Size(max = 50, message = "Mã giảm giá tối đa 50 ký tự.")
    private String voucherCode;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
    private String note;
}
