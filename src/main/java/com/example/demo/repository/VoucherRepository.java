package com.example.demo.repository;

import com.example.demo.entity.Voucher;
import com.example.demo.enums.VoucherStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Truy vấn voucher – lượt dùng phải tăng/giảm bằng UPDATE atomic để tránh đặt quá số lượng. */
public interface VoucherRepository extends JpaRepository<Voucher, Integer> {

    Optional<Voucher> findByCode(String code);

    Page<Voucher> findByStatus(VoucherStatus status, Pageable pageable);

    List<Voucher> findAllByOrderByCreatedAtDesc();

    /**
     * Dùng 1 lượt voucher theo kiểu atomic: chỉ thành công khi còn lượt, đang ACTIVE và trong thời gian áp dụng.
     * Trả về 0 dòng = không dùng được (Service kiểm tra và báo lỗi).
     */
    @Modifying
    @Query("""
            update Voucher v
               set v.usedQuantity = v.usedQuantity + 1
             where v.id = :id
               and v.usedQuantity < v.quantity
               and v.status = com.example.demo.enums.VoucherStatus.ACTIVE
               and :now between v.startAt and v.endAt
            """)
    int increaseUsedQuantity(@Param("id") Integer id, @Param("now") LocalDateTime now);

    /** Trả lại 1 lượt khi huỷ đơn / hoàn trả (không giảm xuống dưới 0). */
    @Modifying
    @Query("update Voucher v set v.usedQuantity = v.usedQuantity - 1 where v.id = :id and v.usedQuantity > 0")
    int decreaseUsedQuantity(@Param("id") Integer id);

    /** Danh sách voucher cho admin: lọc theo trạng thái và tìm theo mã/tên. */
    @Query("""
            select v from Voucher v
             where (:status is null or v.status = :status)
               and (:keyword is null
                    or lower(v.code) like lower(concat('%', :keyword, '%'))
                    or lower(v.name) like lower(concat('%', :keyword, '%')))
             order by v.createdAt desc, v.id desc
            """)
    Page<Voucher> search(@Param("status") VoucherStatus status, @Param("keyword") String keyword,
                         Pageable pageable);
}
