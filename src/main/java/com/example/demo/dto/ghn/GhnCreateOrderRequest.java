package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Thân request tạo vận đơn GHN (POST /v2/shipping-order/create).
 * Tên field giữ đúng snake_case mà GHN yêu cầu.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhnCreateOrderRequest {

    @JsonProperty("payment_type_id")
    private int paymentTypeId = 1;

    @JsonProperty("required_note")
    private String requiredNote = "KHONGCHOXEMHANG";

    @JsonProperty("client_order_code")
    private String clientOrderCode;

    @JsonProperty("to_name")
    private String toName;

    @JsonProperty("to_phone")
    private String toPhone;

    @JsonProperty("to_address")
    private String toAddress;

    @JsonProperty("to_ward_code")
    private String toWardCode;

    @JsonProperty("to_district_id")
    private Integer toDistrictId;

    @JsonProperty("cod_amount")
    private long codAmount;

    private String content = "Vợt cầu lông";

    private int weight = 1500;

    private int length = 60;

    private int width = 20;

    private int height = 12;

    @JsonProperty("insurance_value")
    private long insuranceValue = 500000;

    @JsonProperty("service_type_id")
    private int serviceTypeId = 2;

    private String note;

    private List<GhnOrderItem> items = new ArrayList<>();

    /** Một dòng hàng khai báo với GHN. */
    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GhnOrderItem {

        private String name;

        private String code;

        private int quantity = 1;

        private long price;

        private int weight = 1500;

        public GhnOrderItem(String name, String code, int quantity, long price, int weight) {
            this.name = name;
            this.code = code;
            this.quantity = quantity;
            this.price = price;
            this.weight = weight;
        }
    }
}
