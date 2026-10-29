package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.admin.BrandForm;
import com.example.demo.dto.admin.BrandRow;
import com.example.demo.dto.admin.CatalogUsageRow;
import com.example.demo.dto.admin.CategoryForm;
import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BrandRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Kiểm thử nghiệp vụ quản trị hãng/loại vợt:
 * trùng tên, xoá mềm, chặn xoá khi còn sản phẩm dùng và xử lý logo upload.
 */
@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private AdminCatalogService adminCatalogService;

    @Test
    @DisplayName("Danh sách hãng: gắn đúng số sản phẩm đang dùng, hãng chưa dùng hiện 0")
    void searchBrands_attachesUsageCount() {
        Brand used = brand(1, "Yonex");
        Brand unused = brand(2, "Victor");
        when(brandRepository.search("yo")).thenReturn(List.of(used, unused));
        when(productRepository.countActiveProductsByBrand())
                .thenReturn(List.of(new CatalogUsageRow(1, 4L)));

        List<BrandRow> rows = adminCatalogService.searchBrands("yo");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getBrand().getName()).isEqualTo("Yonex");
        assertThat(rows.get(0).getProductCount()).isEqualTo(4);
        assertThat(rows.get(1).getProductCount()).isZero();
    }

    @Test
    @DisplayName("Thêm hãng: tên trùng (không phân biệt hoa thường) bị chặn")
    void createBrand_rejectsDuplicateName() {
        BrandForm form = brandForm("  yonex ");
        when(brandRepository.existsByNameIgnoreCaseAndDeletedFalse("yonex")).thenReturn(true);

        assertThatThrownBy(() -> adminCatalogService.createBrand(form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");

        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thêm hãng: tên được trim, logo upload được lưu thành URL /uploads")
    void createBrand_storesUploadedLogo() {
        BrandForm form = brandForm(" Yonex ");
        when(brandRepository.existsByNameIgnoreCaseAndDeletedFalse("Yonex")).thenReturn(false);
        when(fileStorageService.storeImage(any())).thenReturn("/uploads/logo.png");
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        form.setLogoFile(upload("logo.png"));

        Brand created = adminCatalogService.createBrand(form);

        assertThat(created.getName()).isEqualTo("Yonex");
        assertThat(created.getLogo()).isEqualTo("/uploads/logo.png");
        assertThat(created.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Xoá hãng đang có sản phẩm dùng ⇒ chặn và hướng dẫn chuyển sản phẩm sang hãng khác")
    void deleteBrand_blockedWhenProductsUseIt() {
        Brand brand = brand(1, "Yonex");
        when(brandRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(brand));
        when(productRepository.countByBrandIdAndDeletedFalse(1)).thenReturn(2L);

        assertThatThrownBy(() -> adminCatalogService.deleteBrand(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang được 2 sản phẩm sử dụng");

        assertThat(brand.isDeleted()).isFalse();
        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("Xoá hãng chưa có sản phẩm: chỉ bật is_deleted và xoá file logo upload")
    void deleteBrand_softDeletesAndRemovesLogo() {
        Brand brand = brand(1, "Yonex");
        brand.setLogo("/uploads/logo.png");
        when(brandRepository.findByIdAndDeletedFalse(1)).thenReturn(Optional.of(brand));
        when(productRepository.countByBrandIdAndDeletedFalse(1)).thenReturn(0L);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Brand deleted = adminCatalogService.deleteBrand(1);

        assertThat(deleted.isDeleted()).isTrue();
        verify(brandRepository, never()).delete(any(Brand.class));
        verify(fileStorageService).deleteIfUploaded("/uploads/logo.png");
    }

    @Test
    @DisplayName("Cập nhật loại vợt: tên trùng với loại khác bị chặn")
    void updateCategory_rejectsDuplicateName() {
        Category category = category(2, "Vợt tấn công");
        when(categoryRepository.findByIdAndDeletedFalse(2)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Vợt phòng thủ", 2))
                .thenReturn(true);

        CategoryForm form = categoryForm("Vợt phòng thủ");

        assertThatThrownBy(() -> adminCatalogService.updateCategory(2, form))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã bị dùng bởi một loại khác");

        verify(categoryRepository, never()).save(any());
    }

    // ==================== DỮ LIỆU GIẢ ====================

    private BrandForm brandForm(String name) {
        BrandForm form = new BrandForm();
        form.setName(name);
        form.setDescription("Mô tả hãng");
        return form;
    }

    private CategoryForm categoryForm(String name) {
        CategoryForm form = new CategoryForm();
        form.setName(name);
        form.setDescription("Mô tả loại");
        return form;
    }

    private MockMultipartFile upload(String fileName) {
        return new MockMultipartFile("logoFile", fileName, "image/png",
                "PNG-GIA-LAP".getBytes(StandardCharsets.UTF_8));
    }

    private Brand brand(Integer id, String name) {
        Brand brand = new Brand();
        brand.setId(id);
        brand.setName(name);
        return brand;
    }

    private Category category(Integer id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        return category;
    }
}
