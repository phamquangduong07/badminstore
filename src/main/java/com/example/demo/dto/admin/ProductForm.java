package com.example.demo.dto.admin;

import com.example.demo.enums.BalanceType;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.ShaftFlexibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * Form admin thêm/sửa sản phẩm (phần thông số chung; giá và tồn kho nằm ở biến thể).
 * Lỗi Bean Validation hiển thị cạnh từng ô; các luật nghiệp vụ (trùng mã…) do AdminProductService báo.
 */
@Getter
@Setter
public class ProductForm {

    @NotBlank(message = "Vui lòng nhập mã sản phẩm.")
    @Size(max = 30, message = "Mã sản phẩm tối đa 30 ký tự.")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã sản phẩm chỉ gồm chữ, số, gạch ngang hoặc gạch dưới.")
    private String productCode;

    @NotBlank(message = "Vui lòng nhập tên sản phẩm.")
    @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự.")
    private String name;

    @NotNull(message = "Vui lòng chọn hãng sản xuất.")
    private Integer brandId;

    @NotNull(message = "Vui lòng chọn loại (lối chơi).")
    private Integer categoryId;

    @Size(max = 4000, message = "Mô tả tối đa 4000 ký tự.")
    private String description;

    @Min(value = 1, message = "Chiều dài vợt phải lớn hơn 0.")
    @Max(value = 1000, message = "Chiều dài vợt không hợp lệ (tối đa 1000 mm).")
    private Integer lengthMm;

    @Min(value = 1, message = "Điểm cân bằng phải lớn hơn 0.")
    @Max(value = 1000, message = "Điểm cân bằng không hợp lệ (tối đa 1000 mm).")
    private Integer balancePointMm;

    private BalanceType balanceType;

    private ShaftFlexibility shaftFlexibility;

    @DecimalMin(value = "0", message = "Mức căng tối đa không được âm.")
    @DecimalMax(value = "40", message = "Mức căng tối đa không hợp lệ (tối đa 40 kg).")
    private BigDecimal maxTensionKg;

    @Size(max = 150, message = "Chất liệu khung tối đa 150 ký tự.")
    private String frameMaterial;

    @Size(max = 150, message = "Chất liệu thân vợt tối đa 150 ký tự.")
    private String shaftMaterial;

    @NotNull(message = "Vui lòng chọn trạng thái hiển thị.")
    private ProductStatus status = ProductStatus.ACTIVE;
}
