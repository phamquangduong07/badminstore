package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.entity.Product;
import com.example.demo.enums.ProductStatus;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.CompareService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test tích hợp SO SÁNH SẢN PHẨM (mức Khá).
 *
 * Tính năng chỉ ghi vào SESSION nên test không tạo dữ liệu mới: dùng sẵn 5 sản phẩm ACTIVE
 * của CSDL thật và kiểm tra trang so sánh, badge ở header, chặn vượt 4 sản phẩm, xoá từng cái/xoá hết.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompareIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private List<Integer> activeProductIds;
    private List<String> activeProductNames;

    @BeforeEach
    void setUp() {
        List<Product> activeProducts = productRepository.findByDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                .limit(5)
                .toList();
        // Cần tối thiểu 5 sản phẩm đang bán để kiểm tra luật "tối đa 4 sản phẩm".
        assumeTrue(activeProducts.size() >= 5, "CSDL cần ít nhất 5 sản phẩm ACTIVE (chạy docs/seed_san_pham.sql)");
        activeProductIds = activeProducts.stream().map(Product::getId).toList();
        activeProductNames = activeProducts.stream().map(Product::getName).toList();
    }

    @Test
    @DisplayName("Trang so sánh khi chưa chọn gì: hiện trạng thái rỗng kèm hướng dẫn")
    void comparePageShowsEmptyState() throws Exception {
        mockMvc.perform(get("/so-sanh"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("So sánh sản phẩm")))
                .andExpect(content().string(containsString("Danh sách so sánh đang trống")))
                .andExpect(content().string(containsString("Xem danh sách vợt")));
    }

    @Test
    @DisplayName("Thẻ sản phẩm ở trang danh mục có nút So sánh gửi tới /so-sanh/them")
    void productCardHasCompareButton() throws Exception {
        mockMvc.perform(get("/san-pham"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/so-sanh/them")))
                .andExpect(content().string(containsString("So sánh")));
    }

    @Test
    @DisplayName("Thêm từ trang chi tiết: quay lại đúng trang cũ, session nhớ 1 sản phẩm, bảng so sánh hiện tên")
    void addFromProductDetailThenCompare() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Integer productId = activeProductIds.get(0);

        mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                        .header("Referer", "http://localhost/san-pham/" + productId)
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/san-pham/" + productId))
                .andExpect(flash().attribute("successMessage", containsString("Đã thêm")));

        assertThat(session.getAttribute(CompareService.SESSION_KEY)).isEqualTo(List.of(productId));

        mockMvc.perform(get("/so-sanh").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(activeProductNames.get(0))))
                .andExpect(content().string(containsString("Tiêu chí")))
                .andExpect(content().string(containsString("Lối chơi")))
                .andExpect(content().string(containsString("Xem chi tiết")));
    }

    @Test
    @DisplayName("Thêm trùng sản phẩm: báo đã có trong danh sách, không nhân đôi cột")
    void addDuplicateShowsMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Integer productId = activeProductIds.get(0);

        mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                        .header("Referer", "/san-pham")
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                        .header("Referer", "/san-pham")
                        .param("productId", String.valueOf(productId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("đã có trong danh sách so sánh")));

        mockMvc.perform(get("/so-sanh").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đang có 1 sản phẩm")));
    }

    @Test
    @DisplayName("Thêm sản phẩm thứ 5: bị chặn kèm hướng dẫn bỏ bớt")
    void addFifthProductIsBlocked() throws Exception {
        MockHttpSession session = new MockHttpSession();

        for (int index = 0; index < CompareService.MAX_ITEMS; index++) {
            mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                            .header("Referer", "/san-pham")
                            .param("productId", String.valueOf(activeProductIds.get(index))))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                        .header("Referer", "/san-pham")
                        .param("productId", String.valueOf(activeProductIds.get(4))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("tối đa 4 sản phẩm")));
    }

    @Test
    @DisplayName("Bỏ một sản phẩm rồi xoá hết: bảng so sánh trở về trạng thái rỗng")
    void removeThenClearAll() throws Exception {
        MockHttpSession session = new MockHttpSession();
        for (int index = 0; index < 3; index++) {
            mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                            .header("Referer", "/san-pham")
                            .param("productId", String.valueOf(activeProductIds.get(index))))
                    .andExpect(status().is3xxRedirection());
        }

        mockMvc.perform(post("/so-sanh/xoa").with(csrf()).session(session)
                        .param("productId", String.valueOf(activeProductIds.get(0))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/so-sanh"))
                .andExpect(flash().attribute("successMessage", containsString("Đã bỏ sản phẩm")));

        mockMvc.perform(get("/so-sanh").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đang có 2 sản phẩm")));

        mockMvc.perform(post("/so-sanh/xoa-het").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("xoá toàn bộ danh sách so sánh")));

        mockMvc.perform(get("/so-sanh").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Danh sách so sánh đang trống")));
    }

    @Test
    @DisplayName("Header hiện badge số sản phẩm đang so sánh trên mọi trang")
    void headerShowsCompareBadge() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/so-sanh/them").with(csrf()).session(session)
                        .header("Referer", "/san-pham")
                        .param("productId", String.valueOf(activeProductIds.get(0))))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/san-pham").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/so-sanh")));
    }
}
