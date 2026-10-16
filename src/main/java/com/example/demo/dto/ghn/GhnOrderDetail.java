package com.example.demo.dto.ghn;

import com.example.demo.enums.GhnStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Chi tiết/trạng thái vận đơn GHN (POST /v2/shipping-order/detail). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnOrderDetail(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("status") String status,
        @JsonProperty("total_fee") long totalFee,
        @JsonProperty("expected_delivery_time") String expectedDeliveryTime,
        @JsonProperty("to_name") String toName,
        @JsonProperty("to_phone") String toPhone,
        @JsonProperty("to_address") String toAddress,
        @JsonProperty("sort_code") String sortCode) {

    /** Trạng thái GHN đã quy về enum của hệ thống. */
    @JsonIgnore
    public GhnStatus statusEnum() {
        return GhnStatus.fromApi(status);
    }

    /** Nhãn tiếng Việt của trạng thái ("Đang giao hàng"...). */
    @JsonIgnore
    public String statusLabel() {
        return statusEnum().getLabel();
    }
}
