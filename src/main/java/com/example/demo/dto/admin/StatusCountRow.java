package com.example.demo.dto.admin;

import com.example.demo.enums.OrderStatus;

/** Số đơn theo từng trạng thái – dùng vẽ biểu đồ tròn ở dashboard. */
public record StatusCountRow(OrderStatus orderStatus, Long total) {
}
