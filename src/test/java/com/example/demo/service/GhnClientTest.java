package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.ghn.GhnApiException;
import com.example.demo.dto.ghn.GhnCreateOrderRequest;
import com.example.demo.dto.ghn.GhnFee;
import com.example.demo.dto.ghn.GhnOrderDetail;
import com.example.demo.dto.ghn.GhnOrderResult;
import com.example.demo.dto.ghn.GhnProvince;
import com.example.demo.dto.ghn.GhnWard;
import com.example.demo.enums.GhnStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Kiểm thử lớp gọi API GHN: đúng đường dẫn/header/tham số, có cache (không gọi lại liên tục),
 * tự chuyển sang POST khi GHN từ chối GET phường/xã, và mọi lỗi đều thành {@link GhnApiException}
 * với thông báo tiếng Việt để giao diện không bao giờ vỡ.
 *
 * Không gọi mạng thật: dùng MockRestServiceServer gắn vào RestClient.Builder.
 */
class GhnClientTest {

    private GhnProperties properties;
    private MockRestServiceServer server;
    private GhnClient client;

    @BeforeEach
    void setUp() {
        properties = new GhnProperties();
        properties.setEnabled(true);
        properties.setMock(false);
        properties.setToken("token-test");
        properties.setShopId(123456);
        properties.setFromDistrictId(1442);
        properties.setBaseUrl("http://ghn.test");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ghn.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GhnClient(properties, builder.build());
    }

    @Test
    @DisplayName("Lấy tỉnh/thành: đúng endpoint + header Token, và kết quả được CACHE (không gọi lần 2)")
    void provinces_callsApiOnceThenUsesCache() {
        server.expect(requestTo("http://ghn.test/master-data/province"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Token", "token-test"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":[{"ProvinceID":201,"ProvinceName":"Hà Nội"},
                                 {"ProvinceID":202,"ProvinceName":"TP. Hồ Chí Minh"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GhnProvince> first = client.provinces();
        List<GhnProvince> second = client.provinces();

        assertThat(first).hasSize(2);
        assertThat(first.get(0).id()).isEqualTo(201);
        assertThat(first.get(0).name()).isEqualTo("Hà Nội");
        // Lần gọi thứ hai phải lấy từ cache ⇒ mock không nhận thêm request nào.
        assertThat(second).hasSize(2);
        server.verify();
    }

    @Test
    @DisplayName("Lấy phường/xã: GHN từ chối GET thì tự thử lại bằng POST như tài liệu v2")
    void wards_fallsBackToPostWhenGetFails() {
        server.expect(requestTo("http://ghn.test/master-data/ward?district_id=1442"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());
        server.expect(requestTo("http://ghn.test/master-data/ward"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.district_id").value(1442))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":[{"WardCode":"1A0808","DistrictID":1442,"WardName":"Phường Bách Khoa"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GhnWard> wards = client.wards(1442);

        assertThat(wards).hasSize(1);
        assertThat(wards.get(0).code()).isEqualTo("1A0808");
        assertThat(wards.get(0).name()).isEqualTo("Phường Bách Khoa");
        server.verify();
    }

    @Test
    @DisplayName("Tính phí vận chuyển: gửi đúng kho lấy hàng + quận nhận + khối lượng")
    void fee_sendsWarehouseAndWeight() {
        server.expect(requestTo("http://ghn.test/v2/shipping-order/fee"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Token", "token-test"))
                .andExpect(header("ShopId", "123456"))
                .andExpect(jsonPath("$.from_district_id").value(1442))
                .andExpect(jsonPath("$.to_district_id").value(1454))
                .andExpect(jsonPath("$.to_ward_code").value("1A0808"))
                .andExpect(jsonPath("$.weight").value(1800))
                .andExpect(jsonPath("$.service_type_id").value(2))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":{"total":34500,"service_fee":30000,"insurance_fee":4500}}
                        """, MediaType.APPLICATION_JSON));

        GhnFee fee = client.fee(1454, "1A0808", 1800);

        assertThat(fee.total()).isEqualTo(34500);
        assertThat(fee.totalAmount()).isEqualByComparingTo("34500");
        server.verify();
    }

    @Test
    @DisplayName("Tạo vận đơn: gửi nguyên thân request và đọc được mã vận đơn GHN trả về")
    void createOrder_returnsOrderCode() {
        server.expect(requestTo("http://ghn.test/v2/shipping-order/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.client_order_code").value("BS261016000001"))
                .andExpect(jsonPath("$.cod_amount").value(1490000))
                .andExpect(jsonPath("$.payment_type_id").value(2))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":{"order_code":"GHN999888","total_fee":34500}}
                        """, MediaType.APPLICATION_JSON));

        GhnCreateOrderRequest request = new GhnCreateOrderRequest();
        request.setClientOrderCode("BS261016000001");
        request.setToName("Nguyễn Văn A");
        request.setToPhone("0987654321");
        request.setToAddress("123 Nguyễn Trãi");
        request.setToDistrictId(1454);
        request.setToWardCode("1A0808");
        request.setPaymentTypeId(2);
        request.setCodAmount(1490000L);

        GhnOrderResult result = client.createOrder(request);

        assertThat(result.orderCode()).isEqualTo("GHN999888");
        assertThat(result.totalFee()).isEqualTo(34500);
        server.verify();
    }

    @Test
    @DisplayName("Xem vận đơn: mã trạng thái GHN được quy về trạng thái đơn của shop")
    void orderDetail_mapsStatus() {
        server.expect(requestTo("http://ghn.test/v2/shipping-order/detail"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.order_code").value("GHN999888"))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":{"order_code":"GHN999888","status":"delivered","total_fee":34500}}
                        """, MediaType.APPLICATION_JSON));

        GhnOrderDetail detail = client.orderDetail("GHN999888");

        assertThat(detail.orderCode()).isEqualTo("GHN999888");
        assertThat(detail.statusEnum()).isEqualTo(GhnStatus.DELIVERED);
        assertThat(detail.statusLabel()).isEqualTo("Đã giao hàng");
        server.verify();
    }

    @Test
    @DisplayName("GHN từ chối (code khác 200) ⇒ GhnApiException kèm lý do GHN trả về")
    void rejectedRequest_throwsGhnApiExceptionWithReason() {
        server.expect(requestTo("http://ghn.test/master-data/province"))
                .andRespond(withSuccess("""
                        {"code":401,"message":"Token is not valid","data":null}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::provinces)
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("Token is not valid");
    }

    @Test
    @DisplayName("Lỗi mạng/5xx ⇒ GhnApiException thông báo tiếng Việt (giao diện không vỡ)")
    void serverError_throwsGhnApiException() {
        server.expect(requestTo("http://ghn.test/master-data/province"))
                .andRespond(withServerError());

        assertThatThrownBy(client::provinces)
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("Không gọi được API GHN");
    }

    @Test
    @DisplayName("Chưa cấu hình token/shopId ⇒ báo rõ chưa bật GHN thay vì gọi API lỗi")
    void notConfigured_throwsGhnApiException() {
        GhnProperties disabled = new GhnProperties();
        GhnClient disabledClient = new GhnClient(disabled, RestClient.builder().build());

        assertThat(disabledClient.isAddressAvailable()).isFalse();
        assertThat(disabledClient.isShippingAvailable()).isFalse();
        assertThatThrownBy(disabledClient::provinces)
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("đang tắt");
    }

    @Test
    @DisplayName("Chỉ có token (chưa có shopId/kho) vẫn tra cứu được địa chỉ, nhưng không tính phí GHN")
    void tokenOnly_enablesAddressLookupNotShipping() {
        GhnProperties addressOnly = new GhnProperties();
        addressOnly.setEnabled(true);
        addressOnly.setMock(false);
        addressOnly.setToken("token-test");
        addressOnly.setBaseUrl("http://ghn.test");

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ghn.test");
        MockRestServiceServer addressServer = MockRestServiceServer.bindTo(builder).build();
        GhnClient addressClient = new GhnClient(addressOnly, builder.build());

        assertThat(addressClient.isAddressAvailable()).isTrue();
        assertThat(addressClient.isShippingAvailable()).isFalse();

        addressServer.expect(requestTo("http://ghn.test/master-data/province"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":200,"message":"Success",
                         "data":[{"ProvinceID":201,"ProvinceName":"Hà Nội"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(addressClient.provinces()).hasSize(1);
        assertThatThrownBy(() -> addressClient.fee(1454, "1A0808", 1500))
                .isInstanceOf(GhnApiException.class)
                .hasMessageContaining("shopId");
        addressServer.verify();
    }

    @Test
    @DisplayName("Chế độ ngoại tuyến: địa chỉ là dữ liệu GHN lưu sẵn ĐẦY ĐỦ (63 tỉnh), phí/vận đơn mới là số giả")
    void mockMode_servesBuiltInDataWithoutHttp() {
        GhnProperties mockProperties = new GhnProperties();
        mockProperties.setEnabled(true);
        mockProperties.setMock(true);

        GhnClient mockClient = new GhnClient(mockProperties, RestClient.builder().build());

        assertThat(mockClient.isAddressAvailable()).isTrue();
        assertThat(mockClient.isShippingAvailable()).isTrue();
        assertThat(mockClient.isMock()).isTrue();
        assertThat(mockClient.provinces()).isNotEmpty();
        assertThat(mockClient.districts(mockClient.provinces().get(0).id())).isNotEmpty();
        // Toàn bộ dữ liệu: 63 tỉnh/thành, Hà Nội 30 quận/huyện, Hoàn Kiếm 18 phường/xã (mã thật của GHN)
        assertThat(mockClient.provinces()).hasSize(63);
        assertThat(mockClient.districts(201)).hasSize(30);
        assertThat(mockClient.wards(1489)).extracting(GhnWard::name).contains("Phường Phúc Tân");
        assertThat(mockClient.wards(1489)).extracting(GhnWard::code).contains("1A0216");
        assertThat(mockClient.fee(1454, "1A0808", 1500).total()).isPositive();
        assertThat(mockClient.createOrder(new GhnCreateOrderRequest()).orderCode()).startsWith("GHNMOCK");
        assertThat(mockClient.orderDetail("GHNMOCK1").orderCode()).isEqualTo("GHNMOCK1");
        server.verify();
    }
}
