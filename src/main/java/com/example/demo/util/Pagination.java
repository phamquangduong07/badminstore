package com.example.demo.util;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Tiện ích phân trang cho các trang quản trị: trả về danh sách số trang (0-based) quanh trang hiện tại
 * để template chỉ việc in ra, không xử lý logic trong view.
 */
public final class Pagination {

    private Pagination() {
    }

    public static List<Integer> pageNumbers(Page<?> result, int window) {
        if (result.getTotalPages() <= 1) {
            return List.of();
        }
        int total = result.getTotalPages();
        int start = Math.max(0, result.getNumber() - window / 2);
        int end = Math.min(total - 1, start + window - 1);
        start = Math.max(0, end - window + 1);

        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
