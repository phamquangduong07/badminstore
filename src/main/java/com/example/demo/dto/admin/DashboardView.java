package com.example.demo.dto.admin;

import com.example.demo.entity.ProductVariant;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

/**
 * Dữ liệu trang Tổng quan của admin: các thẻ số liệu + dữ liệu biểu đồ Chart.js + 2 danh sách phụ.
 *
 * Dữ liệu biểu đồ được đưa sang dạng danh sách nhãn/giá trị ngay ở Service để template
 * chỉ việc in ra JS (th:inline="javascript"), không xử lý logic trong view.
 */
@Getter
public class DashboardView {

    private final long pendingOrders;
    private final long todayOrders;
    private final long totalCustomers;
    private final long activeProducts;
    private final long activeVariants;
    private final BigDecimal todayRevenue;
    private final BigDecimal monthRevenue;

    /** Nhãn "dd/MM" và doanh thu từng ngày (đơn đã giao) trong 14 ngày gần nhất. */
    private final List<String> revenueLabels;
    private final List<BigDecimal> revenueValues;

    /** Số đơn tạo mới mỗi ngày (mọi trạng thái trừ đơn đã huỷ). */
    private final List<Long> orderCountValues;

    /** Nhãn trạng thái tiếng Việt + số đơn tương ứng (đủ 6 trạng thái kể cả 0). */
    private final List<String> statusLabels;
    private final List<Long> statusValues;

    private final List<TopProductRow> topProducts;
    private final List<ProductVariant> lowStockVariants;

    public DashboardView(long pendingOrders, long todayOrders, long totalCustomers, long activeProducts,
                         long activeVariants, BigDecimal todayRevenue, BigDecimal monthRevenue,
                         List<String> revenueLabels, List<BigDecimal> revenueValues, List<Long> orderCountValues,
                         List<String> statusLabels, List<Long> statusValues,
                         List<TopProductRow> topProducts, List<ProductVariant> lowStockVariants) {
        this.pendingOrders = pendingOrders;
        this.todayOrders = todayOrders;
        this.totalCustomers = totalCustomers;
        this.activeProducts = activeProducts;
        this.activeVariants = activeVariants;
        this.todayRevenue = todayRevenue;
        this.monthRevenue = monthRevenue;
        this.revenueLabels = List.copyOf(revenueLabels);
        this.revenueValues = List.copyOf(revenueValues);
        this.orderCountValues = List.copyOf(orderCountValues);
        this.statusLabels = List.copyOf(statusLabels);
        this.statusValues = List.copyOf(statusValues);
        this.topProducts = List.copyOf(topProducts);
        this.lowStockVariants = List.copyOf(lowStockVariants);
    }
}
