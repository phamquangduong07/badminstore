package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.demo.entity.Order;
import com.example.demo.repository.OrderRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Mã đơn cấp sẵn cho QR của quầy: chỉ được dùng lại khi đúng định dạng và chưa có đơn nào dùng. */
@ExtendWith(MockitoExtension.class)
class OrderCodeGeneratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("Mã đúng định dạng và chưa dùng ⇒ giữ nguyên")
    void validatedOrNext_keepsFreshValidCode() {
        when(orderRepository.findByOrderCode("BS260101120000123")).thenReturn(Optional.empty());

        assertThat(new OrderCodeGenerator(orderRepository).validatedOrNext("BS260101120000123"))
                .isEqualTo("BS260101120000123");
    }

    @Test
    @DisplayName("Mã sai định dạng (hoặc bị trình duyệt sửa) ⇒ sinh mã mới hợp lệ")
    void validatedOrNext_replacesInvalidCode() {
        when(orderRepository.findByOrderCode(anyString())).thenReturn(Optional.empty());
        OrderCodeGenerator generator = new OrderCodeGenerator(orderRepository);

        for (String bad : new String[]{null, "", "HACKED", "BS123", "bs260101120000123", "BS26010112000012x", "BS2601011200001234"}) {
            assertThat(generator.validatedOrNext(bad)).matches("BS\\d{15}").isNotEqualTo(bad);
        }
    }

    @Test
    @DisplayName("Mã đã có đơn dùng ⇒ không dùng lại, sinh mã mới")
    void validatedOrNext_replacesCodeAlreadyUsed() {
        when(orderRepository.findByOrderCode("BS260101120000123")).thenReturn(Optional.of(new Order()));
        when(orderRepository.findByOrderCode(org.mockito.ArgumentMatchers.argThat(
                code -> code != null && !code.equals("BS260101120000123")))).thenReturn(Optional.empty());

        assertThat(new OrderCodeGenerator(orderRepository).validatedOrNext("BS260101120000123"))
                .matches("BS\\d{15}").isNotEqualTo("BS260101120000123");
    }
}
