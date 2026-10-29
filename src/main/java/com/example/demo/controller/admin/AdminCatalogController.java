package com.example.demo.controller.admin;

import com.example.demo.dto.admin.BrandForm;
import com.example.demo.dto.admin.CategoryForm;
import com.example.demo.entity.Brand;
import com.example.demo.entity.Category;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AdminCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản trị danh mục nền của sản phẩm:
 * – hãng sản xuất: danh sách/tìm kiếm, thêm, sửa (kèm logo), xoá mềm;
 * – loại vợt (lối chơi): danh sách/tìm kiếm, thêm, sửa, xoá mềm.
 * Mọi thao tác ghi đều theo Post-Redirect-Get + flash message; template có SweetAlert2 xác nhận.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final AdminCatalogService adminCatalogService;

    // ==================== HÃNG SẢN XUẤT ====================

    @GetMapping("/thuong-hieu")
    public String brandList(@RequestParam(name = "keyword", required = false) String keyword, Model model) {
        model.addAttribute("brands", adminCatalogService.searchBrands(keyword));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("adminMenu", "brands");
        return "admin/brands";
    }

    @GetMapping("/thuong-hieu/tao-moi")
    public String brandCreateForm(Model model) {
        prepareBrandForm(model, new BrandForm(), null);
        return "admin/brand-form";
    }

    @PostMapping("/thuong-hieu")
    public String brandCreate(@Valid @ModelAttribute("form") BrandForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareBrandForm(model, form, null);
            return "admin/brand-form";
        }
        try {
            Brand brand = adminCatalogService.createBrand(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm hãng \"" + brand.getName() + "\".");
            return "redirect:/admin/thuong-hieu";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareBrandForm(model, form, null);
            return "admin/brand-form";
        }
    }

    @GetMapping("/thuong-hieu/{id}/sua")
    public String brandEditForm(@PathVariable("id") Integer id, Model model) {
        Brand brand = adminCatalogService.requireBrand(id);
        BrandForm form = new BrandForm();
        form.setName(brand.getName());
        form.setDescription(brand.getDescription());
        prepareBrandForm(model, form, id);
        model.addAttribute("currentLogo", brand.getLogo());
        return "admin/brand-form";
    }

    @PostMapping("/thuong-hieu/{id}")
    public String brandUpdate(@PathVariable("id") Integer id,
                              @Valid @ModelAttribute("form") BrandForm form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareBrandForm(model, form, id);
            model.addAttribute("currentLogo", adminCatalogService.requireBrand(id).getLogo());
            return "admin/brand-form";
        }
        try {
            Brand brand = adminCatalogService.updateBrand(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật hãng \"" + brand.getName() + "\".");
            return "redirect:/admin/thuong-hieu";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareBrandForm(model, form, id);
            model.addAttribute("currentLogo", adminCatalogService.requireBrand(id).getLogo());
            return "admin/brand-form";
        }
    }

    @PostMapping("/thuong-hieu/{id}/xoa")
    public String brandDelete(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        try {
            Brand brand = adminCatalogService.deleteBrand(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xoá hãng \"" + brand.getName() + "\" khỏi danh mục.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/thuong-hieu";
    }

    // ==================== LOẠI VỢT ====================

    @GetMapping("/loai-vot")
    public String categoryList(@RequestParam(name = "keyword", required = false) String keyword, Model model) {
        model.addAttribute("categories", adminCatalogService.searchCategories(keyword));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("adminMenu", "categories");
        return "admin/categories";
    }

    @GetMapping("/loai-vot/tao-moi")
    public String categoryCreateForm(Model model) {
        prepareCategoryForm(model, new CategoryForm(), null);
        return "admin/category-form";
    }

    @PostMapping("/loai-vot")
    public String categoryCreate(@Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareCategoryForm(model, form, null);
            return "admin/category-form";
        }
        try {
            Category category = adminCatalogService.createCategory(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm loại vợt \"" + category.getName() + "\".");
            return "redirect:/admin/loai-vot";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareCategoryForm(model, form, null);
            return "admin/category-form";
        }
    }

    @GetMapping("/loai-vot/{id}/sua")
    public String categoryEditForm(@PathVariable("id") Integer id, Model model) {
        Category category = adminCatalogService.requireCategory(id);
        CategoryForm form = new CategoryForm();
        form.setName(category.getName());
        form.setDescription(category.getDescription());
        prepareCategoryForm(model, form, id);
        return "admin/category-form";
    }

    @PostMapping("/loai-vot/{id}")
    public String categoryUpdate(@PathVariable("id") Integer id,
                                 @Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult bindingResult,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareCategoryForm(model, form, id);
            return "admin/category-form";
        }
        try {
            Category category = adminCatalogService.updateCategory(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật loại vợt \"" + category.getName() + "\".");
            return "redirect:/admin/loai-vot";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareCategoryForm(model, form, id);
            return "admin/category-form";
        }
    }

    @PostMapping("/loai-vot/{id}/xoa")
    public String categoryDelete(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        try {
            Category category = adminCatalogService.deleteCategory(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã xoá loại vợt \"" + category.getName() + "\" khỏi danh mục.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/loai-vot";
    }

    // ==================== DỮ LIỆU CHO FORM ====================

    private void prepareBrandForm(Model model, BrandForm form, Integer brandId) {
        model.addAttribute("form", form);
        model.addAttribute("brandId", brandId);
        model.addAttribute("adminMenu", "brands");
    }

    private void prepareCategoryForm(Model model, CategoryForm form, Integer categoryId) {
        model.addAttribute("form", form);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("adminMenu", "categories");
    }
}
