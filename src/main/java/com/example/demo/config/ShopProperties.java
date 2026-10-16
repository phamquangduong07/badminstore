package com.example.demo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Thông tin cửa hàng – dùng cho hoá đơn bán tại quầy, phiếu in và khối liên hệ ở chân trang.
 * Cấu hình trong application.yml ở nhánh {@code app.shop}.
 */
@Component
@ConfigurationProperties(prefix = "app.shop")
@Getter
@Setter
public class ShopProperties {

    private String name = "BadStore";

    private String address = "Số 1 Đại Cồ Việt, Bách Khoa, Hà Nội";

    private String hotline = "1900 0000";

    private String email = "cskh@badstore.local";

    /** Địa chỉ ghi vào đơn bán tại quầy: khách nhận hàng ngay tại cửa hàng. */
    public String pickupAddress() {
        return "Khách nhận hàng tại quầy " + name + " – " + address;
    }
}
