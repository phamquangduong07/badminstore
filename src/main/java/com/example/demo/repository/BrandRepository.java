package com.example.demo.repository;

import com.example.demo.entity.Brand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn hãng sản xuất – luôn lọc is_deleted = 0. */
public interface BrandRepository extends JpaRepository<Brand, Integer> {

    List<Brand> findByDeletedFalseOrderByNameAsc();

    Optional<Brand> findByIdAndDeletedFalse(Integer id);

    /**
     * Danh sách hãng cho trang quản trị: tìm theo tên/mô tả.
     * Dùng lower(...) để tìm không phân biệt hoa thường theo cách của ứng dụng
     * (SQL Server mặc định đã không phân biệt, nhưng viết rõ để chạy đúng cả khi đổi collation).
     */
    @Query("""
            select b from Brand b
            where b.deleted = false
              and (:keyword is null
                   or lower(b.name) like lower(concat('%', :keyword, '%'))
                   or lower(b.description) like lower(concat('%', :keyword, '%')))
            order by b.name asc, b.id asc
            """)
    List<Brand> search(@Param("keyword") String keyword);

    /** Kiểm tra trùng tên chỉ trong các hãng CHƯA xoá mềm (khớp UX_brands_name có WHERE is_deleted = 0). */
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Integer id);
}
