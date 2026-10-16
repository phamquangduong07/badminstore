package com.example.demo.repository;

import com.example.demo.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy vấn thanh toán – mỗi đơn có đúng 1 bản ghi (order_id UNIQUE). */
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    Optional<Payment> findByOrderId(Integer orderId);

    boolean existsByOrderId(Integer orderId);
}
