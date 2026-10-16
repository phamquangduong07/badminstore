package com.example.demo.repository;

import com.example.demo.entity.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn địa chỉ giao hàng – luôn lọc is_deleted = 0. */
public interface UserAddressRepository extends JpaRepository<UserAddress, Integer> {

    List<UserAddress> findByUserIdAndDeletedFalseOrderByDefaultAddressDescCreatedAtDesc(Integer userId);

    Optional<UserAddress> findByIdAndUserIdAndDeletedFalse(Integer id, Integer userId);

    Optional<UserAddress> findFirstByUserIdAndDefaultAddressTrueAndDeletedFalse(Integer userId);

    /**
     * Bỏ cờ mặc định của mọi địa chỉ của khách – phải chạy TRƯỚC khi đặt cờ mới
     * để không vi phạm unique index UX_user_addresses_default (mỗi khách 1 địa chỉ mặc định).
     */
    @Modifying
    @Query("update UserAddress a set a.defaultAddress = false, a.updatedAt = CURRENT_TIMESTAMP "
            + "where a.user.id = :userId and a.defaultAddress = true")
    int clearDefaultAddress(@Param("userId") Integer userId);
}
