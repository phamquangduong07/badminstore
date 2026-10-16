package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Người dùng đang đăng nhập (principal của Spring Security).
 * Chỉ giữ các trường cơ bản – KHÔNG truy cập quan hệ LAZY từ template để tránh lỗi lazy-load.
 * Dùng trong Controller: {@code @AuthenticationPrincipal CustomUserDetails currentUser}.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Integer userId;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final UserRole role;
    private final UserStatus status;

    private CustomUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.role = user.getRole();
        this.status = user.getStatus();
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
