package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Quận/Huyện theo GHN (GET /master-data/district?province_id=...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnDistrict(
        @JsonProperty("DistrictID") int id,
        @JsonProperty("ProvinceID") int provinceId,
        @JsonProperty("DistrictName") String name) {
}
