package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn tài khoản người dùng. */
public interface UserRepository extends JpaRepository<User, Integer> {

    /** Cột email có collation Vietnamese_100_CI_AS nên tìm không phân biệt hoa/thường. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    List<User> findByRoleOrderByCreatedAtDesc(UserRole role);

    /** Tra khách quen theo số điện thoại (màn bán hàng tại quầy + gắn đơn với tài khoản khách). */
    Optional<User> findFirstByPhoneOrderByIdAsc(String phone);

    /** Số tài khoản theo vai trò (dashboard admin). */
    long countByRole(UserRole role);
}
