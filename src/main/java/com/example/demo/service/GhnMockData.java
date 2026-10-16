package com.example.demo.service;

import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnFee;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnWard;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dữ liệu GHN giả để DEMO OFFLINE (bật bằng `app.ghn.mock: true`).
 *
 * Mục đích: thầy/cô xem được luồng chọn Tỉnh–Huyện–Xã, tính phí và đồng bộ trạng thái
 * mà không cần mạng/token GHN thật. Khi có token thì tắt mock để gọi API thật.
 * Id/code ở đây chỉ mang tính minh hoạ, KHÔNG dùng cho môi trường thật.
 */
final class GhnMockData {

    private static final AtomicInteger ORDER_SEQ = new AtomicInteger(100000);

    private GhnMockData() {
    }

    static List<GhnProvince> provinces() {
        return List.of(
                new GhnProvince(201, "Hà Nội"),
                new GhnProvince(202, "TP. Hồ Chí Minh"),
                new GhnProvince(203, "Hải Phòng"),
                new GhnProvince(204, "Đà Nẵng"),
                new GhnProvince(205, "Cần Thơ"));
    }

    static List<GhnDistrict> districts(int provinceId) {
        return switch (provinceId) {
            case 201 -> List.of(
                    new GhnDistrict(1442, 201, "Quận Hoàn Kiếm"),
                    new GhnDistrict(1444, 201, "Quận Đống Đa"),
                    new GhnDistrict(1451, 201, "Quận Cầu Giấy"));
            case 202 -> List.of(
                    new GhnDistrict(1454, 202, "Quận 1"),
                    new GhnDistrict(1456, 202, "Quận 3"),
                    new GhnDistrict(1479, 202, "Quận 7"));
            case 203 -> List.of(
                    new GhnDistrict(1470, 203, "Quận Lê Chân"),
                    new GhnDistrict(1473, 203, "Quận Ngô Quyền"));
            case 204 -> List.of(
                    new GhnDistrict(1458, 204, "Quận Hải Châu"),
                    new GhnDistrict(1461, 204, "Quận Thanh Khê"));
            case 205 -> List.of(
                    new GhnDistrict(1482, 205, "Quận Ninh Kiều"),
                    new GhnDistrict(1485, 205, "Quận Bình Thuỷ"));
            default -> List.of();
        };
    }

    static List<GhnWard> wards(int districtId) {
        return switch (districtId) {
            case 1442 -> List.of(
                    new GhnWard("20308", 1442, "Phường Phúc Tân"),
                    new GhnWard("20311", 1442, "Phường Đồng Xuân"),
                    new GhnWard("20314", 1442, "Phường Hàng Bài"));
            case 1444 -> List.of(
                    new GhnWard("20320", 1444, "Phường Văn Miếu"),
                    new GhnWard("20323", 1444, "Phường Quốc Tử Giám"));
            case 1451 -> List.of(
                    new GhnWard("20326", 1451, "Phường Dịch Vọng"),
                    new GhnWard("20329", 1451, "Phường Mai Dịch"));
            case 1454 -> List.of(
                    new GhnWard("26734", 1454, "Phường Bến Nghé"),
                    new GhnWard("26737", 1454, "Phường Bến Thành"),
                    new GhnWard("26740", 1454, "Phường Cô Giang"));
            case 1456 -> List.of(
                    new GhnWard("26743", 1456, "Phường Võ Thị Sáu"),
                    new GhnWard("26746", 1456, "Phường 7"));
            case 1479 -> List.of(
                    new GhnWard("26752", 1479, "Phường Tân Phú"),
                    new GhnWard("26755", 1479, "Phường Bình Thuận"));
            case 1470 -> List.of(
                    new GhnWard("20500", 1470, "Phường Trại Cau"),
                    new GhnWard("20503", 1470, "Phường Kênh Dương"));
            case 1473 -> List.of(
                    new GhnWard("20506", 1473, "Phường Máy Chai"),
                    new GhnWard("20509", 1473, "Phường Cầu Tre"));
            case 1458 -> List.of(
                    new GhnWard("20300", 1458, "Phường Thạch Thang"),
                    new GhnWard("20303", 1458, "Phường Hải Châu 1"));
            case 1461 -> List.of(
                    new GhnWard("20306", 1461, "Phường Tam Thuận"),
                    new GhnWard("20309", 1461, "Phường Thanh Khê Đông"));
            case 1482 -> List.of(
                    new GhnWard("20600", 1482, "Phường Tân An"),
                    new GhnWard("20603", 1482, "Phường Xuân Khánh"));
            case 1485 -> List.of(
                    new GhnWard("20606", 1485, "Phường Bình Thuỷ"),
                    new GhnWard("20609", 1485, "Phường Long Hoà"));
            default -> List.of();
        };
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
