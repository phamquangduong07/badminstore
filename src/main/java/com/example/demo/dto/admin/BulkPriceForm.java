package com.example.demo.dto.admin;

import com.example.demo.enums.BulkPriceMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Form cập nhật giá hàng loạt cho biến thể của MỘT sản phẩm.
 * Các ô tích chọn nằm trong bảng biến thể nhưng được gắn vào form này bằng thuộc tính HTML5 `form=`
 * (không lồng form vào nhau vì mỗi dòng đã có form Ngừng bán / Xoá riêng).
 * Luật phụ thuộc kiểu cập nhật (ví dụ % chỉ dùng khi giảm giá) do AdminProductService kiểm tra.
 */
@Getter
@Setter
public class BulkPriceForm {

    /** Id các biến thể được tích chọn. */
    private List<Integer> variantIds = new ArrayList<>();

    @NotNull(message = "Vui lòng chọn kiểu cập nhật giá.")
    private BulkPriceMode mode = BulkPriceMode.DISCOUNT;

    /** Phần trăm giảm giá, chỉ dùng khi mode = DISCOUNT. */
    @DecimalMin(value = "0", message = "Phần trăm giảm giá không được âm.")
    @DecimalMax(value = "90", message = "Phần trăm giảm giá tối đa 90%.")
    private BigDecimal percent = new BigDecimal("10");
}
