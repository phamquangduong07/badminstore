package com.example.demo.dto.ghn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Phường/Xã theo GHN (GET /master-data/ward?district_id=...). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GhnWard(
        @JsonProperty("WardCode") String code,
        @JsonProperty("DistrictID") int districtId,
        @JsonProperty("WardName") String name) {
}
