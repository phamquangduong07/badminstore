package com.example.demo.controller.shop;

import com.example.demo.enums.ProductStatus;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang chủ của cửa hàng: banner + vài thống kê + danh mục nhanh + sản phẩm mới nhất. */
@Controller
@RequiredArgsConstructor
public class HomeController {

    /** Số sản phẩm mới nhất hiển thị ở trang chủ. */
    private static final int FEATURED_LIMIT = 4;

    /** Số danh mục hiển thị ở khối "chọn nhanh theo lối chơi". */
    private static final int CATEGORY_LIMIT = 4;

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final CategoryRepository categoryRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("totalProducts", productRepository.countByDeletedFalseAndStatus(ProductStatus.ACTIVE));
        model.addAttribute("featuredProducts", productService.getFeatured(FEATURED_LIMIT));
        model.addAttribute("categories",
                categoryRepository.findByDeletedFalseOrderByNameAsc().stream().limit(CATEGORY_LIMIT).toList());
        return "shop/home";
    }
}
