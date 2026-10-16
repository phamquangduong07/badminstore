package com.example.demo.config;

import com.example.demo.service.CustomUserDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;

/**
 * Thêm flash "Đăng nhập thành công. Xin chào …" để trang chủ hiển thị thông báo sau khi đăng nhập.
 *
 * LƯU Ý Spring Framework 7: {@code RequestContextUtils.getOutputFlashMap()} / {@code getFlashMapManager()}
 * chỉ ĐỌC request attribute do DispatcherServlet đặt. Ở tầng filter (đăng nhập) các attribute này chưa có,
 * nên phải tự tạo FlashMap rồi gọi thẳng bean {@link FlashMapManager} (sessionFlashMapManager của Spring MVC).
 */
@Component
@RequiredArgsConstructor
public class FlashMessageAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final MessageSource messageSource;
    private final FlashMapManager flashMapManager;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof CustomUserDetails principal) {
            FlashMap flashMap = new FlashMap();
            flashMap.put("successMessage",
                    messageSource.getMessage("msg.login.success", new Object[]{principal.getFullName()}, null));
            // Chỉ hiển thị ở lần vào trang chủ ngay sau khi đăng nhập.
            flashMap.setTargetRequestPath("/");
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
