package com.example.demo.exception;

/**
 * Không tìm thấy dữ liệu để hiển thị (sản phẩm đã xoá/ngừng bán, đơn hàng không thuộc về khách…).
 * Khác với BusinessException: lỗi này trả về trang 404 kèm thông báo tiếng Việt,
 * KHÔNG quay lại trang cũ bằng flash vì bản thân URL là sai.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
