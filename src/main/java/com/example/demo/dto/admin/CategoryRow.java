package com.example.demo.dto.admin;

import com.example.demo.entity.Category;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Một dòng trong bảng quản lý loại vợt: loại + số sản phẩm đang dùng. */
@Getter
@RequiredArgsConstructor
public class CategoryRow {

    private final Category category;
    private final long productCount;
}
