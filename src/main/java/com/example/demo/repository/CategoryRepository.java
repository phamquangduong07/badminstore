package com.example.demo.repository;

import com.example.demo.entity.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn loại vợt – luôn lọc is_deleted = 0. */
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    List<Category> findByDeletedFalseOrderByNameAsc();

    Optional<Category> findByIdAndDeletedFalse(Integer id);

    boolean existsByName(String name);
}
