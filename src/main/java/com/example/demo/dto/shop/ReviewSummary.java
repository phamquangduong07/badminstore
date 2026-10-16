package com.example.demo.dto.shop;

import java.util.Locale;
import lombok.Getter;

/**
 * Tóm tắt đánh giá của một sản phẩm: điểm trung bình (1–5 sao) và số lượt đánh giá.
 * Mọi chuỗi hiển thị được tính sẵn theo định dạng tiếng Việt để template không chứa logic.
 */
@Getter
public class ReviewSummary {

    private static final Locale VIETNAM = Locale.forLanguageTag("vi-VN");

    private final double average;
    private final long count;

    public ReviewSummary(Double average, long count) {
        // Làm tròn 1 chữ số thập phân ngay tại tầng dữ liệu cho khớp phần hiển thị.
        this.average = average == null ? 0d : Math.round(average * 10d) / 10d;
        this.count = count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    /** Ví dụ "4,5/5". */
    public String getAverageLabel() {
        return String.format(VIETNAM, "%.1f/5", average);
    }

    /** Số ngôi sao tô đậm khi vẽ icon (làm tròn tới hào). */
    public int getFilledStars() {
        return (int) Math.round(average);
    }

    public String getCountLabel() {
        return count == 0 ? "Chưa có đánh giá" : count + " lượt đánh giá";
    }

    /** Một dòng gọn cho thẻ meta/thông báo. */
    public String getSummaryLabel() {
        return count == 0 ? "Chưa có đánh giá" : getAverageLabel() + " – " + count + " lượt đánh giá";
    }
}
