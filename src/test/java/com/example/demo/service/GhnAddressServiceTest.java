package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.GhnProperties;
import com.example.demo.dto.ghn.GhnFee;
import com.example.demo.dto.ghn.GhnProvince;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử lớp bọc dữ liệu địa chỉ GHN cho giao diện:
 *  – chưa khai báo kho lấy hàng ⇒ KHÔNG gọi GHN tính phí, trả null để dùng phí mặc định của shop;
 *  – GHN tắt/lỗi ⇒ trả danh sách rỗng, giao diện chuyển sang nhập địa chỉ bằng tay (không vỡ trang);
 *  – khối lượng kiện hàng tính đúng theo số cây vợt.
 */
@ExtendWith(MockitoExtension.class)
class GhnAddressServiceTest {

    @Mock
    private GhnClient ghnClient;

    private GhnProperties properties;
    private GhnAddressService service;

    @BeforeEach
    void setUp() {
        properties = new GhnProperties();
        properties.setEnabled(true);
        properties.setToken("token-test");
        service = new GhnAddressService(ghnClient, properties);
    }

    @Test
    @DisplayName("Chưa khai báo shopId/kho ⇒ không gọi GHN tính phí, trả null để dùng phí mặc định")
    void feeOrNull_skipsWhenShippingNotConfigured() {
        when(ghnClient.isShippingAvailable()).thenReturn(false);

        assertThat(service.feeOrNull(1454, "1A0808", 1800)).isNull();
        assertThat(service.isShippingAvailable()).isFalse();
        verify(ghnClient, never()).fee(anyInt(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Đã cấu hình đủ kho ⇒ phí GHN được dùng thay cho phí mặc định")
    void feeOrNull_returnsGhnFee() {
        when(ghnClient.isShippingAvailable()).thenReturn(true);
        when(ghnClient.fee(1454, "1A0808", 1800)).thenReturn(new GhnFee(34500L, 30000L, 4500L, 0L, 0L, 0L));

        assertThat(service.feeOrNull(1454, "1A0808", 1800)).isEqualByComparingTo("34500");
    }

    @Test
    @DisplayName("GHN tắt ⇒ danh sách tỉnh/thành rỗng thay vì ném lỗi ra giao diện")
    void provinces_returnsEmptyWhenGhnDisabled() {
        when(ghnClient.isAddressAvailable()).thenReturn(false);

        assertThat(service.provinces()).isEmpty();
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Tra cứu tỉnh/thành chỉ cần token (không cần shopId)")
    void provinces_worksWithTokenOnly() {
        when(ghnClient.isAddressAvailable()).thenReturn(true);
        when(ghnClient.provinces()).thenReturn(List.of(new GhnProvince(201, "Hà Nội")));

        assertThat(service.isAvailable()).isTrue();
        assertThat(service.provinces()).extracting(GhnProvince::name).containsExactly("Hà Nội");
    }

    @Test
    @DisplayName("Khối lượng kiện hàng: 1 cây 1.500 g, mỗi cây thêm 300 g")
    void shippingWeight_countsExtraItems() {
        assertThat(service.shippingWeight(0)).isEqualTo(1500);
        assertThat(service.shippingWeight(1)).isEqualTo(1500);
        assertThat(service.shippingWeight(3)).isEqualTo(2100);
    }
}
