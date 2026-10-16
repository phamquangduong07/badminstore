package com.example.demo.service;

import com.example.demo.dto.admin.DashboardView;
import com.example.demo.dto.admin.RevenueRow;
import com.example.demo.dto.admin.StatusCountRow;
import com.example.demo.dto.admin.TopProductRow;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.OrderStatus;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.VariantStatus;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductVariantRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thống kê cho trang Tổng quan của admin.
 *
 * QUY ƯỚC TÍNH (ghi rõ để khi bảo vệ giải thích được):
 * – "Doanh thu" chỉ tính đơn DELIVERED (tiền đã thu); đơn huỷ/hoàn trả không tính.
 * – "Số đơn" đếm theo ngày tạo, bỏ đơn đã huỷ (đơn hoàn trả vẫn tính vì đã từng bán).
 * – Biểu đồ gom số liệu trong Java từ danh sách dòng thô ⇒ không phụ thuộc hàm CAST ngày của SQL Server.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** Số ngày hiển thị trên biểu đồ doanh thu. */
    public static final int CHART_DAYS = 14;

    /** Ngưỡng cảnh báo sắp hết hàng. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    /** Chỉ đơn đã giao thành công mới được tính vào doanh thu. */
    private static final OrderStatus REVENUE_STATUS = OrderStatus.DELIVERED;

    /** Đơn đã bán (đã xác nhận trở lên) – dùng cho bảng xếp hạng sản phẩm bán chạy. */
    private static final List<OrderStatus> SOLD_STATUSES =
            List.of(OrderStatus.CONFIRMED, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    private static final int TOP_PRODUCT_LIMIT = 5;
    private static final int LOW_STOCK_LIMIT = 5;
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardView load() {
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime startOfChart = today.minusDays(CHART_DAYS - 1L).atStartOfDay();

        // Lấy một lần các dòng đơn từ mốc sớm nhất cần dùng, rồi tự gom lại theo ngày.
        LocalDateTime earliest = firstOfMonth.atStartOfDay().isBefore(startOfChart)
                ? firstOfMonth.atStartOfDay() : startOfChart;
        List<RevenueRow> rows = orderRepository.findRowsSince(earliest);

        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        Map<LocalDate, Long> ordersByDay = new HashMap<>();
        BigDecimal todayRevenue = BigDecimal.ZERO;
        BigDecimal monthRevenue = BigDecimal.ZERO;
        long todayOrders = 0;

        for (RevenueRow row : rows) {
            LocalDate day = row.createdAt().toLocalDate();

            if (row.orderStatus() != OrderStatus.CANCELLED) {
                ordersByDay.merge(day, 1L, Long::sum);
                if (day.equals(today)) {
                    todayOrders++;
                }
            }
            if (row.orderStatus() == REVENUE_STATUS) {
                revenueByDay.merge(day, row.totalAmount(), BigDecimal::add);
                if (!day.isBefore(firstOfMonth)) {
                    monthRevenue = monthRevenue.add(row.totalAmount());
                }
                if (day.equals(today)) {
                    todayRevenue = todayRevenue.add(row.totalAmount());
                }
            }
        }

        // Ngày nào không có đơn thì ghi 0 để biểu đồ không bị đứt đoạn
        List<String> revenueLabels = new ArrayList<>();
        List<BigDecimal> revenueValues = new ArrayList<>();
        List<Long> orderCountValues = new ArrayList<>();
        for (int i = CHART_DAYS - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            revenueLabels.add(day.format(LABEL_FORMAT));
            revenueValues.add(revenueByDay.getOrDefault(day, BigDecimal.ZERO));
            orderCountValues.add(ordersByDay.getOrDefault(day, 0L));
        }

        // Đếm đơn theo trạng thái: luôn hiện đủ 6 trạng thái (kể cả trạng thái chưa có đơn nào)
        Map<OrderStatus, Long> countsByStatus = new HashMap<>();
        for (StatusCountRow row : orderRepository.countByStatusGrouped()) {
            countsByStatus.put(row.orderStatus(), row.total());
        }
        List<String> statusLabels = new ArrayList<>();
        List<Long> statusValues = new ArrayList<>();
        for (OrderStatus status : OrderStatus.values()) {
            statusLabels.add(status.getLabel());
            statusValues.add(countsByStatus.getOrDefault(status, 0L));
        }

        List<TopProductRow> topProducts = orderRepository.findTopProducts(SOLD_STATUSES,
                PageRequest.of(0, TOP_PRODUCT_LIMIT));
        List<ProductVariant> lowStockVariants = productVariantRepository.findLowStock(
                VariantStatus.ACTIVE, LOW_STOCK_THRESHOLD, PageRequest.of(0, LOW_STOCK_LIMIT));

        return new DashboardView(
                countsByStatus.getOrDefault(OrderStatus.PENDING, 0L),
                todayOrders,
                userRepository.countByRole(UserRole.CUSTOMER),
                productRepository.countByDeletedFalseAndStatus(ProductStatus.ACTIVE),
                productVariantRepository.countByStatus(VariantStatus.ACTIVE),
                todayRevenue,
                monthRevenue,
                revenueLabels,
                revenueValues,
                orderCountValues,
                statusLabels,
                statusValues,
                topProducts,
                lowStockVariants);
    }
}
