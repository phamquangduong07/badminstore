package com.example.demo.exception;

/**
 * Lỗi nghiệp vụ đã biết trước (do người dùng thao tác sai) – luôn kèm câu tiếng Việt
 * để hiển thị thẳng cho người dùng qua flash message.
 * Khác với lỗi hệ thống: đây KHÔNG phải bug, không cần stack trace dài dòng.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
