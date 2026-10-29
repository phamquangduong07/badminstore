package com.example.demo.service;

import com.example.demo.dto.admin.AdminUserForm;
import com.example.demo.dto.admin.UserOrderStatRow;
import com.example.demo.dto.admin.UserRow;
import com.example.demo.entity.User;
import com.example.demo.enums.UserRole;
import com.example.demo.enums.UserStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản trị tài khoản người dùng.
 *
 * Quy tắc an toàn:
 * – không xoá tài khoản (đơn hàng/địa chỉ/đánh giá còn tham chiếu) ⇒ khoá hoặc chuyển INACTIVE;
 * – admin không thể tự hạ quyền hoặc tự khoá tài khoản đang đăng nhập;
 * – luôn phải còn ít nhất một ADMIN đang ACTIVE để không mất quyền quản trị hệ thống;
 * – mật khẩu luôn băm BCrypt, không lưu hoặc hiển thị mật khẩu thô.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int PAGE_SIZE = 10;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;

    /** Danh sách có lọc + thống kê đơn hàng của cả trang trong 2 truy vấn. */
    @Transactional(readOnly = true)
    public Page<UserRow> search(String keyword, UserRole role, UserStatus status, int pageNumber) {
        Page<User> users = userRepository.searchForAdmin(role, status, normalizeKeyword(keyword),
                PageRequest.of(Math.max(0, pageNumber - 1), PAGE_SIZE));

        List<Integer> userIds = users.getContent().stream().map(User::getId).toList();
        Map<Integer, UserOrderStatRow> stats = userIds.isEmpty()
                ? Map.of()
                : orderRepository.countOrdersByUserIds(userIds).stream()
                        .collect(Collectors.toMap(UserOrderStatRow::userId, row -> row));

        return users.map(user -> {
            UserOrderStatRow stat = stats.get(user.getId());
            return new UserRow(user,
                    stat == null ? 0L : stat.orderCount(),
                    stat == null ? BigDecimal.ZERO : stat.totalSpent());
        });
    }

    @Transactional(readOnly = true)
    public User requireUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tài khoản cần xử lý."));
    }

    @Transactional
    public User create(AdminUserForm form) {
        String email = AuthService.normalizeEmail(form.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("Email " + email + " đã được sử dụng. Vui lòng dùng email khác.");
        }
        String password = form.getPassword();
        if (!hasText(password)) {
            throw new BusinessException("Vui lòng nhập mật khẩu cho tài khoản mới.");
        }
        requireMatchingPassword(password, form.getConfirmPassword());

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        applyProfile(user, form);
        return userRepository.save(user);
    }

    /** Cập nhật thông tin, vai trò, trạng thái; mật khẩu chỉ đổi khi admin có nhập. */
    @Transactional
    public User update(Integer id, AdminUserForm form, Integer currentAdminId) {
        User user = requireUser(id);
        boolean editingSelf = Objects.equals(user.getId(), currentAdminId);

        String email = AuthService.normalizeEmail(form.getEmail());
        if (userRepository.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException("Email " + email + " đã được một tài khoản khác sử dụng.");
        }

        requireAllowedRoleAndStatus(user, form.getRole(), form.getStatus(), editingSelf);

        if (hasText(form.getPassword())) {
            requireMatchingPassword(form.getPassword(), form.getConfirmPassword());
            user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        }

        user.setEmail(email);
        applyProfile(user, form);
        return userRepository.save(user);
    }

    /**
     * Khoá / mở khoá tài khoản. Tài khoản đang INACTIVE khi bị khoá sẽ thành LOCKED;
     * mở khoá luôn đưa về ACTIVE để khách đăng nhập lại được.
     */
    @Transactional
    public User toggleLock(Integer id, Integer currentAdminId) {
        User user = requireUser(id);
        if (Objects.equals(user.getId(), currentAdminId)) {
            throw new BusinessException("Bạn không thể tự khoá tài khoản đang đăng nhập.");
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        } else {
            requireNotLastActiveAdmin(user);
            user.setStatus(UserStatus.LOCKED);
        }
        return userRepository.save(user);
    }

    // ==================== KIỂM TRA NGHIỆP VỤ ====================

    private void requireAllowedRoleAndStatus(User user, UserRole newRole, UserStatus newStatus,
                                             boolean editingSelf) {
        if (editingSelf) {
            if (newRole != UserRole.ADMIN) {
                throw new BusinessException("Bạn không thể tự hạ quyền quản trị của chính mình. "
                        + "Hãy nhờ một quản trị viên khác thực hiện.");
            }
            if (newStatus != UserStatus.ACTIVE) {
                throw new BusinessException("Bạn không thể tự khoá tài khoản đang đăng nhập.");
            }
        }

        boolean removesActiveAdmin = user.getRole() == UserRole.ADMIN
                && user.getStatus() == UserStatus.ACTIVE
                && (newRole != UserRole.ADMIN || newStatus != UserStatus.ACTIVE);
        if (removesActiveAdmin) {
            requireNotLastActiveAdmin(user);
        }
    }

    /** Chặn thao tác làm mất ADMIN đang hoạt động cuối cùng của hệ thống. */
    private void requireNotLastActiveAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE
                && userRepository.countByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BusinessException("Hệ thống phải còn ít nhất một quản trị viên đang hoạt động. "
                    + "Hãy tạo hoặc mở khoá một ADMIN khác trước.");
        }
    }

    private void requireMatchingPassword(String password, String confirmPassword) {
        if (!Objects.equals(password, confirmPassword)) {
            throw new BusinessException("Mật khẩu xác nhận không khớp. Vui lòng nhập lại.");
        }
    }

    private void applyProfile(User user, AdminUserForm form) {
        user.setFullName(requireFullName(form.getFullName()));
        user.setPhone(trimToNull(form.getPhone()));
        user.setRole(form.getRole() == null ? UserRole.CUSTOMER : form.getRole());
        user.setStatus(form.getStatus() == null ? UserStatus.ACTIVE : form.getStatus());
    }

    private String requireFullName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new BusinessException("Vui lòng nhập họ tên người dùng.");
        }
        if (name.length() > 100) {
            throw new BusinessException("Họ tên tối đa 100 ký tự.");
        }
        return name;
    }

    private String normalizeKeyword(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
