package com.example.demo.repository;

import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.VariantStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn biến thể – nơi giữ giá bán và tồn kho. */
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Integer> {

    List<ProductVariant> findByProductIdOrderByColorAscWeightClassAscGripSizeAsc(Integer productId);

    /** Trang chi tiết của khách chỉ hiển thị biến thể đang bán. */
    List<ProductVariant> findByProductIdAndStatusOrderByColorAscWeightClassAscGripSizeAsc(Integer productId,
                                                                                         VariantStatus status);

    Optional<ProductVariant> findByIdAndStatus(Integer id, VariantStatus status);

    Optional<ProductVariant> findBySku(String sku);

    /** Tra biến thể theo SKU kèm sẵn sản phẩm (màn bán tại quầy) – tránh nạp LAZY từng dòng. */
    @Query("select v from ProductVariant v join fetch v.product p where v.sku = :sku")
    Optional<ProductVariant> findBySkuWithProduct(@Param("sku") String sku);

    /** Giá thấp nhất của các biến thể đang bán – dùng hiển thị "giá từ" ở trang danh sách. */
    Optional<ProductVariant> findFirstByProductIdAndStatusOrderByPriceAsc(Integer productId, VariantStatus status);

    /**
     * TRỪ KHO khi xác nhận đơn – câu UPDATE có điều kiện nên chống bán quá tồn kho:
     * trả về 0 dòng nghĩa là tồn kho không đủ ⇒ Service ném lỗi để rollback cả đơn.
     * Điều kiện {@code stock_quantity >= :qty} nằm trong câu lệnh nên không bị race condition.
     */
    @Modifying
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity - :quantity, v.updatedAt = CURRENT_TIMESTAMP "
            + "where v.id = :id and v.stockQuantity >= :quantity")
    int deductStock(@Param("id") Integer id, @Param("quantity") int quantity);

    /** CỘNG LẠI KHO khi huỷ đơn / hoàn trả. */
    @Modifying
    @Query("update ProductVariant v set v.stockQuantity = v.stockQuantity + :quantity, v.updatedAt = CURRENT_TIMESTAMP "
            + "where v.id = :id")
    int restoreStock(@Param("id") Integer id, @Param("quantity") int quantity);

    /** Đếm biến thể theo trạng thái (dashboard admin). */
    long countByStatus(VariantStatus status);

    /** Cảnh báo tồn kho thấp – nạp kèm sản phẩm để hiển thị tên trên dashboard. */
    @Query("""
            select v from ProductVariant v join fetch v.product p
            where v.status = :status and v.stockQuantity <= :threshold
            order by v.stockQuantity asc, v.id asc
            """)
    List<ProductVariant> findLowStock(@Param("status") VariantStatus status, @Param("threshold") int threshold,
                                      Pageable pageable);

    /** Toàn bộ biến thể đang bán kèm tên sản phẩm – dùng gợi ý SKU khi admin tạo đơn thủ công. */
    @Query("""
            select v from ProductVariant v join fetch v.product p
            where v.status = com.example.demo.enums.VariantStatus.ACTIVE
            order by p.name asc, v.sku asc
            """)
    List<ProductVariant> findActiveWithProduct();

    /**
     * Tìm biến thể đang bán cho màn hình BÁN HÀNG TẠI QUẦY (POS).
     * Từ khoá khớp SKU hoặc tên sản phẩm; chỉ lấy sản phẩm/biến thể còn bán, chưa xoá mềm.
     */
    @Query("""
            select v from ProductVariant v join fetch v.product p
            where v.status = com.example.demo.enums.VariantStatus.ACTIVE
                and p.status = com.example.demo.enums.ProductStatus.ACTIVE
                and p.deleted = false
                and (lower(v.sku) like lower(concat('%', :keyword, '%'))
                     or lower(p.name) like lower(concat('%', :keyword, '%')))
            order by p.name asc, v.price asc, v.id asc
            """)
    List<ProductVariant> searchActiveForSale(@Param("keyword") String keyword, Pageable pageable);

    /** Biến thể của cả một trang sản phẩm trong 1 truy vấn (cho danh sách quản trị). */
    List<ProductVariant> findByProductIdIn(Collection<Integer> productIds);

    Optional<ProductVariant> findByIdAndProductId(Integer id, Integer productId);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Integer id);

    /** Khớp ràng buộc UQ_product_variant (product_id, color, weight_class, grip_size) để báo lỗi sớm. */
    boolean existsByProductIdAndColorAndWeightClassAndGripSize(Integer productId, String color, String weightClass,
                                                               String gripSize);

    boolean existsByProductIdAndColorAndWeightClassAndGripSizeAndIdNot(Integer productId, String color,
                                                                       String weightClass, String gripSize,
                                                                       Integer id);
}
