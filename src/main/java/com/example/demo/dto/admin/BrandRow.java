package com.example.demo.dto.admin;

import com.example.demo.entity.Brand;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Một dòng trong bảng quản lý hãng: hãng + số sản phẩm đang dùng. */
@Getter
@RequiredArgsConstructor
public class BrandRow {

    private final Brand brand;
    private final long productCount;
}
