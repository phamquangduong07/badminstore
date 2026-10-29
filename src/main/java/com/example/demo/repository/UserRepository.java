package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn tài khoản người dùng. */
public interface UserRepository extends JpaRepository<User, Integer> {

    /** Cột email có collation Vietnamese_100_CI_AS nên tìm không phân biệt hoa/thường. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Integer id);

    /**
     * Danh sách tài khoản cho trang quản trị: lọc theo vai trò/trạng thái và tìm theo
     * họ tên, email hoặc số điện thoại. Sắp xếp tài khoản mới tạo lên trước.
     */
    @Query(value = """
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:keyword is null
                   or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                   or lower(u.email) like lower(concat('%', :keyword, '%'))
                   or u.phone like concat('%', :keyword, '%'))
            order by u.createdAt desc, u.id desc
            """,
            countQuery = """
            select count(u) from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:keyword is null
                   or lower(u.fullName) like lower(concat('%', :keyword, '%'))
                   or lower(u.email) like lower(concat('%', :keyword, '%'))
                   or u.phone like concat('%', :keyword, '%'))
            """)
    Page<User> searchForAdmin(@Param("role") UserRole role,
                              @Param("status") UserStatus status,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    List<User> findByRoleOrderByCreatedAtDesc(UserRole role);

    /** Tra khách quen theo số điện thoại (màn bán hàng tại quầy + gắn đơn với tài khoản khách). */
    Optional<User> findFirstByPhoneOrderByIdAsc(String phone);

    /** Số tài khoản theo vai trò (dashboard admin). */
    long countByRole(UserRole role);

    /** Đếm ADMIN đang hoạt động – dùng để chặn thao tác làm mất quản trị viên cuối cùng. */
    long countByRoleAndStatus(UserRole role, UserStatus status);
}
