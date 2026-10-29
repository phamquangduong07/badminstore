package com.example.demo.dto.admin;

import com.example.demo.enums.VariantStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Form admin thêm/sửa biến thể (màu × trọng lượng × cỡ cán).
 * Luật CSDL được phản chiếu ở đây: trọng lượng 2U–5U, cỡ cán G3–G6, giá ≥ 0,
 * giá gạch ngang (nếu có) phải ≥ giá bán – đúng CK_variants_original trong BadStore.sql.
 */
@Getter
@Setter
public class VariantForm {

    @NotBlank(message = "Vui lòng nhập SKU.")
    @Size(max = 50, message = "SKU tối đa 50 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "SKU chỉ gồm chữ, số, gạch ngang hoặc gạch dưới.")
    private String sku;

    @NotBlank(message = "Vui lòng nhập màu sắc.")
    @Size(max = 50, message = "Màu sắc tối đa 50 ký tự.")
    private String color;

    @NotBlank(message = "Vui lòng chọn trọng lượng.")
    @Pattern(regexp = "[2-5]U", message = "Trọng lượng chỉ nhận 2U, 3U, 4U hoặc 5U.")
    private String weightClass;

    @NotBlank(message = "Vui lòng chọn cỡ cán.")
    @Pattern(regexp = "G[3-6]", message = "Cỡ cán chỉ nhận G3, G4, G5 hoặc G6.")
    private String gripSize;

    /** Ảnh riêng của biến thể (tuỳ chọn) – dùng cho giỏ hàng và snapshot đơn hàng. */
    @Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự.")
    private String imageUrl;

    /**
     * Ảnh mới tải lên cho biến thể (JPG, PNG, WEBP, GIF, SVG – tối đa 2 MB).
     * Nếu có file, ảnh này được ưu tiên hơn {@link #imageUrl}; bỏ trống để giữ ảnh hiện tại.
     */
    private MultipartFile imageFile;

    /** Khi sửa: tích để gỡ ảnh hiện tại (nếu không chọn ảnh mới). */
    private boolean removeImage;

    @NotNull(message = "Vui lòng nhập giá bán.")
    @DecimalMin(value = "0", message = "Giá bán không được âm.")
    private BigDecimal price;

    /** Giá gạch ngang: bỏ trống nếu không giảm giá. */
    @DecimalMin(value = "0", message = "Giá gạch ngang không được âm.")
    private BigDecimal originalPrice;

    @Min(value = 0, message = "Tồn kho không được âm.")
    private int stockQuantity;

    @NotNull(message = "Vui lòng chọn trạng thái bán.")
    private VariantStatus status = VariantStatus.ACTIVE;
}
