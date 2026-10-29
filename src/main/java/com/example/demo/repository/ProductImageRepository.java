package com.example.demo.repository;

import com.example.demo.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn bộ ảnh của sản phẩm. */
public interface ProductImageRepository extends JpaRepository<ProductImage, Integer> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Integer productId);

    Optional<ProductImage> findFirstByProductIdAndPrimaryImageTrue(Integer productId);

    /**
     * Lấy ảnh của CẢ TRANG danh sách trong 1 truy vấn (thay vì hỏi từng sản phẩm).
     * Thứ tự: ảnh đại diện (is_primary = 1) trước, rồi theo sort_order ⇒ Service chỉ cần lấy
     * bản ghi đầu tiên của mỗi sản phẩm là ra ảnh hiển thị trên thẻ.
     */
    List<ProductImage> findByProductIdInOrderByPrimaryImageDescSortOrderAscIdAsc(Collection<Integer> productIds);

    /** Ảnh phải thuộc đúng sản phẩm đang thao tác – tránh admin sửa ảnh của sản phẩm khác qua URL. */
    Optional<ProductImage> findByIdAndProductId(Integer id, Integer productId);

    /** Kiểm tra file upload còn được bộ ảnh sản phẩm dùng không, trước khi xoá file của biến thể. */
    boolean existsByImageUrl(String imageUrl);

    void deleteByProductId(Integer productId);
}
