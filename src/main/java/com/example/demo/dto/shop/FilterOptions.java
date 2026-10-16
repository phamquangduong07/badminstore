package com.example.demo.dto.shop;

import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import java.util.List;

/** Dữ liệu đổ vào 2 ô chọn "Hãng" và "Loại vợt" của bộ lọc (chỉ lấy bản ghi chưa xoá mềm). */
public record FilterOptions(List<Brand> brands, List<Category> categories) {
}
