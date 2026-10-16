package com.example.demo.repository;

import com.example.demo.dto.admin.RevenueRow;
import com.example.demo.dto.admin.StatusCountRow;
import com.example.demo.dto.admin.TopProductRow;
import com.example.demo.dto.shop.OrderSummaryView;
import com.example.demo.entity.Order;
import com.example.demo.enums.OrderStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn đơn hàng. */
public interface OrderRepository extends JpaRepository<Order, Integer> {

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByIdAndUserId(Integer id, Integer userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    Page<Order> findByOrderStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByOrderStatus(OrderStatus status);

    /**
     * Chi tiết đơn để HIỂN THỊ cho CHÍNH KHÁCH: fetch join sẵn items + biến thể + sản phẩm + thanh toán
     * + khách + voucher (open-in-view = false nên template chỉ đọc được dữ liệu đã nạp).
     */
    @Query("""
            select distinct o from Order o
                left join fetch o.items i
                left join fetch i.productVariant v
                left join fetch v.product p
                left join fetch o.payment
                left join fetch o.user
                left join fetch o.voucher
            where o.id = :id and o.user.id = :userId
            """)
    Optional<Order> findDetailForUser(@Param("id") Integer id, @Param("userId") Integer userId);

    /** Như trên nhưng cho ADMIN – xem được mọi đơn (kể cả đơn của khách vãng lai). */
    @Query("""
            select distinct o from Order o
                left join fetch o.items i
                left join fetch i.productVariant v
                left join fetch v.product p
                left join fetch o.payment
                left join fetch o.user
                left join fetch o.voucher
            where o.id = :id
            """)
    Optional<Order> findDetailForAdmin(@Param("id") Integer id);

    /** Danh sách đơn của 1 khách (dòng dữ liệu gọn, 2 truy vấn con đếm số dòng/số lượng). */
    @Query(value = """
            select new com.example.demo.dto.shop.OrderSummaryView(o.id, o.orderCode, o.orderSource, o.orderStatus,
                o.totalAmount, o.createdAt, o.receiverName, u.email, p.paymentMethod, p.paymentStatus,
                (select count(i) from OrderItem i where i.order = o),
                (select coalesce(sum(i.quantity), 0) from OrderItem i where i.order = o))
            from Order o
                left join o.user u
                left join o.payment p
            where o.user.id = :userId
            order by o.createdAt desc, o.id desc
            """,
            countQuery = "select count(o) from Order o where o.user.id = :userId")
    Page<OrderSummaryView> findMyOrderSummaries(@Param("userId") Integer userId, Pageable pageable);

    /**
     * Danh sách đơn cho ADMIN: lọc theo trạng thái (null = tất cả) và từ khoá
     * (mã đơn / số điện thoại người nhận / email khách).
     */
    @Query(value = """
            select new com.example.demo.dto.shop.OrderSummaryView(o.id, o.orderCode, o.orderSource, o.orderStatus,
                o.totalAmount, o.createdAt, o.receiverName, u.email, p.paymentMethod, p.paymentStatus,
                (select count(i) from OrderItem i where i.order = o),
                (select coalesce(sum(i.quantity), 0) from OrderItem i where i.order = o))
            from Order o
                left join o.user u
                left join o.payment p
            where (:status is null or o.orderStatus = :status)
                and (:keyword is null
                     or lower(o.orderCode) like lower(concat('%', :keyword, '%'))
                     or o.receiverPhone like concat('%', :keyword, '%')
                     or lower(u.email) like lower(concat('%', :keyword, '%')))
            order by o.createdAt desc, o.id desc
            """,
            countQuery = """
                    select count(o) from Order o left join o.user u
                    where (:status is null or o.orderStatus = :status)
                        and (:keyword is null
                             or lower(o.orderCode) like lower(concat('%', :keyword, '%'))
                             or o.receiverPhone like concat('%', :keyword, '%')
                             or lower(u.email) like lower(concat('%', :keyword, '%')))
                    """)
    Page<OrderSummaryView> findOrderSummariesForAdmin(@Param("status") OrderStatus status,
                                                      @Param("keyword") String keyword,
                                                      Pageable pageable);

    /** Dòng đơn thô kể từ mốc thời gian – Service gom theo ngày để vẽ biểu đồ dashboard. */
    @Query("""
            select new com.example.demo.dto.admin.RevenueRow(o.createdAt, o.totalAmount, o.orderStatus)
            from Order o
            where o.createdAt >= :from
            order by o.createdAt asc, o.id asc
            """)
    List<RevenueRow> findRowsSince(@Param("from") LocalDateTime from);

    /** Số đơn theo từng trạng thái bằng 1 câu truy vấn (thay vì 6 lần count). */
    @Query("""
            select new com.example.demo.dto.admin.StatusCountRow(o.orderStatus, count(o))
            from Order o
            group by o.orderStatus
            """)
    List<StatusCountRow> countByStatusGrouped();

    /** Sản phẩm bán chạy: chỉ tính đơn đã xác nhận trở lên (bỏ đơn chờ, đơn huỷ, đơn hoàn trả). */
    @Query("""
            select new com.example.demo.dto.admin.TopProductRow(i.productName, sum(i.quantity), sum(i.subtotal))
            from OrderItem i
            where i.order.orderStatus in :statuses
            group by i.productName
            order by sum(i.quantity) desc
            """)
    List<TopProductRow> findTopProducts(@Param("statuses") Collection<OrderStatus> statuses, Pageable pageable);

    /** Đơn đang giao có mã vận đơn GHN – job định kỳ dùng để đồng bộ trạng thái vận đơn. */
    List<Order> findByGhnOrderCodeNotNullAndOrderStatusIn(Collection<OrderStatus> statuses);
}
