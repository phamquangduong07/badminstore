package com.example.demo.service;

import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnWard;
import java.io.IOException;
import java.io.InputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * TOÀN BỘ dữ liệu địa chỉ GHN (Tỉnh → Quận/Huyện → Phường/Xã) lưu sẵn trong
 * {@code src/main/resources/ghn/*.json}: nhờ vậy app chạy được khi KHÔNG có mạng/token
 * ({@code app.ghn.mock=true}) mà khách vẫn chọn được đầy đủ 63 tỉnh/thành.
 *
 * <p>Dữ liệu được chép nguyên từ API GHN bằng script {@code docs/ghn-fetch-address.mjs}
 * (63 tỉnh/thành, 722 quận/huyện, 11.980 phường/xã). Mã ở đây là mã THẬT của GHN nên khi
 * chuyển sang gọi API thật (mock=false) địa chỉ đã lưu vẫn dùng được để tính phí/tạo vận đơn.
 * Cập nhật dữ liệu: chạy lại script với token GHN rồi chép 3 file JSON vào thư mục này.</p>
 *
 * <p>Sắp xếp theo tên (thứ tự từ điển tiếng Việt) vì danh sách rất dài, sắp xếp giúp khách tra nhanh.</p>
 */
final class GhnOfflineAddressData {

    private static final List<GhnProvince> PROVINCES;
    private static final Map<Integer, List<GhnDistrict>> DISTRICTS_BY_PROVINCE;
    private static final Map<Integer, List<GhnWard>> WARDS_BY_DISTRICT;

    static {
        JsonMapper mapper = JsonMapper.builder().build();
        PROVINCES = sortByName(
                read(mapper, "ghn/provinces.json", new TypeReference<List<GhnProvince>>() {
                }), GhnProvince::name);
        DISTRICTS_BY_PROVINCE = groupBy(
                sortByName(read(mapper, "ghn/districts.json", new TypeReference<List<GhnDistrict>>() {
                }), GhnDistrict::name), GhnDistrict::provinceId);
        WARDS_BY_DISTRICT = groupBy(
                sortByName(read(mapper, "ghn/wards.json", new TypeReference<List<GhnWard>>() {
                }), GhnWard::name), GhnWard::districtId);
    }

    private GhnOfflineAddressData() {
    }

    /** Toàn bộ tỉnh/thành phố (đã sắp xếp theo tên). */
    static List<GhnProvince> provinces() {
        return PROVINCES;
    }

    /** Quận/huyện của tỉnh; tỉnh lạ thì trả danh sách rỗng (nơi gọi tự báo lỗi). */
    static List<GhnDistrict> districts(int provinceId) {
        return DISTRICTS_BY_PROVINCE.getOrDefault(provinceId, List.of());
    }

    /** Phường/xã của quận; vài quận đặc biệt của GHN không có phường nên trả danh sách rỗng. */
    static List<GhnWard> wards(int districtId) {
        return WARDS_BY_DISTRICT.getOrDefault(districtId, List.of());
    }

    private static <T> Map<Integer, List<T>> groupBy(List<T> items, ToIntFunction<T> keyGetter) {
        Map<Integer, List<T>> grouped = new HashMap<>();
        for (T item : items) {
            grouped.computeIfAbsent(keyGetter.applyAsInt(item), key -> new ArrayList<>()).add(item);
        }
        grouped.replaceAll((key, value) -> List.copyOf(value));
        return Map.copyOf(grouped);
    }

    private static <T> List<T> sortByName(List<T> items, Function<T, String> nameGetter) {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("vi-VN"));
        List<T> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(nameGetter, collator::compare));
        return List.copyOf(sorted);
    }

    private static <T> List<T> read(JsonMapper mapper, String resource, TypeReference<List<T>> type) {
        try (InputStream in = GhnOfflineAddressData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu file dữ liệu địa chỉ GHN: " + resource);
            }
            return mapper.readValue(in, type);
        } catch (JacksonException ex) {
            throw new IllegalStateException("File dữ liệu địa chỉ GHN bị sai định dạng: " + resource, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Không đọc được file dữ liệu địa chỉ GHN: " + resource, ex);
        }
    }
}
