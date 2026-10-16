package com.example.demo.controller.admin;

import com.example.demo.dto.admin.BulkPriceForm;
import com.example.demo.dto.admin.ProductForm;
import com.example.demo.dto.admin.ProductRow;
import com.example.demo.dto.admin.VariantForm;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductImage;
import com.example.demo.entity.ProductVariant;
import com.example.demo.enums.BalanceType;
import com.example.demo.enums.BulkPriceMode;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.ShaftFlexibility;
import com.example.demo.enums.VariantStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.service.AdminProductService;
import com.example.demo.util.Pagination;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Quản trị danh mục bán hàng:
 * – sản phẩm: danh sách có lọc, thêm/sửa, ẩn-hiện, xoá mềm;
 * – biến thể: thêm/sửa giá – tồn kho, ngừng bán, xoá (nếu chưa vào đơn nào);
 * – ảnh: tải lên (kiểm tra định dạng/dung lượng), đặt ảnh chính, gỡ ảnh.
 */
@Controller
@RequestMapping("/admin/san-pham")
@RequiredArgsConstructor
public class AdminProductController {

    private static final int PAGE_LINK_WINDOW = 5;

    private final AdminProductService adminProductService;

    // ==================== SẢN PHẨM ====================

    @GetMapping
    public String list(@RequestParam(name = "keyword", required = false) String keyword,
                       @RequestParam(name = "brandId", required = false) Integer brandId,
                       @RequestParam(name = "categoryId", required = false) Integer categoryId,
                       @RequestParam(name = "status", required = false) ProductStatus status,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        Page<ProductRow> products = adminProductService.search(keyword, brandId, categoryId, status, page);
        if (products.getTotalPages() > 0 && page > products.getTotalPages()) {
            products = adminProductService.search(keyword, brandId, categoryId, status, products.getTotalPages());
        }

        model.addAttribute("products", products);
        model.addAttribute("brands", adminProductService.brands());
        model.addAttribute("categories", adminProductService.categories());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("brandFilter", brandId);
        model.addAttribute("categoryFilter", categoryId);
        model.addAttribute("statusFilter", status);
        model.addAttribute("pageNumbers", Pagination.pageNumbers(products, PAGE_LINK_WINDOW));
        model.addAttribute("adminMenu", "products");
        return "admin/products";
    }

    @GetMapping("/tao-moi")
    public String createForm(Model model) {
        prepareProductForm(model, new ProductForm(), null);
        return "admin/product-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProductForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareProductForm(model, form, null);
            return "admin/product-form";
        }
        try {
            Product product = adminProductService.createProduct(form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm sản phẩm \"" + product.getName() + "\". Hãy tạo biến thể để có giá và tồn kho.");
            return "redirect:/admin/san-pham/" + product.getId() + "/bien-the";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareProductForm(model, form, null);
            return "admin/product-form";
        }
    }

    @GetMapping("/{id}/sua")
    public String editForm(@PathVariable("id") Integer id, Model model) {
        Product product = adminProductService.requireProduct(id);
        ProductForm form = new ProductForm();
        form.setProductCode(product.getProductCode());
        form.setName(product.getName());
        form.setBrandId(product.getBrand().getId());
        form.setCategoryId(product.getCategory().getId());
        form.setDescription(product.getDescription());
        form.setLengthMm(product.getLengthMm());
        form.setBalancePointMm(product.getBalancePointMm());
        form.setBalanceType(product.getBalanceType());
        form.setShaftFlexibility(product.getShaftFlexibility());
        form.setMaxTensionKg(product.getMaxTensionKg());
        form.setFrameMaterial(product.getFrameMaterial());
        form.setShaftMaterial(product.getShaftMaterial());
        form.setStatus(product.getStatus());
        prepareProductForm(model, form, id);
        return "admin/product-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable("id") Integer id,
                         @Valid @ModelAttribute("form") ProductForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            prepareProductForm(model, form, id);
            return "admin/product-form";
        }
        try {
            Product product = adminProductService.updateProduct(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật sản phẩm \"" + product.getName() + "\".");
            return "redirect:/admin/san-pham";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareProductForm(model, form, id);
            return "admin/product-form";
        }
    }

    /** Ẩn/hiện sản phẩm trên trang khách (ACTIVE ⇄ HIDDEN). */
    @PostMapping("/{id}/doi-trang-thai")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        Product product = adminProductService.toggleProductStatus(id);
        redirectAttributes.addFlashAttribute("successMessage",
                product.getStatus() == ProductStatus.ACTIVE
                        ? "Đã cho hiển thị sản phẩm \"" + product.getName() + "\"."
                        : "Đã ẩn sản phẩm \"" + product.getName() + "\" khỏi trang khách.");
        return "redirect:/admin/san-pham";
    }

    /** Xoá mềm sản phẩm – dữ liệu đơn hàng cũ vẫn giữ nguyên. */
    @PostMapping("/{id}/xoa")
    public String delete(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        Product product = adminProductService.softDeleteProduct(id);
        redirectAttributes.addFlashAttribute("successMessage",
                "Đã xoá sản phẩm \"" + product.getName() + "\" khỏi danh mục bán.");
        return "redirect:/admin/san-pham";
    }

    // ==================== BIẾN THỂ & ẢNH ====================

    @GetMapping("/{id}/bien-the")
    public String variantList(@PathVariable("id") Integer id, Model model) {
        Product product = adminProductService.requireProduct(id);
        model.addAttribute("product", product);
        model.addAttribute("variants", adminProductService.variantsOf(id));
        model.addAttribute("images", adminProductService.imagesOf(id));
        model.addAttribute("bulkModes", BulkPriceMode.values());
        if (!model.containsAttribute("bulkForm")) {
            model.addAttribute("bulkForm", new BulkPriceForm());
        }
        model.addAttribute("adminMenu", "products");
        return "admin/product-variants";
    }

    @GetMapping("/{id}/bien-the/tao-moi")
    public String variantCreateForm(@PathVariable("id") Integer id, Model model) {
        Product product = adminProductService.requireProduct(id);
        VariantForm form = new VariantForm();
        form.setSku(product.getProductCode() + "-");
        prepareVariantForm(model, product, form, null);
        return "admin/variant-form";
    }

    @PostMapping("/{id}/bien-the")
    public String variantCreate(@PathVariable("id") Integer id,
                                @Valid @ModelAttribute("form") VariantForm form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Product product = adminProductService.requireProduct(id);
        if (bindingResult.hasErrors()) {
            prepareVariantForm(model, product, form, null);
            return "admin/variant-form";
        }
        try {
            ProductVariant variant = adminProductService.createVariant(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã thêm biến thể " + variant.getSku() + " cho \"" + product.getName() + "\".");
            return "redirect:/admin/san-pham/" + id + "/bien-the";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareVariantForm(model, product, form, null);
            return "admin/variant-form";
        }
    }

    @GetMapping("/{id}/bien-the/{variantId}/sua")
    public String variantEditForm(@PathVariable("id") Integer id,
                                  @PathVariable("variantId") Integer variantId,
                                  Model model) {
        Product product = adminProductService.requireProduct(id);
        ProductVariant variant = adminProductService.requireVariantOf(id, variantId);

        VariantForm form = new VariantForm();
        form.setSku(variant.getSku());
        form.setColor(variant.getColor());
        form.setWeightClass(variant.getWeightClass());
        form.setGripSize(variant.getGripSize());
        form.setImageUrl(variant.getImageUrl());
        form.setPrice(variant.getPrice());
        form.setOriginalPrice(variant.getOriginalPrice());
        form.setStockQuantity(variant.getStockQuantity());
        form.setStatus(variant.getStatus());
        prepareVariantForm(model, product, form, variantId);
        return "admin/variant-form";
    }

    @PostMapping("/{id}/bien-the/{variantId}")
    public String variantUpdate(@PathVariable("id") Integer id,
                                @PathVariable("variantId") Integer variantId,
                                @Valid @ModelAttribute("form") VariantForm form,
                                BindingResult bindingResult,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        Product product = adminProductService.requireProduct(id);
        if (bindingResult.hasErrors()) {
            prepareVariantForm(model, product, form, variantId);
            return "admin/variant-form";
        }
        try {
            ProductVariant variant = adminProductService.updateVariant(id, variantId, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật biến thể " + variant.getSku() + ".");
            return "redirect:/admin/san-pham/" + id + "/bien-the";
        } catch (BusinessException ex) {
            model.addAttribute("formError", ex.getMessage());
            prepareVariantForm(model, product, form, variantId);
            return "admin/variant-form";
        }
    }

    @PostMapping("/{id}/bien-the/{variantId}/doi-trang-thai")
    public String variantToggle(@PathVariable("id") Integer id,
                                @PathVariable("variantId") Integer variantId,
                                RedirectAttributes redirectAttributes) {
        ProductVariant variant = adminProductService.toggleVariantStatus(id, variantId);
        redirectAttributes.addFlashAttribute("successMessage",
                variant.getStatus() == VariantStatus.ACTIVE
                        ? "Đã mở bán lại biến thể " + variant.getSku() + "."
                        : "Đã ngừng bán biến thể " + variant.getSku() + ".");
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    @PostMapping("/{id}/bien-the/{variantId}/xoa")
    public String variantDelete(@PathVariable("id") Integer id,
                                @PathVariable("variantId") Integer variantId,
                                RedirectAttributes redirectAttributes) {
        adminProductService.deleteVariant(id, variantId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã xoá biến thể khỏi sản phẩm.");
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    /**
     * Cập nhật giá hàng loạt cho các biến thể được tích chọn (mục 13 – mức Khá).
     * Lỗi nghiệp vụ hiện ngay trên trang biến thể qua flash để admin nhập lại.
     */
    @PostMapping("/{id}/bien-the/cap-nhat-gia")
    public String updatePricesBulk(@PathVariable("id") Integer id,
                                   @ModelAttribute("bulkForm") BulkPriceForm form,
                                   RedirectAttributes redirectAttributes) {
        try {
            int updated = adminProductService.updatePricesBulk(id, form);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Đã cập nhật giá cho " + updated + " biến thể.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    /** Tải ảnh lên: lỗi định dạng/dung lượng hiện ngay trên trang biến thể để admin chọn lại. */
    @PostMapping("/{id}/anh")
    public String uploadImage(@PathVariable("id") Integer id,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(name = "makePrimary", defaultValue = "false") boolean makePrimary,
                              RedirectAttributes redirectAttributes) {
        try {
            ProductImage image = adminProductService.addImage(id, file, makePrimary);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm ảnh mới cho sản phẩm.");
            if (image.isPrimaryImage()) {
                redirectAttributes.addFlashAttribute("infoMessage", "Ảnh này đang được dùng làm ảnh chính.");
            }
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    @PostMapping("/{id}/anh/{imageId}/anh-chinh")
    public String makePrimary(@PathVariable("id") Integer id,
                              @PathVariable("imageId") Integer imageId,
                              RedirectAttributes redirectAttributes) {
        adminProductService.makePrimary(id, imageId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã đặt ảnh làm ảnh chính của sản phẩm.");
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    @PostMapping("/{id}/anh/{imageId}/xoa")
    public String deleteImage(@PathVariable("id") Integer id,
                              @PathVariable("imageId") Integer imageId,
                              RedirectAttributes redirectAttributes) {
        adminProductService.deleteImage(id, imageId);
        redirectAttributes.addFlashAttribute("successMessage", "Đã gỡ ảnh khỏi sản phẩm.");
        return "redirect:/admin/san-pham/" + id + "/bien-the";
    }

    // ==================== DỮ LIỆU CHO FORM ====================

    private void prepareProductForm(Model model, ProductForm form, Integer productId) {
        model.addAttribute("form", form);
        model.addAttribute("productId", productId);
        model.addAttribute("brands", adminProductService.brands());
        model.addAttribute("categories", adminProductService.categories());
        model.addAttribute("statuses", ProductStatus.values());
        model.addAttribute("balanceTypes", BalanceType.values());
        model.addAttribute("flexibilities", ShaftFlexibility.values());
        model.addAttribute("adminMenu", "products");
    }

    private void prepareVariantForm(Model model, Product product, VariantForm form, Integer variantId) {
        model.addAttribute("form", form);
        model.addAttribute("product", product);
        model.addAttribute("variantId", variantId);
        model.addAttribute("variantStatuses", VariantStatus.values());
        model.addAttribute("weightClasses", List.of("2U", "3U", "4U", "5U"));
        model.addAttribute("gripSizes", List.of("G3", "G4", "G5", "G6"));
        model.addAttribute("adminMenu", "products");
    }
}
