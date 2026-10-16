package com.example.demo.controller.shop;

import com.example.demo.dto.shop.FilterOptions;
import com.example.demo.dto.shop.ProductCardView;
import com.example.demo.dto.shop.ProductDetailView;
import com.example.demo.dto.shop.ProductFilter;
import com.example.demo.entity.ProductReview;
import com.example.demo.enums.ProductSort;
import com.example.demo.service.CustomUserDetails;
import com.example.demo.service.ProductService;
import com.example.demo.service.ReviewService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Trang danh mục sản phẩm và trang chi tiết – ai cũng xem được, không cần đăng nhập. */
@Controller
@RequiredArgsConstructor
@RequestMapping("/san-pham")
public class ProductController {

    /** Số nút số trang hiển thị quanh trang hiện tại. */
    private static final int PAGE_LINK_WINDOW = 5;

    private final ProductService productService;
    private final ReviewService reviewService;

    @GetMapping
    public String list(@ModelAttribute("filter") ProductFilter filter,
                       BindingResult bindingResult,
                       @RequestParam(name = "page", defaultValue = "1") int page,
                       Model model) {
        Page<ProductCardView> result = productService.search(filter, page);
        FilterOptions options = productService.getFilterOptions();

        model.addAttribute("page", result);
        model.addAttribute("brands", options.brands());
        model.addAttribute("categories", options.categories());
        model.addAttribute("sorts", ProductSort.values());
        model.addAttribute("pageNumbers", pageNumbers(result));
        return "shop/products";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Integer id,
                         @RequestParam(name = "reviewPage", defaultValue = "1") int reviewPage,
                         @AuthenticationPrincipal CustomUserDetails currentUser,
                         Model model) {
        ProductDetailView detail = productService.getDetail(id);

        // Khối đánh giá: danh sách + điểm trung bình; quyền đánh giá kiểm tra theo tài khoản đang đăng nhập
        Page<ProductReview> reviews = reviewService.visibleReviews(id, reviewPage);
        if (reviews.getTotalPages() > 0 && reviewPage > reviews.getTotalPages()) {
            reviews = reviewService.visibleReviews(id, reviews.getTotalPages());
        }

        model.addAttribute("product", detail.product());
        model.addAttribute("variants", detail.variants());
        model.addAttribute("images", detail.images());
        model.addAttribute("detail", detail);
        model.addAttribute("reviews", reviews);
        model.addAttribute("reviewSummary", reviewService.summaryOf(id));
        if (currentUser != null) {
            model.addAttribute("reviewable",
                    reviewService.findReviewableOrderItemId(id, currentUser.getUserId()).isPresent());
            model.addAttribute("purchased", reviewService.hasPurchased(id, currentUser.getUserId()));
        }
        return "shop/product-detail";
    }

    /**
     * Cửa sổ số trang (ví dụ đang ở trang 7/20 thì hiện 5,6,7,8,9) – tính ở Controller
     * để template không phải chứa logic.
     */
    private List<Integer> pageNumbers(Page<?> result) {
        if (result.getTotalPages() <= 1) {
            return List.of();
        }
        int total = result.getTotalPages();
        int start = Math.max(0, result.getNumber() - PAGE_LINK_WINDOW / 2);
        int end = Math.min(total - 1, start + PAGE_LINK_WINDOW - 1);
        start = Math.max(0, end - PAGE_LINK_WINDOW + 1);

        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
