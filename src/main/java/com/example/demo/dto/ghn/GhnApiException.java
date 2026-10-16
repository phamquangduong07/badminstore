package com.example.demo.dto.ghn;

/**
 * Lỗi khi gọi API GHN (mạng, token hết hạn, GHN từ chối nghiệp vụ...).
 * Tầng controller bắt lỗi này và hiển thị thông báo tiếng Việt thân thiện cho người dùng.
 */
public class GhnApiException extends RuntimeException {

    public GhnApiException(String message) {
        super(message);
    }

    public GhnApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
