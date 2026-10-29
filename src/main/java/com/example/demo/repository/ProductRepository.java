package com.example.demo.repository;

import com.example.demo.dto.admin.CatalogUsageRow;
import com.example.demo.dto.shop.ProductCardView;
import com.example.demo.entity.Product;
import com.example.demo.enums.ProductStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Truy vấn sản phẩm – luôn lọc is_deleted = 0, trang khách chỉ thấy status = ACTIVE.
 */
public interface ProductRepository extends JpaRepository<Product, Integer> {

    /**
     * Truy vấn "1 phát ra cả trang": gộp sản phẩm + hãng + loại + giá thấp nhất + tổng tồn kho
     * trong MỘT câu SQL (left join biến thể + group by) nên không bị N+1.
     *
     * Lọc theo giá dùng EXISTS trên biến thể đang bán: nghĩa là "sản phẩm có ít nhất 1 biến thể trong khoảng giá",
     * đúng với cách khách hiểu bộ lọc giá.
     *
     * Lưu ý SQL Server: cột nào xuất hiện trong ORDER BY (p.createdAt) thì BẮT BUỘC phải có trong GROUP BY,
     * khác MySQL/PostgreSQL – thiếu sẽ báo lỗi 8120 "Column … is invalid in the ORDER BY clause".
     */
    String CARD_SELECT = """
            select new com.example.demo.dto.shop.ProductCardView(
                p.id, p.name, p.productCode, b.name, c.name,
                min(v.price), min(v.originalPrice), sum(v.stockQuantity), count(v.id))
            from Product p
                join p.brand b
                join p.category c
                left join ProductVariant v on v.product = p and v.status = com.example.demo.enums.VariantStatus.ACTIVE
            where p.deleted = false
              and p.status = :status
              and (:keyword is null or lower(p.name) like :keywordPattern)
              and (:brandId is null or b.id = :brandId)
              and (:categoryId is null or c.id = :categoryId)
              and (:minPrice is null or exists (
                    select 1 from ProductVariant pv where pv.product = p
                      and pv.status = com.example.demo.enums.VariantStatus.ACTIVE and pv.price >= :minPrice))
              and (:maxPrice is null or exists (
                    select 1 from ProductVariant pv2 where pv2.product = p
                      and pv2.status = com.example.demo.enums.VariantStatus.ACTIVE and pv2.price <= :maxPrice))
            group by p.id, p.name, p.productCode, p.createdAt, b.name, c.name
            """;

    /** Câu đếm dùng chung cho phân trang (truy vấn gộp ở trên không thể để Spring tự suy ra count). */
    String CARD_COUNT = """
            select count(p.id)
            from Product p
                join p.brand b
                join p.category c
            where p.deleted = false
              and p.status = :status
              and (:keyword is null or lower(p.name) like :keywordPattern)
              and (:brandId is null or b.id = :brandId)
              and (:categoryId is null or c.id = :categoryId)
              and (:minPrice is null or exists (
                    select 1 from ProductVariant pv where pv.product = p
                      and pv.status = com.example.demo.enums.VariantStatus.ACTIVE and pv.price >= :minPrice))
              and (:maxPrice is null or exists (
                    select 1 from ProductVariant pv2 where pv2.product = p
                      and pv2.status = com.example.demo.enums.VariantStatus.ACTIVE and pv2.price <= :maxPrice))
            """;

    /**
     * JPQL không cho truyền tên cột vào ORDER BY, nên sắp xếp được viết bằng CASE:
     * chỉ nhánh ứng với tham số :sort mới trả về giá trị, các nhánh còn lại là NULL (đồng hạng)
     * ⇒ mỗi lần chỉ có đúng một tiêu chí sắp xếp có hiệu lực.
     * `case when min(v.price) is null …` đẩy sản phẩm chưa có biến thể xuống cuối khi sắp theo giá.
     */
    String CARD_ORDER_BY = """
             order by
               case when min(v.price) is null then 1 else 0 end asc,
               case when :sort = 'PRICE_ASC' then min(v.price) end asc,
               case when :sort = 'PRICE_DESC' then min(v.price) end desc,
               case when :sort = 'NAME_ASC' then p.name end asc,
               case when :sort = 'NAME_DESC' then p.name end desc,
               p.createdAt desc,
               p.id desc
            """;

    @Query(value = CARD_SELECT + CARD_ORDER_BY, countQuery = CARD_COUNT)
    Page<ProductCardView> search(@Param("status") ProductStatus status,
                                 @Param("keyword") String keyword,
                                 @Param("keywordPattern") String keywordPattern,
                                 @Param("brandId") Integer brandId,
                                 @Param("categoryId") Integer categoryId,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("sort") String sort,
                                 Pageable pageable);

    /**
     * Trang chi tiết: fetch join luôn brand + category để template đọc được tên hãng/loại
     * (dự án đặt spring.jpa.open-in-view = false nên không thể lazy-load khi render).
     */
    @Query("select p from Product p join fetch p.brand join fetch p.category where p.id = :id and p.deleted = false")
    Optional<Product> findDetailById(@Param("id") Integer id);

    /** Chi tiết nhiều sản phẩm trong 1 truy vấn – dùng cho trang so sánh (tối đa 4 sản phẩm). */
    @Query("""
            select p from Product p join fetch p.brand join fetch p.category
             where p.id in :ids and p.deleted = false
            """)
    List<Product> findDetailByIdIn(@Param("ids") Collection<Integer> ids);

    Optional<Product> findByIdAndDeletedFalse(Integer id);

    List<Product> findByDeletedFalseOrderByCreatedAtDesc();

    long countByDeletedFalseAndStatus(ProductStatus status);

    long countByBrandIdAndDeletedFalse(Integer brandId);

    long countByCategoryIdAndDeletedFalse(Integer categoryId);

    /** Số sản phẩm chưa xoá mềm theo từng hãng – một truy vấn cho cả trang quản lý hãng. */
    @Query("""
            select new com.example.demo.dto.admin.CatalogUsageRow(p.brand.id, count(p.id))
            from Product p
            where p.deleted = false
            group by p.brand.id
            """)
    List<CatalogUsageRow> countActiveProductsByBrand();

    /** Số sản phẩm chưa xoá mềm theo từng loại vợt – một truy vấn cho cả trang quản lý loại. */
    @Query("""
            select new com.example.demo.dto.admin.CatalogUsageRow(p.category.id, count(p.id))
            from Product p
            where p.deleted = false
            group by p.category.id
            """)
    List<CatalogUsageRow> countActiveProductsByCategory();

    boolean existsByProductCode(String productCode);

    boolean existsByProductCodeAndDeletedFalse(String productCode);

    boolean existsByProductCodeAndIdNotAndDeletedFalse(String productCode, Integer id);

    /**
     * Trang quản trị sản phẩm: lọc theo từ khoá/hãng/loại/trạng thái.
     * Fetch join sẵn brand + category vì dự án đặt spring.jpa.open-in-view = false,
     * template không thể lazy-load khi render. Count query viết riêng cho phân trang.
     */
    @Query(value = """
            select p from Product p join fetch p.brand join fetch p.category
             where p.deleted = false
               and (:status is null or p.status = :status)
               and (:brandId is null or p.brand.id = :brandId)
               and (:categoryId is null or p.category.id = :categoryId)
               and (:keyword is null
                    or lower(p.name) like lower(concat('%', :keyword, '%'))
                    or lower(p.productCode) like lower(concat('%', :keyword, '%')))
             order by p.createdAt desc, p.id desc
            """,
            countQuery = """
            select count(p.id) from Product p
             where p.deleted = false
               and (:status is null or p.status = :status)
               and (:brandId is null or p.brand.id = :brandId)
               and (:categoryId is null or p.category.id = :categoryId)
               and (:keyword is null
                    or lower(p.name) like lower(concat('%', :keyword, '%'))
                    or lower(p.productCode) like lower(concat('%', :keyword, '%')))
            """)
    Page<Product> searchForAdmin(@Param("status") ProductStatus status,
                                 @Param("brandId") Integer brandId,
                                 @Param("categoryId") Integer categoryId,
                                 @Param("keyword") String keyword,
                                 Pageable pageable);
}
