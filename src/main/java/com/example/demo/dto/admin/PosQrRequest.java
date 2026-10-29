package com.example.demo.dto.admin;

import java.util.List;

/**
 * Yêu cầu dựng mã QR chuyển khoản cho hoá đơn quầy đang lập: chỉ SKU + số lượng + voucher
 * (giống tính tiền) và mã đơn đã được cấp ở lần trước (nếu có). Số tiền do server tính lại.
 */
public record PosQrRequest(String voucherCode, List<PosPriceRequest.Line> lines, String orderCode) {
}
