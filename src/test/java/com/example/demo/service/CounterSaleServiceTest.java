package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ShopProperties;
import com.example.demo.dto.admin.CounterSaleForm;
import com.example.demo.dto.admin.CounterSaleResult;
import com.example.demo.dto.admin.CustomerLookupView;
import com.example.demo.dto.admin.ManualOrderForm;
import com.example.demo.dto.admin.ManualOrderLineForm;
import com.example.demo.dto.admin.PosPriceRequest;
import com.example.demo.dto.admin.PosPriceResult;
import com.example.demo.dto.admin.PosProductView;
import com.example.demo.entity.Order;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductVariant;
import com.example.demo.entity.User;
import com.example.demo.entity.Voucher;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * Kiểm thử nghiệp vụ BÁN HÀNG TẠI QUẦY (Mockito):
 * tiền – tồn kho – voucher đều do server quyết định; tiền khách đưa/tiền thừa tính ở server;
 * khách quen được gắn theo SĐT; quầy KHÔNG dùng phương thức COD.
 */
@ExtendWith(MockitoExtension.class)
class CounterSaleServiceTest {

    private static final Integer ADMIN_ID = 5;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private VoucherService voucherService;

    private ShopProperties shopProperties;
    private CounterSaleService counterSaleService;

    @BeforeEach
    void setUp() {
        shopProperties = new ShopProperties();
        shopProperties.setName("BadStore");
        shopProperties.setAddress("Số 1 Đại Cồ Việt, Hà Nội");
        counterSaleService = new CounterSaleService(productVariantRepository, userRepository,
                orderService, voucherService, shopProperties);
    }

    private ProductVariant variant(int id, String sku, String price, int stock) {
        Product product = new Product();
        product.setName("Vợt cầu lông " + sku);
        ProductVariant created = new ProductVariant();
        created.setId(id);
        created.setProduct(product);
        created.setSku(sku);
        created.setColor("Đen");
        created.setWeightClass("4U");
        created.setGripSize("G5");
        created.setPrice(new BigDecimal(price));
        created.setStockQuantity(stock);
        created.setStatus(VariantStatus.ACTIVE);
        return created;
    }

    private Order createdOrder(String total) {
        Order order = new Order();
        order.setOrderCode("BS261016000001");
        order.setTotalAmount(new BigDecimal(total));
        order.setNote("Bán hàng tại quầy");
        return order;
    }

    private CounterSaleForm cashForm(int... quantities) {
        CounterSaleForm form = new CounterSaleForm();
        String[] skus = {"SKU-1", "SKU-2"};
        List<ManualOrderLineForm> lines = new java.util.ArrayList<>();
        for (int index = 0; index < quantities.length; index++) {
            ManualOrderLineForm line = new ManualOrderLineForm();
            line.setSku(skus[index]);
            line.setQuantity(quantities[index]);
            lines.add(line);
        }
        form.setLines(lines);
        form.setPaymentMethod("CASH");
        return form;
    }

    @Test
    @DisplayName("Tìm sản phẩm ở quầy: chỉ đọc DB, trả về tên/phiên bản/giá/tồn kho cho giao diện")
    void searchForPos_mapsVariantToView() {
        ProductVariant variant = variant(11, "SKU-1", "1490000", 4);
        when(productVariantRepository.searchActiveForSale(eq("yonex"), any(PageRequest.class)))
                .thenReturn(List.of(variant));

        List<PosProductView> products = counterSaleService.searchForPos("yonex");

        assertThat(products).hasSize(1);
        assertThat(products.get(0).sku()).isEqualTo("SKU-1");
        assertThat(products.get(0).productName()).isEqualTo("Vợt cầu lông SKU-1");
        assertThat(products.get(0).variantLabel()).isEqualTo("Đen / 4U / G5");
        assertThat(products.get(0).price()).isEqualByComparingTo("1490000");
        assertThat(products.get(0).stock()).isEqualTo(4);
        assertThat(products.get(0).available()).isTrue();
    }

    @Test
    @DisplayName("Tính tiền hoá đơn quầy: dòng vượt tồn kho bị đánh dấu lỗi và KHÔNG cộng vào tạm tính")
    void priceCart_flagsStockProblem() {
        when(productVariantRepository.findBySkuWithProduct("SKU-1"))
                .thenReturn(Optional.of(variant(11, "SKU-1", "500000", 5)));
        when(productVariantRepository.findBySkuWithProduct("SKU-2"))
                .thenReturn(Optional.of(variant(12, "SKU-2", "300000", 1)));

        PosPriceResult result = counterSaleService.priceCart(new PosPriceRequest(null, List.of(
                new PosPriceRequest.Line("SKU-1", 2),
                new PosPriceRequest.Line("SKU-2", 3))));

        assertThat(result.subtotal()).isEqualByComparingTo("1000000");
        assertThat(result.total()).isEqualByComparingTo("1000000");
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).lineTotal()).isEqualByComparingTo("1000000");
        assertThat(result.lines().get(1).error()).contains("chỉ còn 1");
        verify(voucherService, never()).requireUsable(any(), any());
    }

    @Test
    @DisplayName("Tính tiền hoá đơn quầy: SKU lạ cũng bị báo lỗi ngay trên dòng hàng")
    void priceCart_flagsUnknownSku() {
        when(productVariantRepository.findBySkuWithProduct("KHONG-CO"))
                .thenReturn(Optional.empty());

        PosPriceResult result = counterSaleService.priceCart(new PosPriceRequest(null,
                List.of(new PosPriceRequest.Line("KHONG-CO", 1))));

        assertThat(result.subtotal()).isEqualByComparingTo("0");
        assertThat(result.lines().get(0).error()).contains("Không tìm thấy SKU");
    }

    @Test
    @DisplayName("Tính tiền hoá đơn quầy: voucher giảm theo số tiền server tính (không tin client)")
    void priceCart_appliesVoucher() {
        when(productVariantRepository.findBySkuWithProduct("SKU-1"))
                .thenReturn(Optional.of(variant(11, "SKU-1", "1000000", 5)));
        Voucher voucher = new Voucher();
        voucher.setCode("GIAM10");
        when(voucherService.normalizeCode("giam10")).thenReturn("GIAM10");
        when(voucherService.requireUsable("GIAM10", new BigDecimal("1000000"))).thenReturn(voucher);
        when(voucherService.discountOf(voucher, new BigDecimal("1000000")))
                .thenReturn(new BigDecimal("100000"));
        when(voucherService.describe(voucher)).thenReturn("Giảm 10% (tối đa 500.000 ₫)");

        PosPriceResult result = counterSaleService.priceCart(new PosPriceRequest("giam10",
                List.of(new PosPriceRequest.Line("SKU-1", 1))));

        assertThat(result.voucherCode()).isEqualTo("GIAM10");
        assertThat(result.voucherDescription()).contains("Giảm 10%");
        assertThat(result.discount()).isEqualByComparingTo("100000");
        assertThat(result.total()).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Tính tiền hoá đơn quầy: voucher không dùng được thì báo lý do, tiền vẫn tính bình thường")
    void priceCart_reportsVoucherError() {
        when(productVariantRepository.findBySkuWithProduct("SKU-1"))
                .thenReturn(Optional.of(variant(11, "SKU-1", "200000", 5)));
        when(voucherService.normalizeCode("HETHAN")).thenReturn("HETHAN");
        when(voucherService.requireUsable("HETHAN", new BigDecimal("200000")))
                .thenThrow(new BusinessException("Voucher đã hết hạn sử dụng."));

        PosPriceResult result = counterSaleService.priceCart(new PosPriceRequest("HETHAN",
                List.of(new PosPriceRequest.Line("SKU-1", 1))));

        assertThat(result.voucherError()).isEqualTo("Voucher đã hết hạn sử dụng.");
        assertThat(result.discount()).isEqualByComparingTo("0");
        assertThat(result.total()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("Bán khách lẻ: đơn MANUAL xác nhận ngay, phí ship 0, tên/SĐT mặc định, đủ tiền thối")
    void sell_walkInCashWithChange() {
        Order order = createdOrder("1000000");
        when(orderService.createManualOrder(any(ManualOrderForm.class), eq(ADMIN_ID))).thenReturn(order);

        CounterSaleForm form = cashForm(2);
        form.setCashReceived(new BigDecimal("1200000"));

        CounterSaleResult result = counterSaleService.sell(form, ADMIN_ID);

        assertThat(result.cashReceived()).isEqualByComparingTo("1200000");
        assertThat(result.change()).isEqualByComparingTo("200000");
        assertThat(result.isCash()).isTrue();

        ArgumentCaptor<ManualOrderForm> captor = ArgumentCaptor.forClass(ManualOrderForm.class);
        verify(orderService).createManualOrder(captor.capture(), eq(ADMIN_ID));
        ManualOrderForm manual = captor.getValue();
        assertThat(manual.isConfirmNow()).isTrue();
        assertThat(manual.getShippingFee()).isEqualByComparingTo("0");
        assertThat(manual.getPaymentMethod()).isEqualTo("CASH");
        assertThat(manual.getCustomerEmail()).isNull();
        assertThat(manual.getReceiverName()).isEqualTo(CounterSaleService.WALK_IN_NAME);
        assertThat(manual.getReceiverPhone()).isEqualTo(CounterSaleService.WALK_IN_PHONE);
        assertThat(manual.getShippingAddress()).contains("Khách nhận hàng tại quầy BadStore");
        assertThat(manual.getNote()).contains("Bán hàng tại quầy").contains("khách lẻ");
        // Tiền thừa được ghi vào ghi chú đơn để phiếu in/chi tiết đơn luôn có chứng từ.
        assertThat(order.getNote()).contains("Khách đưa").contains("thừa");
    }

    @Test
    @DisplayName("Bán tại quầy: tiền khách đưa CHƯA ĐỦ thì chặn lại và transaction rollback (không tạo đơn)")
    void sell_rejectsCashShortfall() {
        Order order = createdOrder("1000000");
        when(orderService.createManualOrder(any(ManualOrderForm.class), eq(ADMIN_ID))).thenReturn(order);

        CounterSaleForm form = cashForm(2);
        form.setCashReceived(new BigDecimal("500000"));

        assertThatThrownBy(() -> counterSaleService.sell(form, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cần thu thêm");
    }

    @Test
    @DisplayName("Khách quen: tra được theo SĐT ⇒ hoá đơn gắn vào tài khoản khách")
    void sell_attachesRegisteredCustomerByPhone() {
        User customer = new User();
        customer.setEmail("khach.quen@example.com");
        customer.setFullName("Trần Thị B");
        customer.setPhone("0987654321");
        when(userRepository.findFirstByPhoneOrderByIdAsc("0987654321")).thenReturn(Optional.of(customer));
        when(orderService.createManualOrder(any(ManualOrderForm.class), eq(ADMIN_ID)))
                .thenReturn(createdOrder("500000"));

        CounterSaleForm form = cashForm(1);
        form.setCustomerPhone("0987654321");

        counterSaleService.sell(form, ADMIN_ID);

        ArgumentCaptor<ManualOrderForm> captor = ArgumentCaptor.forClass(ManualOrderForm.class);
        verify(orderService).createManualOrder(captor.capture(), eq(ADMIN_ID));
        ManualOrderForm manual = captor.getValue();
        assertThat(manual.getCustomerEmail()).isEqualTo("khach.quen@example.com");
        assertThat(manual.getReceiverName()).isEqualTo("Trần Thị B");
        assertThat(manual.getReceiverPhone()).isEqualTo("0987654321");
        assertThat(manual.getNote()).contains("khách quen");
    }

    @Test
    @DisplayName("Chuyển khoản: quầy tích \"đã nhận tiền\" ⇒ hoá đơn được đánh dấu đã thanh toán")
    void sell_transferConfirmedMarksPaid() {
        Order order = createdOrder("1490000");
        order.setId(77);
        when(orderService.createManualOrder(any(ManualOrderForm.class), eq(ADMIN_ID))).thenReturn(order);

        CounterSaleForm form = cashForm(1);
        form.setPaymentMethod("BANK_TRANSFER");
        form.setTransferConfirmed(true);

        CounterSaleResult result = counterSaleService.sell(form, ADMIN_ID);

        assertThat(result.isCash()).isFalse();
        verify(orderService).confirmPayment(77, null, ADMIN_ID);
    }

    @Test
    @DisplayName("Quầy không bán theo hình thức COD (COD là nghiệp vụ giao hàng)")
    void sell_rejectsCod() {
        CounterSaleForm form = cashForm(1);
        form.setPaymentMethod("COD");

        assertThatThrownBy(() -> counterSaleService.sell(form, ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiền mặt hoặc chuyển khoản");
        verify(orderService, never()).createManualOrder(any(), any());
    }

    @Test
    @DisplayName("Tra khách theo SĐT: có tài khoản thì trả thông tin, không có thì báo khách lẻ")
    void lookupCustomer_foundAndNotFound() {
        User customer = new User();
        customer.setEmail("khach@example.com");
        customer.setFullName("Lê Văn C");
        customer.setPhone("0912345678");
        when(userRepository.findFirstByPhoneOrderByIdAsc("0912345678")).thenReturn(Optional.of(customer));
        when(userRepository.findFirstByPhoneOrderByIdAsc("0999999999")).thenReturn(Optional.empty());

        CustomerLookupView found = counterSaleService.lookupCustomer("0912345678");
        CustomerLookupView notFound = counterSaleService.lookupCustomer("0999999999");
        CustomerLookupView blank = counterSaleService.lookupCustomer("  ");

        assertThat(found.found()).isTrue();
        assertThat(found.fullName()).isEqualTo("Lê Văn C");
        assertThat(notFound.found()).isFalse();
        assertThat(blank.found()).isFalse();
    }

    @Test
    @DisplayName("Đơn quầy trả tiền mặt: không gọi xác nhận thanh toán tay (tiền mặt đã PAID ngay trong OrderService)")
    void sell_cashDoesNotConfirmPaymentAgain() {
        when(orderService.createManualOrder(any(ManualOrderForm.class), eq(ADMIN_ID)))
                .thenReturn(createdOrder("500000"));

        CounterSaleForm form = cashForm(1);
        form.setCashReceived(new BigDecimal("500000"));

        counterSaleService.sell(form, ADMIN_ID);

        verify(orderService, never()).confirmPayment(any(), any(), any());
    }
}
