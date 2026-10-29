package com.example.demo.dto.admin;

/**
 * Số sản phẩm (chưa xoá mềm) đang dùng một hãng/loại.
 * Truy vấn gộp cả bảng một lần để trang quản lý hãng/loại không bị N+1.
 */
public record CatalogUsageRow(Integer id, Long total) {
}
