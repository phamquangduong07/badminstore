package com.example.demo.dto.shop;

import java.math.BigDecimal;

/**
 * Số liệu của bước thanh toán: giỏ hàng + phí vận chuyển + voucher + tổng thanh toán.
 * Mọi con số đều do server tính từ dữ liệu trong DB (không nhận từ client).
 *
 * @param voucherError lý do voucher trong URL không dùng được (null = không có lỗi) – hiển thị
 *                     ngay trên trang để khách sửa mã, không cần chuyển trang.
 */
public record CheckoutSummary(CartView cart,
                              BigDecimal shippingFee,
                              BigDecimal freeShippingThreshold,
                              AppliedVoucher voucher,
                              String voucherError) {

    /*
     * Getters kiểu bean cho các thành phần của record.
     * Thymeleaf/SpEL đọc thuộc tính qua getter (getX) chứ không hiểu accessor của record (x()),
     * nên cần thêm các phương thức dưới đây để template dùng được ${summary.voucher}…
     */
    public CartView getCart() {
        return cart;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public BigDecimal getFreeShippingThreshold() {
        return freeShippingThreshold;
    }

    public AppliedVoucher getVoucher() {
        return voucher;
    }

    public String getVoucherError() {
        return voucherError;
    }

    /** Số tiền cần mua thêm để được miễn phí vận chuyển (0 = đã được miễn). */
    public BigDecimal getAmountToFreeShipping() {
        if (freeShippingThreshold == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal missing = freeShippingThreshold.subtract(cart.getSubtotal());
        return missing.signum() <= 0 ? BigDecimal.ZERO : missing;
    }

    public boolean isFreeShipping() {
        return shippingFee != null && shippingFee.signum() == 0;
    }

    public BigDecimal getDiscountAmount() {
        return voucher == null ? BigDecimal.ZERO : voucher.discountAmount();
    }

    /** Tổng phải trả = tạm tính + phí vận chuyển − giảm giá (khớp CK_orders_total). */
    public BigDecimal getTotalAmount() {
        return cart.getSubtotal().add(shippingFee == null ? BigDecimal.ZERO : shippingFee)
                .subtract(getDiscountAmount());
    }

    /** Số sản phẩm (tổng số lượng) trong đơn. */
    public int getTotalQuantity() {
        return cart.getTotalQuantity();
    }
}
