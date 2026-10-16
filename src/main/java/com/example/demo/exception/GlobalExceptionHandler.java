package com.example.demo.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Bắt lỗi ở một chỗ duy nhất và biến thành thông báo tiếng Việt thân thiện.
 * Nguyên tắc: lỗi nghiệp vụ/lỗi ràng buộc DB ⇒ quay lại trang cũ kèm flash đỏ (Post-Redirect-Get),
 * không để người dùng nhìn thấy stack trace.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    /** Lỗi nghiệp vụ (Service ném ra) – hiển thị đúng câu thông báo của Service. */
    @ExceptionHandler(BusinessException.class)
    public RedirectView handleBusinessException(BusinessException ex, HttpServletRequest request,
                                                RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return redirectBack(request);
    }

    /**
     * Dữ liệu cần xem không tồn tại (sản phẩm đã xoá/ngừng bán, đơn hàng của người khác…).
     * Trả về trang 404 kèm lý do tiếng Việt – KHÔNG quay lại trang cũ vì bản thân URL đã sai.
     */
    @ExceptionHandler(NotFoundException.class)
    public ModelAndView handleNotFound(NotFoundException ex) {
        ModelAndView modelAndView = new ModelAndView("error/404");
        modelAndView.addObject("message", ex.getMessage());
        modelAndView.setStatus(HttpStatus.NOT_FOUND);
        return modelAndView;
    }

    /** Vi phạm CHECK/UNIQUE/FK của SQL Server (email trùng, tồn kho âm, sai tổng tiền…). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public RedirectView handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request,
                                            RedirectAttributes redirectAttributes) {
        log.warn("Vi phạm ràng buộc dữ liệu tại {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", message("error.data"));
        return redirectBack(request);
    }

    /** Ảnh tải lên vượt dung lượng cấu hình (spring.servlet.multipart.max-file-size). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public RedirectView handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request,
                                        RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", message("error.upload.size"));
        return redirectBack(request);
    }

    /** Lỗi phân quyền phải để Spring Security xử lý (hiện trang 403) – không nuốt ở đây. */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    /**
     * URL không tồn tại, sai method… (nhóm lỗi HTTP có mã trạng thái) phải ném tiếp để Spring Boot
     * hiển thị đúng trang 404/405. Nếu bắt ở đây sẽ biến 404 thành 200 – lỗi rất khó phát hiện.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public void handleErrorResponse(ErrorResponseException ex) throws ErrorResponseException {
        throw ex;
    }

    /** Lỗi còn lại: ghi log để còn debug, rồi để container hiển thị trang 500 (tránh tự render gây vòng lặp). */
    @ExceptionHandler(Exception.class)
    public void handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        log.error("Lỗi không mong đợi tại {}", request.getRequestURI(), ex);
        throw ex;
    }

    private String message(String key) {
        return messageSource.getMessage(key, null, key, null);
    }

    /** Quay lại đúng trang vừa thao tác; nếu không xác định được thì về trang chủ. */
    private RedirectView redirectBack(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        String target = (referer == null || referer.isBlank()) ? "/" : referer;
        return new RedirectView(target, true, false);
    }
}
