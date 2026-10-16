package com.example.demo.repository;

import com.example.demo.entity.Brand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn hãng sản xuất – luôn lọc is_deleted = 0. */
public interface BrandRepository extends JpaRepository<Brand, Integer> {

    List<Brand> findByDeletedFalseOrderByNameAsc();

    Optional<Brand> findByIdAndDeletedFalse(Integer id);

    boolean existsByName(String name);
}
