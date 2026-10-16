package com.example.demo.service;

import com.example.demo.exception.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lưu ảnh sản phẩm do admin tải lên.
 *
 * Quy tắc bảo mật (mức Khá): giới hạn dung lượng, kiểm tra phần mở rộng VÀ MIME, KHÔNG dùng tên file gốc
 * (đổi thành UUID) nên không thể ghi đè hay chèn đường dẫn lạ vào thư mục upload.
 * Trả về URL công khai "/uploads/&lt;tên-file&gt;" để ghi vào product_images.image_url.
 */
@Service
public class FileStorageService {

    /** Dung lượng tối đa cho 1 ảnh (2 MB). */
    public static final long MAX_IMAGE_SIZE = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif", "svg");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/svg+xml");
    private static final String PUBLIC_PREFIX = "/uploads/";

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /** Thư mục gốc chứa ảnh – WebConfig dùng để map ra URL /uploads/**. */
    public Path getUploadDir() {
        return uploadDir;
    }

    /** Lưu file và trả về URL công khai. Mọi lỗi đều là BusinessException có thông báo tiếng Việt. */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Vui lòng chọn file ảnh để tải lên.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("Ảnh tối đa 2 MB. Bạn hãy chọn ảnh nhỏ hơn hoặc nén lại trước khi tải.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Chỉ nhận ảnh định dạng JPG, PNG, WEBP, GIF hoặc SVG.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("File bạn chọn không phải là ảnh hợp lệ.");
        }

        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(fileName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BusinessException("Tên file không hợp lệ.");
            }
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BusinessException("Không lưu được ảnh lên máy chủ, bạn thử lại sau nhé.");
        }
        return PUBLIC_PREFIX + fileName;
    }

    /** Xoá file đã lưu khi admin gỡ ảnh. Bỏ qua URL ngoài hệ thống (ảnh seed dùng /images/**). */
    public void deleteIfUploaded(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith(PUBLIC_PREFIX)) {
            return;
        }
        Path target = uploadDir.resolve(imageUrl.substring(PUBLIC_PREFIX.length())).normalize();
        if (!target.startsWith(uploadDir)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // File đang bị khoá/đã bị xoá tay: không chặn nghiệp vụ, chỉ cần bản ghi CSDL biến mất.
        }
    }

    private String extractExtension(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
