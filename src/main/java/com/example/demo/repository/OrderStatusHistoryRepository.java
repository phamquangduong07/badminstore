package com.example.demo.repository;

import com.example.demo.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn lịch sử trạng thái – dùng dựng timeline đơn hàng cho khách. */
public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Integer> {

    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Integer orderId);

    /** Timeline kèm người đổi trạng thái (fetch join vì open-in-view = false). */
    @Query("""
            select h from OrderStatusHistory h
                left join fetch h.changedBy
            where h.order.id = :orderId
            order by h.createdAt asc, h.id asc
            """)
    List<OrderStatusHistory> findTimeline(@Param("orderId") Integer orderId);
}
