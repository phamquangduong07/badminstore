package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Phí vận chuyển GHN trả về (POST /v2/shipping-order/fee).
 * Đơn vị VND, làm tròn về đồng. Chỉ `total` là bắt buộc, các khoản phụ để dạng Long
 * vì có môi trường GHN không trả về đủ (tránh lỗi đọc JSON).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnFee(
        @JsonProperty("total") long total,
        @JsonProperty("service_fee") Long serviceFee,
        @JsonProperty("insurance_fee") Long insuranceFee,
        @JsonProperty("pick_station_fee") Long pickStationFee,
        @JsonProperty("coupon_value") Long couponValue,
        @JsonProperty("r2s_fee") Long returnFee) {

    public BigDecimal totalAmount() {
        return BigDecimal.valueOf(total);
    }
}
