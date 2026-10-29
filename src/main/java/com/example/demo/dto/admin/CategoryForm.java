package com.example.demo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Form admin thêm/sửa loại vợt (lối chơi). */
@Getter
@Setter
public class CategoryForm {

    @NotBlank(message = "Vui lòng nhập tên loại vợt.")
    @Size(max = 100, message = "Tên loại vợt tối đa 100 ký tự.")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
    private String description;
}
