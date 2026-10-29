package com.example.demo.service;

import com.example.demo.dto.admin.PosPriceRequest;
import com.example.demo.dto.admin.PosPriceResult;
import com.example.demo.dto.admin.PosQrRequest;
import com.example.demo.dto.admin.PosQrResult;
import com.example.demo.dto.shop.PaymentQrView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR chuyển khoản cho màn bán hàng tại quầy. Đơn chỉ được tạo khi bấm "Thu tiền", nên QR phải được dựng
 * TRƯỚC với một mã đơn cấp sẵn (nội dung chuyển khoản). Số tiền luôn lấy từ {@link CounterSaleService#priceCart}
 * (giá DB + voucher), không nhận từ trình duyệt; khi bán, mã đơn được kiểm tra lại (đúng định dạng + chưa dùng).
 */
@Service
@RequiredArgsConstructor
public class PosQrService {

    private final CounterSaleService counterSaleService;
    private final QrPaymentService qrPaymentService;
    private final OrderCodeGenerator orderCodeGenerator;

    @Transactional(readOnly = true)
    public PosQrResult qrFor(PosQrRequest request) {
        if (!qrPaymentService.isAvailable()) {
            return PosQrResult.unavailable("Chưa cấu hình tài khoản ngân hàng nhận tiền (app.payment.bank).");
        }
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            return PosQrResult.unavailable("Hãy thêm sản phẩm vào hoá đơn để tạo mã QR.");
        }
        PosPriceResult price = counterSaleService.priceCart(
                new PosPriceRequest(request.voucherCode(), request.lines()));
        boolean hasLineError = price.lines().stream().anyMatch(line -> line.error() != null);
        if (hasLineError) {
            return PosQrResult.unavailable("Hoá đơn còn dòng lỗi (sai SKU hoặc vượt tồn kho) – chưa tạo được QR.");
        }
        if (price.total() == null || price.total().signum() <= 0) {
            return PosQrResult.unavailable("Hoá đơn 0 ₫ – không cần chuyển khoản.");
        }
        String orderCode = orderCodeGenerator.validatedOrNext(request.orderCode());
        Optional<PaymentQrView> qr = qrPaymentService.build(orderCode, price.total());
        return qr.map(view -> new PosQrResult(true, null, orderCode, price.total(), view))
                .orElseGet(() -> PosQrResult.unavailable("Không tạo được mã QR."));
    }
}
