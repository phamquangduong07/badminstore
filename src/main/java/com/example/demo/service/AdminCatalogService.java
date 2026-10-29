package com.example.demo.service;

import com.example.demo.dto.admin.BrandForm;
import com.example.demo.dto.admin.BrandRow;
import com.example.demo.dto.admin.CatalogUsageRow;
import com.example.demo.dto.admin.CategoryForm;
import com.example.demo.dto.admin.CategoryRow;
import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quản trị danh mục nền của sản phẩm: hãng sản xuất (brands) và loại vợt (categories).
 *
 * Quy tắc:
 * – xoá là XOÁ MỀM (is_deleted = 1) để không phá liên kết với sản phẩm/đơn hàng cũ;
 * – không cho xoá hãng/loại đang được sản phẩm CHƯA xoá mềm sử dụng;
 * – tên không trùng trong các bản ghi chưa xoá mềm (khớp unique index có WHERE is_deleted = 0);
 * – logo hãng là tuỳ chọn, tải lên qua FileStorageService (giới hạn dung lượng + kiểm tra MIME).
 */
@Service
@RequiredArgsConstructor
public class AdminCatalogService {

    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;

    // ==================== HÃNG SẢN XUẤT ====================

    @Transactional(readOnly = true)
    public List<BrandRow> searchBrands(String keyword) {
        Map<Integer, Long> usage = usageByBrand();
        return brandRepository.search(normalizeKeyword(keyword)).stream()
                .map(brand -> new BrandRow(brand, usage.getOrDefault(brand.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Brand requireBrand(Integer id) {
        return brandRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hãng sản xuất cần xử lý."));
    }

    @Transactional
    public Brand createBrand(BrandForm form) {
        String name = requireName(form.getName(), "hãng");
        if (brandRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new BusinessException("Hãng \"" + name + "\" đã tồn tại, bạn đặt tên khác nhé.");
        }

        Brand brand = new Brand();
        brand.setDeleted(false);
        brand.setName(name);
        brand.setDescription(trimToNull(form.getDescription()));
        applyLogo(brand, form.getLogoFile());
        return brandRepository.save(brand);
    }

    @Transactional
    public Brand updateBrand(Integer id, BrandForm form) {
        Brand brand = requireBrand(id);
        String name = requireName(form.getName(), "hãng");
        if (brandRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, id)) {
            throw new BusinessException("Hãng \"" + name + "\" đã bị dùng bởi một hãng khác.");
        }

        String oldLogo = brand.getLogo();
        brand.setName(name);
        brand.setDescription(trimToNull(form.getDescription()));

        MultipartFile logoFile = form.getLogoFile();
        if (logoFile != null && !logoFile.isEmpty()) {
            brand.setLogo(fileStorageService.storeImage(logoFile));
        } else if (form.isRemoveLogo()) {
            brand.setLogo(null);
        }

        Brand saved = brandRepository.save(brand);
        deleteReplacedLogo(oldLogo, saved.getLogo());
        return saved;
    }

    /**
     * Xoá mềm hãng. Chặn khi còn sản phẩm chưa xoá đang dùng hãng này – nếu không,
     * trang khách sẽ hiển thị sản phẩm thuộc một hãng đã biến mất khỏi bộ lọc.
     */
    @Transactional
    public Brand deleteBrand(Integer id) {
        Brand brand = requireBrand(id);
        long usedBy = productRepository.countByBrandIdAndDeletedFalse(id);
        if (usedBy > 0) {
            throw new BusinessException("Hãng \"" + brand.getName() + "\" đang được " + usedBy
                    + " sản phẩm sử dụng nên chưa thể xoá. Bạn hãy chuyển các sản phẩm sang hãng khác trước.");
        }
        brand.setDeleted(true);
        Brand saved = brandRepository.save(brand);
        fileStorageService.deleteIfUploaded(saved.getLogo());
        return saved;
    }

    // ==================== LOẠI VỢT ====================

    @Transactional(readOnly = true)
    public List<CategoryRow> searchCategories(String keyword) {
        Map<Integer, Long> usage = usageByCategory();
        return categoryRepository.search(normalizeKeyword(keyword)).stream()
                .map(category -> new CategoryRow(category, usage.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Category requireCategory(Integer id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy loại vợt cần xử lý."));
    }

    @Transactional
    public Category createCategory(CategoryForm form) {
        String name = requireName(form.getName(), "loại vợt");
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new BusinessException("Loại vợt \"" + name + "\" đã tồn tại, bạn đặt tên khác nhé.");
        }

        Category category = new Category();
        category.setDeleted(false);
        category.setName(name);
        category.setDescription(trimToNull(form.getDescription()));
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(Integer id, CategoryForm form) {
        Category category = requireCategory(id);
        String name = requireName(form.getName(), "loại vợt");
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, id)) {
            throw new BusinessException("Loại vợt \"" + name + "\" đã bị dùng bởi một loại khác.");
        }

        category.setName(name);
        category.setDescription(trimToNull(form.getDescription()));
        return categoryRepository.save(category);
    }

    @Transactional
    public Category deleteCategory(Integer id) {
        Category category = requireCategory(id);
        long usedBy = productRepository.countByCategoryIdAndDeletedFalse(id);
        if (usedBy > 0) {
            throw new BusinessException("Loại vợt \"" + category.getName() + "\" đang được " + usedBy
                    + " sản phẩm sử dụng nên chưa thể xoá. Bạn hãy chuyển các sản phẩm sang loại khác trước.");
        }
        category.setDeleted(true);
        return categoryRepository.save(category);
    }

    // ==================== HÀM NỘI BỘ ====================

    private Map<Integer, Long> usageByBrand() {
        return productRepository.countActiveProductsByBrand().stream()
                .collect(Collectors.toMap(CatalogUsageRow::id, CatalogUsageRow::total));
    }

    private Map<Integer, Long> usageByCategory() {
        return productRepository.countActiveProductsByCategory().stream()
                .collect(Collectors.toMap(CatalogUsageRow::id, CatalogUsageRow::total));
    }

    private void applyLogo(Brand brand, MultipartFile logoFile) {
        if (logoFile != null && !logoFile.isEmpty()) {
            brand.setLogo(fileStorageService.storeImage(logoFile));
        }
    }

    private void deleteReplacedLogo(String oldLogo, String newLogo) {
        if (oldLogo != null && !oldLogo.equals(newLogo)) {
            fileStorageService.deleteIfUploaded(oldLogo);
        }
    }

    private String normalizeKeyword(String keyword) {
        return (keyword == null || keyword.isBlank()) ? null : keyword.trim();
    }

    /** Tên bắt buộc 1–100 ký tự; gộp khoảng trắng thừa để tránh hai bản ghi "gần giống nhau". */
    private String requireName(String rawName, String label) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new BusinessException("Vui lòng nhập tên " + label + ".");
        }
        if (name.length() > 100) {
            throw new BusinessException("Tên " + label + " tối đa 100 ký tự.");
        }
        return name;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
