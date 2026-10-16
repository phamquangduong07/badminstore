package com.example.demo.controller.shop;

import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnWard;
import com.example.demo.service.GhnAddressService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API nội bộ cho giao diện lấy dữ liệu địa chỉ GHN (Tỉnh → Huyện → Xã) và phí vận chuyển tạm tính.
 *
 * Vì dữ liệu Tỉnh–Huyện–Xã của GHN được lấy ở SERVER (có token, có cache) rồi mới trả cho trình duyệt,
 * token GHN không bao giờ lộ ra ngoài. GHN tắt/lỗi thì các API này trả `enabled: false` + danh sách rỗng
 * để giao diện tự chuyển sang nhập địa chỉ bằng tay.
 */
@RestController
@RequestMapping("/api/ghn")
@RequiredArgsConstructor
public class AddressApiController {

    private final GhnAddressService ghnAddressService;

    @GetMapping("/tinh")
    public Map<String, Object> provinces() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (GhnProvince province : ghnAddressService.provinces()) {
            items.add(option(province.id(), province.name()));
        }
        return payload(items);
    }

    @GetMapping("/huyen")
    public Map<String, Object> districts(@RequestParam(name = "provinceId", required = false) Integer provinceId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (GhnDistrict district : ghnAddressService.districts(provinceId)) {
            items.add(option(district.id(), district.name()));
        }
        return payload(items);
    }

    @GetMapping("/xa")
    public Map<String, Object> wards(@RequestParam(name = "districtId", required = false) Integer districtId) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (GhnWard ward : ghnAddressService.wards(districtId)) {
            items.add(option(ward.code(), ward.name()));
        }
        return payload(items);
    }

    /** Phí vận chuyển tạm tính cho khách xem trước khi đặt hàng (server vẫn tính lại khi lưu đơn). */
    @GetMapping("/phi-ship")
    public Map<String, Object> fee(@RequestParam(name = "districtId", required = false) Integer districtId,
                                   @RequestParam(name = "wardCode", required = false) String wardCode,
                                   @RequestParam(name = "quantity", defaultValue = "1") int quantity,
                                   @RequestParam(name = "weight", required = false) Integer weight) {
        int weightGram = weight == null ? ghnAddressService.shippingWeight(quantity) : weight;
        BigDecimal fee = ghnAddressService.feeOrNull(districtId, wardCode, weightGram);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", ghnAddressService.isAvailable());
        result.put("mock", ghnAddressService.isMock());
        result.put("fee", fee);
        result.put("weightGram", weightGram);
        result.put("warehouse", ghnAddressService.warehouseAddress());
        return result;
    }

    private Map<String, Object> option(Object id, String name) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", id);
        option.put("name", name);
        return option;
    }

    private Map<String, Object> payload(List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", ghnAddressService.isAvailable());
        result.put("mock", ghnAddressService.isMock());
        result.put("items", items);
        return result;
    }
}
