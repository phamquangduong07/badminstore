package com.example.demo.dto.ghn;

/**
 * Vỏ phản hồi chung của API GHN v2: {"code":200,"message":"Success","data":...}.
 * `code` khác 200 nghĩa là GHN từ chối (hết hạn token, sai shopId, thiếu tham số...).
 */
public record GhnResponse<T>(int code, String message, T data) {

    public boolean success() {
        return code == 200;
    }

    /** Lấy dữ liệu, nếu thất bại thì ném lỗi nghiệp vụ với thông báo GHN trả về. */
    public T requireData() {
        if (!success() || data == null) {
            throw new GhnApiException("GHN trả về lỗi " + code + ": " + (message == null ? "không rõ" : message));
        }
        return data;
    }
}
