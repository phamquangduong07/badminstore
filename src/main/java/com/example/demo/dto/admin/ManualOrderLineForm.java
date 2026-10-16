package com.example.demo.dto.admin;

import lombok.Getter;
import lombok.Setter;

/** Một dòng hàng của đơn thủ công: SKU của biến thể + số lượng. */
@Getter
@Setter
public class ManualOrderLineForm {

    private String sku;

    private Integer quantity = 1;
}
