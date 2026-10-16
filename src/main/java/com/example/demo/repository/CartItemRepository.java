package com.example.demo.repository;

import com.example.demo.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn giỏ hàng – giỏ gắn với tài khoản, mỗi biến thể chỉ có 1 dòng (UQ_cart_items_user_variant). */
public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<CartItem> findByUserIdAndProductVariantId(Integer userId, Integer productVariantId);

    long countByUserId(Integer userId);

    void deleteByUserId(Integer userId);

    /**
     * Giỏ hàng đầy đủ của 1 khách: fetch join luôn biến thể + sản phẩm để trang giỏ hàng
     * không phát sinh N+1 (dự án đặt open-in-view = false nên template không đọc được quan hệ LAZY).
     */
    @Query("""
            select ci from CartItem ci
                join fetch ci.productVariant v
                join fetch v.product p
            where ci.user.id = :userId
            order by ci.createdAt desc, ci.id desc
            """)
    List<CartItem> findCartByUserId(@Param("userId") Integer userId);

    /** 1 dòng giỏ hàng kèm kiểm tra chủ sở hữu – dùng cho sửa số lượng/xoá. */
    @Query("""
            select ci from CartItem ci
                join fetch ci.productVariant v
                join fetch v.product p
            where ci.id = :id and ci.user.id = :userId
            """)
    Optional<CartItem> findByIdAndUserId(@Param("id") Integer id, @Param("userId") Integer userId);

    /** Tổng số sản phẩm trong giỏ (hiển thị trên badge ở header). */
    @Query("select coalesce(sum(ci.quantity), 0) from CartItem ci where ci.user.id = :userId")
    Long sumQuantityByUserId(@Param("userId") Integer userId);
}
