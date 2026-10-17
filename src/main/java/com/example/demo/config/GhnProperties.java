package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Cấu hình tích hợp API GHN (Giao Hàng Nhanh) – đọc từ nhánh `app.ghn` trong application.yml.
 *
 * Mặc định TẮT (`enabled: false`) để dự án chạy được khi không có mạng/token:
 * khi tắt, địa chỉ nhập tay như cũ và phí ship dùng hằng số 30.000 ₫.
 * Bật lên thì:
 *  – sổ địa chỉ / thanh toán lấy Tỉnh–Huyện–Xã trực tiếp từ GHN;
 *  – phí vận chuyển tính theo API GHN (theo quận + khối lượng);
 *  – admin đẩy đơn sang GHN lấy mã vận đơn và đồng bộ trạng thái giao hàng về hệ thống.
 *
 * Token/ShopId KHÔNG hard-code trong repo: đặt qua biến môi trường GHN_TOKEN / GHN_SHOP_ID.
 */
@ConfigurationProperties(prefix = "app.ghn")
@Getter
@Setter
public class GhnProperties {

    /** Bật/tắt toàn bộ tích hợp GHN. */
    private boolean enabled = false;

    /** Chạy bằng dữ liệu giả (demo offline, thi không cần mạng). */
    private boolean mock = false;

    /** Trạng thái GHN mà chế độ mock trả về khi đồng bộ (để demo ánh xạ trạng thái). */
    private String mockStatus = "delivering";

    /** Địa chỉ API: dev = dev-online-gateway.ghn.vn, thật = online-gateway.ghn.vn. */
    private String baseUrl = "https://dev-online-gateway.ghn.vn/shiip/public-api";

    private String token = "d444f10b-b72d-11f0-83e1-6217795c23da";

    /** ShopId lấy từ tài khoản GHN (bắt buộc khi tạo vận đơn/tính phí). */
    private Integer shopId;

    /** Kho lấy hàng (địa chỉ shop) – GHN bắt buộc khi tính phí và tạo vận đơn. */
    private Integer fromDistrictId;
    private String fromWardCode = "";
    private String fromName = "BadStore";
    private String fromPhone = "0900000000";
    private String fromAddress = "Số 1 Đại Cồ Việt, Bách Khoa, Hà Nội";

    /** Khối lượng gói hàng mặc định (gram) – 1 cây vợt + hộp. */
    private int weightGram = 1500;
    private int lengthCm = 60;
    private int widthCm = 20;
    private int heightCm = 12;

    /** 2 = chuyển phát thương mại điện tử (mặc định của GHN), 5 = hàng nhẹ. */
    private int serviceTypeId = 2;

    /** 1 = shop trả phí vận chuyển, 2 = người nhận trả (dùng cho đơn COD). */
    private int paymentTypeId = 1;

    /** Giá trị khai giá mặc định (VND) để GHN bồi thường khi mất hàng. */
    private long insuranceValue = 500000;

    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    /** Thời gian cache dữ liệu Tỉnh–Huyện–Xã và phí ship (giây) để không gọi API liên tục. */
    private long cacheTtlSeconds = 600;

    /** Tự động đồng bộ trạng thái các đơn đang giao mỗi `syncDelayMs` (mặc định TẮT). */
    private boolean syncEnabled = false;
    private long syncDelayMs = 600000;

    /** Đã đủ điều kiện gọi API thật chưa (bật + có token + có shopId). */
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(token) && shopId != null;
    }
}
