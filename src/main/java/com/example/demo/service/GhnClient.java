package com.example.demo.service;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.ghn.GhnApiException;
import com.example.demo.dto.ghn.GhnCreateOrderRequest;
import com.example.demo.dto.ghn.GhnDistrict;
import com.example.demo.dto.ghn.GhnFee;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnResponse;
import com.example.demo.dto.ghn.GhnWard;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Khách gọi API GHN (Giao Hàng Nhanh) – phiên bản public API v2.
 *
 * Nguyên tắc an toàn cho demo:
 *  1. Mặc định TẮT: khi chưa có token, mọi chức năng GHN tự ẩn/ngưng, địa chỉ nhập tay như cũ.
 *  2. `app.ghn.mock: true` trả dữ liệu giả để demo offline (xem {@link GhnMockData}).
 *  3. Dữ liệu Tỉnh–Huyện–Xã và phí ship được CACHE trong bộ nhớ (mặc định 10 phút)
 *     để một trang không gọi GHN quá nhiều lần.
 *  4. Mọi lỗi mạng/GHN từ chối đều bọc thành {@link GhnApiException} với thông báo tiếng Việt.
 */
@Service
public class GhnClient {

    private static final Logger log = LoggerFactory.getLogger(GhnClient.class);

    private final GhnProperties props;
    private final RestClient restClient;
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    /**
     * Constructor dùng cho Spring (cần @Autowired vì lớp có 2 constructor):
     * tự dựng RestClient (JDK HttpClient, có timeout theo cấu hình).
     */
    @Autowired
    public GhnClient(GhnProperties props) {
        this(props, buildRestClient(props, RestClient.builder()));
    }

    /** Dùng cho unit test: truyền thẳng RestClient đã gắn MockRestServiceServer. */
    GhnClient(GhnProperties props, RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    private static RestClient buildRestClient(GhnProperties props, RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return builder.requestFactory(factory).baseUrl(props.getBaseUrl()).build();
    }

    /** GHN có được bật trong cấu hình không (UI dùng để ẩn/hiện khối GHN). */
    public boolean isEnabled() {
        return props.isEnabled();
    }

    /** GHN dùng được thật hay đang chạy chế độ mock. */
    public boolean isMock() {
        return props.isMock();
    }

    /** Bật + (mock hoặc đã có token & shopId) = dùng được. */
    public boolean isAvailable() {
        return props.isEnabled() && (props.isMock() || props.isConfigured());
    }

    /** Lấy danh sách Tỉnh/Thành phố. */
    public List<GhnProvince> provinces() {
        ensureAvailable("lấy danh sách tỉnh/thành phố");
        if (props.isMock()) {
            return GhnMockData.provinces();
        }
        return cached("provinces", () -> {
            GhnResponse<List<GhnProvince>> res = restClient.get()
                    .uri("/master-data/province")
                    .header("Token", props.getToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<GhnResponse<List<GhnProvince>>>() {
                    });
            return require(res, "lấy danh sách tỉnh/thành phố");
        });
    }

    /** Lấy danh sách Quận/Huyện theo tỉnh. */
    public List<GhnDistrict> districts(int provinceId) {
        ensureAvailable("lấy danh sách quận/huyện");
        if (props.isMock()) {
            return GhnMockData.districts(provinceId);
        }
        return cached("districts:" + provinceId, () -> {
            GhnResponse<List<GhnDistrict>> res = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/master-data/district")
                            .queryParam("province_id", provinceId)
                            .build())
                    .header("Token", props.getToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<GhnResponse<List<GhnDistrict>>>() {
                    });
            return require(res, "lấy danh sách quận/huyện");
        });
    }

    /** Lấy danh sách Phường/Xã theo quận (thử GET, nếu GHN không nhận thì gọi POST như tài liệu mới). */
    public List<GhnWard> wards(int districtId) {
        ensureAvailable("lấy danh sách phường/xã");
        if (props.isMock()) {
            return GhnMockData.wards(districtId);
        }
        return cached("wards:" + districtId, () -> {
            try {
                GhnResponse<List<GhnWard>> res = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/master-data/ward")
                                .queryParam("district_id", districtId)
                                .build())
                        .header("Token", props.getToken())
                        .retrieve()
                        .body(new ParameterizedTypeReference<GhnResponse<List<GhnWard>>>() {
                        });
                return require(res, "lấy danh sách phường/xã");
            } catch (RestClientException firstTry) {
                log.debug("GHN ward GET lỗi, thử lại bằng POST: {}", firstTry.getMessage());
                GhnResponse<List<GhnWard>> res = restClient.post()
                        .uri("/master-data/ward")
                        .header("Token", props.getToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("district_id", districtId))
                        .retrieve()
                        .body(new ParameterizedTypeReference<GhnResponse<List<GhnWard>>>() {
                        });
                return require(res, "lấy danh sách phường/xã");
            }
        });
    }

    /** Tính phí vận chuyển từ kho shop tới quận của khách. */
    public GhnFee fee(int toDistrictId, String toWardCode, int weightGram) {
        ensureShippingConfig("tính phí vận chuyển");
        if (props.isMock()) {
            int from = props.getFromDistrictId() == null ? 0 : props.getFromDistrictId();
            return GhnMockData.fee(from, toDistrictId, weightGram);
        }
        String key = "fee:" + toDistrictId + ":" + toWardCode + ":" + weightGram;
        return cached(key, () -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("from_district_id", props.getFromDistrictId());
            if (StringUtils.hasText(props.getFromWardCode())) {
                body.put("from_ward_code", props.getFromWardCode());
            }
            body.put("to_district_id", toDistrictId);
            if (StringUtils.hasText(toWardCode)) {
                body.put("to_ward_code", toWardCode);
            }
            body.put("service_type_id", props.getServiceTypeId());
            body.put("weight", weightGram);
            body.put("length", props.getLengthCm());
            body.put("width", props.getWidthCm());
            body.put("height", props.getHeightCm());
            body.put("insurance_value", props.getInsuranceValue());
            GhnResponse<GhnFee> res = restClient.post()
                    .uri("/v2/shipping-order/fee")
                    .header("Token", props.getToken())
                    .header("ShopId", String.valueOf(props.getShopId()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<GhnResponse<GhnFee>>() {
                    });
            return require(res, "tính phí vận chuyển");
        });
    }

    /** Đẩy đơn sang GHN để lấy mã vận đơn. */
    public GhnOrderResult createOrder(GhnCreateOrderRequest request) {
        ensureShippingConfig("tạo vận đơn");
        if (props.isMock()) {
            return GhnMockData.createOrder();
        }
        GhnResponse<GhnOrderResult> res = restClient.post()
                .uri("/v2/shipping-order/create")
                .header("Token", props.getToken())
                .header("ShopId", String.valueOf(props.getShopId()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<GhnResponse<GhnOrderResult>>() {
                });
        return require(res, "tạo vận đơn");
    }

    /** Xem chi tiết/trạng thái hiện tại của vận đơn. */
    public GhnOrderDetail orderDetail(String orderCode) {
        ensureAvailable("xem trạng thái vận đơn");
        if (props.isMock()) {
            return GhnMockData.orderDetail(orderCode, props.getMockStatus());
        }
        GhnResponse<GhnOrderDetail> res = restClient.post()
                .uri("/v2/shipping-order/detail")
                .header("Token", props.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("order_code", orderCode))
                .retrieve()
                .body(new ParameterizedTypeReference<GhnResponse<GhnOrderDetail>>() {
                });
        return require(res, "xem trạng thái vận đơn");
    }

    /** Xoá cache (gọi khi admin muốn nạp lại dữ liệu mới nhất từ GHN). */
    public void clearCache() {
        cache.clear();
    }

    // ------------------------------------------------------------------ nội bộ

    private <T> T require(GhnResponse<T> response, String action) {
        if (response == null) {
            throw new GhnApiException("GHN không trả dữ liệu khi " + action + ".");
        }
        if (!response.success() || response.data() == null) {
            throw new GhnApiException("GHN từ chối yêu cầu " + action + ": "
                    + (response.message() == null ? "không rõ lý do (mã " + response.code() + ")" : response.message()));
        }
        return response.data();
    }

    private <T> T cached(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        CacheEntry<?> entry = cache.get(key);
        if (entry != null && entry.expiresAt() > now && entry.value() != null) {
            @SuppressWarnings("unchecked")
            T value = (T) entry.value();
            return value;
        }
        T value = call(key, loader);
        long ttlMillis = Math.max(1, props.getCacheTtlSeconds()) * 1000L;
        cache.put(key, new CacheEntry<>(value, now + ttlMillis));
        return value;
    }

    private <T> T call(String action, Supplier<T> loader) {
        try {
            return loader.get();
        } catch (GhnApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("GHN lỗi khi {}: {}", action, ex.getMessage());
            throw new GhnApiException("Không gọi được API GHN (" + action
                    + "). Vui lòng kiểm tra kết nối mạng, token và shopId.", ex);
        }
    }

    private void ensureAvailable(String action) {
        if (!props.isEnabled()) {
            throw new GhnApiException("Tính năng GHN đang tắt (app.ghn.enabled = false).");
        }
        if (!props.isMock() && !props.isConfigured()) {
            throw new GhnApiException("Chưa cấu hình GHN để " + action
                    + ": cần GHN_TOKEN và GHN_SHOP_ID (hoặc bật app.ghn.mock để demo offline).");
        }
    }

    private void ensureShippingConfig(String action) {
        ensureAvailable(action);
        if (!props.isMock() && props.getFromDistrictId() == null) {
            throw new GhnApiException("Chưa khai báo kho lấy hàng (app.ghn.from-district-id) nên không thể " + action + ".");
        }
    }

    private record CacheEntry<T>(T value, long expiresAt) {
    }
}
