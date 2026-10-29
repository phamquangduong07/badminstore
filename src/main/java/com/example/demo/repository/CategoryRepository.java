package com.example.demo.repository;

import com.example.demo.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn loại vợt – luôn lọc is_deleted = 0. */
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByDeletedFalseOrderByNameAsc();

    Optional<Category> findByIdAndDeletedFalse(Integer id);

    /** Danh sách loại vợt cho trang quản trị: tìm theo tên/mô tả. */
    @Query("""
            select c from Category c
            where c.deleted = false
              and (:keyword is null
                   or lower(c.name) like lower(concat('%', :keyword, '%'))
                   or lower(c.description) like lower(concat('%', :keyword, '%')))
            order by c.name asc, c.id asc
            """)
    List<Category> search(@Param("keyword") String keyword);

    /** Kiểm tra trùng tên chỉ trong các loại CHƯA xoá mềm (khớp UX_categories_name có WHERE is_deleted = 0). */
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Integer id);
}
