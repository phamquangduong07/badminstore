package com.example.demo.service;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.ghn.GhnApiException;
import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnWard;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bọc {@link GhnClient} thành API "an toàn cho giao diện": mọi lỗi mạng/GHN đều bị nuốt và
 * trả về kết quả rỗng, nhờ đó trang bán hàng KHÔNG bao giờ trắng trang vì GHN lỗi –
 * khách chỉ thấy dropdown trống và vẫn nhập địa chỉ bằng tay.
 */
@Service
@RequiredArgsConstructor
public class GhnAddressService {

    private static final Logger log = LoggerFactory.getLogger(GhnAddressService.class);

    /** Mỗi cây vợt thêm vào kiện hàng được cộng thêm 300 g khi tính phí GHN. */
    public static final int EXTRA_WEIGHT_PER_ITEM = 300;

    private final GhnClient ghnClient;
    private final GhnProperties ghnProperties;

    public boolean isEnabled() {
        return ghnClient.isEnabled();
    }

    /** Đang dùng API thật hay dữ liệu giả (để hiện nhãn "dữ liệu demo"). */
    public boolean isMock() {
        return ghnClient.isMock();
    }

    public boolean isAvailable() {
        return ghnClient.isAvailable();
    }

    /** Kho lấy hàng đã khai báo (hiện trên giao diện để khách biết hàng đi từ đâu). */
    public String warehouseAddress() {
        return ghnProperties.getFromAddress();
    }

    public List<GhnProvince> provinces() {
        try {
            return ghnClient.isAvailable() ? ghnClient.provinces() : List.of();
        } catch (GhnApiException ex) {
            log.warn("Không lấy được tỉnh/thành GHN: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<GhnDistrict> districts(Integer provinceId) {
        if (provinceId == null) {
            return List.of();
        }
        try {
            return ghnClient.isAvailable() ? ghnClient.districts(provinceId) : List.of();
        } catch (GhnApiException ex) {
            log.warn("Không lấy được quận/huyện GHN: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<GhnWard> wards(Integer districtId) {
        if (districtId == null) {
            return List.of();
        }
        try {
            return ghnClient.isAvailable() ? ghnClient.wards(districtId) : List.of();
        } catch (GhnApiException ex) {
            log.warn("Không lấy được phường/xã GHN: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Khối lượng tính phí theo số sản phẩm: 1 cây ≈ 1,5 kg, mỗi cây thêm 0,3 kg. */
    public int shippingWeight(int totalQuantity) {
        int base = ghnProperties.getWeightGram();
        int extra = Math.max(0, totalQuantity - 1);
        return base + extra * EXTRA_WEIGHT_PER_ITEM;
    }

    /**
     * Phí vận chuyển GHN cho một quận. Trả về {@code null} khi GHN chưa bật/lỗi
     * để nơi gọi tự dùng phí mặc định của shop.
     */
    public BigDecimal feeOrNull(Integer districtId, String wardCode, int weightGram) {
        if (districtId == null || !ghnClient.isAvailable()) {
            return null;
        }
        try {
            long total = ghnClient.fee(districtId, wardCode, weightGram).total();
            return total <= 0 ? null : BigDecimal.valueOf(total);
        } catch (GhnApiException ex) {
            log.warn("Không tính được phí GHN cho quận {}: {}", districtId, ex.getMessage());
            return null;
        }
    }
}
