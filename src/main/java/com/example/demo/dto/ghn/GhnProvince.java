package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Tỉnh/Thành phố theo GHN (GET /master-data/province). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnProvince(
        @JsonProperty("ProvinceID") int id,
        @JsonProperty("ProvinceName") String name) {
}
