package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật:
 * – trang khách xem tự do, mọi thao tác khác phải đăng nhập;
 * – /admin/** chỉ ADMIN;
 * – CSRF bật (Thymeleaf tự chèn token vào form nên không cần tắt);
 * – mật khẩu băm bằng BCrypt; đăng nhập/đăng xuất bằng email.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Các đường dẫn cho phép truy cập không cần đăng nhập. */
    private static final String[] PUBLIC_URLS = {
            "/", "/dang-nhap", "/dang-ky",
            "/san-pham/**", "/tim-kiem", "/so-sanh/**",
            "/css/**", "/js/**", "/images/**", "/uploads/**", "/webjars/**",
            "/favicon.ico", "/error/**"
    };

    private final FlashMessageAuthenticationSuccessHandler authenticationSuccessHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/dang-nhap")
                        .loginProcessingUrl("/dang-nhap")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(authenticationSuccessHandler)
                        .failureUrl("/dang-nhap?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/dang-xuat")
                        .logoutSuccessUrl("/dang-nhap?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
