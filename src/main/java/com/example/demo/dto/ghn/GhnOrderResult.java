package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Kết quả tạo vận đơn GHN: mã vận đơn + phí + thời gian giao dự kiến. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnOrderResult(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("total_fee") long totalFee,
        @JsonProperty("expected_delivery_time") String expectedDeliveryTime,
        @JsonProperty("sort_code") String sortCode,
        @JsonProperty("trans_type") String transType) {
}
