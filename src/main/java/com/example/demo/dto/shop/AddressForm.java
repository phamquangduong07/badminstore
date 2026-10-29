package com.example.demo.dto.shop;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Form thêm/sửa địa chỉ giao hàng (sổ địa chỉ + bước thanh toán).
 * Validate 2 lớp: HTML (required/pattern trong template) và Bean Validation ở đây;
 * lỗi hiện cạnh từng ô bằng class is-invalid.
 */
@Getter
@Setter
public class AddressForm {

    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự.")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận.")
    @Pattern(regexp = "0\\d{9,10}", message = "Số điện thoại phải bắt đầu bằng 0 và gồm 10–11 chữ số.")
    private String receiverPhone;

    @NotBlank(message = "Vui lòng nhập tỉnh/thành phố.")
    @Size(max = 100, message = "Tỉnh/thành phố tối đa 100 ký tự.")
    private String provinceName;

    @NotBlank(message = "Vui lòng nhập quận/huyện.")
    @Size(max = 100, message = "Quận/huyện tối đa 100 ký tự.")
    private String districtName;

    @NotBlank(message = "Vui lòng nhập phường/xã.")
    @Size(max = 100, message = "Phường/xã tối đa 100 ký tự.")
    private String wardName;

    @NotBlank(message = "Vui lòng nhập số nhà, tên đường.")
    @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự.")
    private String addressDetail;

    /** Đặt làm địa chỉ mặc định? (địa chỉ ĐẦU TIÊN luôn là mặc định) */
    private boolean defaultAddress;

    /**
     * Mã địa chỉ do GHN cấp: Tỉnh/Huyện/Xã trên form được chọn trực tiếp từ dữ liệu GHN nên
     * 3 mã này BẮT BUỘC khi GHN đang dùng được ({@code AddressController} kiểm tra trước khi lưu).
     * Dùng để tính phí vận chuyển và đẩy đơn sang GHN; server luôn kiểm tra lại mã (tồn tại thật)
     * và tên địa danh (khớp mã) nên khách không thể sửa mã để ăn phí ship rẻ hơn.
     */
    private Integer provinceId;

    private Integer districtId;

    private String wardCode;
}
