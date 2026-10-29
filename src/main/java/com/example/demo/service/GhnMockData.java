package com.example.demo.service;

import com.example.demo.dto.ghn.GhnFee;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phí vận chuyển và vận đơn GIẢ để DEMO OFFLINE (bật bằng `app.ghn.mock: true`).
 *
 * Mục đích: thầy/cô xem được luồng tính phí và đồng bộ trạng thái vận đơn mà không cần
 * mạng/token GHN thật. Riêng dữ liệu địa chỉ Tỉnh–Huyện–Xã ở chế độ này là dữ liệu THẬT,
 * đầy đủ 63 tỉnh/thành (xem {@link GhnOfflineAddressData}), nên chọn địa chỉ không bị giới hạn.
 */
final class GhnMockData {

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(100000);

    private GhnMockData() {
    }

    /** Phí giả: nội thành (cùng quận với shop) rẻ hơn, hàng nặng hơn thì cộng thêm. */
    static GhnFee fee(int fromDistrictId, int toDistrictId, int weightGram) {
        boolean sameDistrict = fromDistrictId == toDistrictId;
        long service = sameDistrict ? 19000 : 32000;
        if (weightGram > 1000) {
            service += ((weightGram - 1000) / 500 + 1) * 2500L;
        }
        long insurance = 0;
        return new GhnFee(service + insurance, service, insurance, 0L, 0L, 0L);
    }

    static GhnOrderResult createOrder() {
        int seq = ORDER_SEQ.incrementAndGet();
        return new GhnOrderResult("GHNMOCK" + seq, 34500, "2026-10-18T12:00:00Z", "MOCK" + (seq % 100), "1");
    }

    /** Chi tiết vận đơn giả: trạng thái lấy theo `app.ghn.mock-status` để demo ánh xạ trạng thái. */
    static GhnOrderDetail orderDetail(String orderCode, String mockStatus) {
        return new GhnOrderDetail(orderCode, mockStatus, 34500, "2026-10-18T12:00:00Z",
                "Khách GHN", "0900000000", "Địa chỉ theo GHN", "MOCK01");
    }
}
