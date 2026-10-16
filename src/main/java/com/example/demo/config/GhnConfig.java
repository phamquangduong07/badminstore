package com.example.demo.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kích hoạt cấu hình GHN và bộ lập lịch (dùng cho việc tự động đồng bộ trạng thái vận đơn).
 * Bản thân lịch đồng bộ chỉ chạy khi `app.ghn.sync-enabled: true`, mặc định tắt.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(GhnProperties.class)
public class GhnConfig {
}
