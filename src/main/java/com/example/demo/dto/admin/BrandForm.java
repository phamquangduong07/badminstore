package com.example.demo.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Form admin thêm/sửa hãng sản xuất.
 * Logo là tuỳ chọn: admin có thể tải ảnh mới hoặc gỡ logo hiện tại.
 */
@Getter
@Setter
public class BrandForm {

    @NotBlank(message = "Vui lòng nhập tên hãng.")
    @Size(max = 100, message = "Tên hãng tối đa 100 ký tự.")
    private String name;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự.")
    private String description;

    /** Ảnh logo mới (JPG, PNG, WEBP, GIF, SVG – tối đa 2 MB). Bỏ trống để giữ logo hiện tại. */
    private MultipartFile logoFile;

    /** Khi sửa: tích để gỡ logo hiện tại (nếu không chọn ảnh mới). */
    private boolean removeLogo;
}
