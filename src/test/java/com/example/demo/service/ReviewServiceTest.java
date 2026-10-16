package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.shop.ReviewSummary;
import com.example.demo.entity.OrderItem;
import com.example.demo.entity.Product;
import com.example.demo.entity.ProductReview;
import com.example.demo.entity.User;
import com.example.demo.enums.ReviewStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductReviewRepository;
import com.example.demo.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Kiểm thử nghiệp vụ đánh giá sản phẩm (mức Khá):
 * chỉ khách đã nhận hàng (đơn DELIVERED) mới đánh giá được, mỗi lượt mua một lần,
 * điểm 1–5 sao và nội dung tối đa 1000 ký tự.
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Integer PRODUCT_ID = 124;
    private static final Integer USER_ID = 7;

    @Mock
    private ProductReviewRepository productReviewRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("Chọn 0 sao hoặc quá 5 sao ⇒ chặn ngay, không truy vấn dữ liệu")
    void addReview_rejectsRatingOutOfRange() {
        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 0, "Rất tốt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("từ 1 đến 5");

        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 9, "Rất tốt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("từ 1 đến 5");

        verify(productReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Bỏ trống nội dung hoặc quá 1000 ký tự ⇒ chặn")
    void addReview_rejectsBadComment() {
        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 5, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("vài dòng cảm nhận");

        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 5, "x".repeat(1001)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối đa 1000 ký tự");

        verify(productReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Khách chưa có đơn DELIVERED cho sản phẩm ⇒ không được đánh giá")
    void addReview_requiresDeliveredOrder() {
        when(orderItemRepository.findDeliveredItemIdsOf(USER_ID, PRODUCT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 5, "Vợt đẹp"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ khách đã mua và đã nhận sản phẩm này");

        verify(productReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dòng đơn đã được đánh giá rồi ⇒ không đánh giá lại (UNIQUE order_item_id)")
    void addReview_blocksSecondReviewOfSameItem() {
        when(orderItemRepository.findDeliveredItemIdsOf(USER_ID, PRODUCT_ID)).thenReturn(List.of(55));
        when(productReviewRepository.existsByOrderItemId(55)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 4, "Ổn"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mỗi lượt mua chỉ đánh giá một lần");

        verify(productReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("Khách đã nhận hàng: đánh giá được lưu ở trạng thái hiển thị, nội dung được cắt khoảng trắng")
    void addReview_savesVisibleReview() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        OrderItem orderItem = new OrderItem();
        orderItem.setId(55);
        User user = new User();
        user.setId(USER_ID);

        when(orderItemRepository.findDeliveredItemIdsOf(USER_ID, PRODUCT_ID)).thenReturn(List.of(55));
        when(productReviewRepository.existsByOrderItemId(55)).thenReturn(false);
        when(productRepository.findDetailById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(orderItemRepository.getReferenceById(55)).thenReturn(orderItem);
        when(productReviewRepository.save(any(ProductReview.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductReview saved = reviewService.addReview(PRODUCT_ID, USER_ID, (byte) 5, "  Vợt nhẹ, đập rất êm  ");

        assertThat(saved.getRating()).isEqualTo((byte) 5);
        assertThat(saved.getComment()).isEqualTo("Vợt nhẹ, đập rất êm");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.VISIBLE);
        assertThat(saved.getProduct()).isSameAs(product);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getOrderItem()).isSameAs(orderItem);
    }

    @Test
    @DisplayName("Chọn lượt mua còn trống: bỏ qua dòng đơn đã đánh giá")
    void findReviewableOrderItemId_skipsReviewedItems() {
        when(orderItemRepository.findDeliveredItemIdsOf(USER_ID, PRODUCT_ID)).thenReturn(List.of(55, 56));
        when(productReviewRepository.existsByOrderItemId(55)).thenReturn(true);
        when(productReviewRepository.existsByOrderItemId(56)).thenReturn(false);

        assertThat(reviewService.findReviewableOrderItemId(PRODUCT_ID, USER_ID)).contains(56);
    }

    @Test
    @DisplayName("Khách chưa từng nhận hàng sản phẩm ⇒ hasPurchased = false")
    void hasPurchased_returnsFalseWhenNoDeliveredItem() {
        when(orderItemRepository.findDeliveredItemIdsOf(USER_ID, PRODUCT_ID)).thenReturn(List.of());

        assertThat(reviewService.hasPurchased(PRODUCT_ID, USER_ID)).isFalse();
    }

    @Test
    @DisplayName("Tóm tắt đánh giá: điểm trung bình làm tròn 1 chữ số theo kiểu Việt Nam")
    void summaryOf_roundsAverage() {
        when(productReviewRepository.findAverageRating(PRODUCT_ID, ReviewStatus.VISIBLE)).thenReturn(4.6667);
        when(productReviewRepository.countByProductIdAndStatus(PRODUCT_ID, ReviewStatus.VISIBLE)).thenReturn(3L);

        ReviewSummary summary = reviewService.summaryOf(PRODUCT_ID);

        assertThat(summary.getAverage()).isEqualTo(4.7d);
        assertThat(summary.getAverageLabel()).isEqualTo("4,7/5");
        assertThat(summary.getFilledStars()).isEqualTo(5);
        assertThat(summary.getCountLabel()).isEqualTo("3 lượt đánh giá");
        assertThat(summary.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Sản phẩm chưa có đánh giá: điểm 0 và nhãn \"Chưa có đánh giá\"")
    void summaryOf_handlesEmpty() {
        when(productReviewRepository.findAverageRating(PRODUCT_ID, ReviewStatus.VISIBLE)).thenReturn(null);
        when(productReviewRepository.countByProductIdAndStatus(PRODUCT_ID, ReviewStatus.VISIBLE)).thenReturn(0L);

        ReviewSummary summary = reviewService.summaryOf(PRODUCT_ID);

        assertThat(summary.isEmpty()).isTrue();
        assertThat(summary.getFilledStars()).isZero();
        assertThat(summary.getSummaryLabel()).isEqualTo("Chưa có đánh giá");
    }
}
