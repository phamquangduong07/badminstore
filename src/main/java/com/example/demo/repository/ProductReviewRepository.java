package com.example.demo.repository;

import com.example.demo.entity.ProductReview;
import com.example.demo.enums.ReviewStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn đánh giá sản phẩm. */
public interface ProductReviewRepository extends JpaRepository<ProductReview, Integer> {

    Page<ProductReview> findByProductIdAndStatusOrderByCreatedAtDesc(Integer productId, ReviewStatus status,
                                                                    Pageable pageable);

    /** Mỗi dòng đơn chỉ đánh giá 1 lần. */
    boolean existsByOrderItemId(Integer orderItemId);

    /** Điểm trung bình của sản phẩm (NULL nếu chưa có đánh giá nào). */
    @Query("select avg(r.rating) from ProductReview r where r.product.id = :productId and r.status = :status")
    Double findAverageRating(@Param("productId") Integer productId, @Param("status") ReviewStatus status);

    /**
     * Đánh giá đang hiển thị, kèm sẵn NGƯỜI VIẾT (join fetch) vì dự án đặt
     * spring.jpa.open-in-view = false nên template không thể lazy-load tên khách.
     */
    @Query("""
            select r from ProductReview r join fetch r.user
             where r.product.id = :productId and r.status = :status
             order by r.createdAt desc, r.id desc
            """)
    Page<ProductReview> findVisibleWithUser(@Param("productId") Integer productId,
                                            @Param("status") ReviewStatus status,
                                            Pageable pageable);

    /** Số đánh giá đang hiển thị của sản phẩm. */
    long countByProductIdAndStatus(Integer productId, ReviewStatus status);

    /** Toàn bộ đánh giá của một sản phẩm (mọi trạng thái) – dùng khi cần dọn/gỡ đánh giá. */
    List<ProductReview> findByProductId(Integer productId);
}
