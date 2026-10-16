package com.example.demo.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Định dạng tiền VND ở tầng Java (thông báo flash, message lỗi).
 * Template dùng th:fragment "money" của fragments/common.html – cùng quy ước "1.500.000 ₫".
 */
public final class Money {

    private static final DecimalFormat FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        symbols.setGroupingSeparator('.');
        FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private Money() {
    }

    public static String format(BigDecimal value) {
        return value == null ? "0 ₫" : FORMAT.format(value) + " ₫";
    }
}
