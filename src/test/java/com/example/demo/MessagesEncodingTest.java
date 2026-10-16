package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;

/**
 * Kiểm tra file messages.properties được đọc đúng UTF-8 (tiếng Việt có dấu không bị lỗi font).
 * Đây là lỗi rất khó thấy bằng mắt trên UI nên cần test tự động.
 */
@SpringBootTest
class MessagesEncodingTest {

    @Autowired
    private MessageSource messageSource;

    @Test
    @DisplayName("Thông báo đăng nhập thành công giữ đúng dấu tiếng Việt")
    void vietnameseMessagesAreReadAsUtf8() {
        String message = messageSource.getMessage("msg.login.success", new Object[]{"Nguyễn Văn A"}, null);

        assertThat(message).isEqualTo("Đăng nhập thành công. Xin chào Nguyễn Văn A!");
    }

    @Test
    @DisplayName("Thông báo lỗi nghiệp vụ dùng cho GlobalExceptionHandler có nội dung tiếng Việt")
    void errorMessagesExist() {
        assertThat(messageSource.getMessage("error.data", null, null))
                .contains("ràng buộc dữ liệu");
        assertThat(messageSource.getMessage("msg.register.success", null, null))
                .contains("Đăng ký thành công");
    }
}
