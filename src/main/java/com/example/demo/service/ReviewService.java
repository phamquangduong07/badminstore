package com.example.demo.service;

import com.example.demo.dto.shop.ReviewSummary;
import com.example.demo.entity.ProductReview;
import com.example.demo.enums.ReviewStatus;
import com.example.demo.exception.BusinessException;
import com.example.demo.exception.NotFoundException;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.ProductReviewRepository;
import com.example.demo.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đánh giá sản phẩm (tính năng mức Khá).
 *
 * Luật nghiệp vụ: CHỈ khách đã mua và đã nhận hàng (đơn DELIVERED) mới được đánh giá,
 * mỗi dòng đơn (order_items) đánh giá đúng MỘT lần – khớp ràng buộc UNIQUE product_reviews.order_item_id
 * trong BadStore.sql. Điểm sao 1–5, nội dung tối đa 1000 ký tự.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** Số đánh giá mỗi trang ở khối bình luận trang chi tiết sản phẩm. */
    public static final int PAGE_SIZE = 5;

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final ProductReviewRepository productReviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /** Đánh giá đang hiển thị của sản phẩm, mới nhất trước (đã join fetch người viết). */
    @Transactional(readOnly = true)
    public Page<ProductReview> visibleReviews(Integer productId, int pageNumber) {
        return productReviewRepository.findVisibleWithUser(productId, ReviewStatus.VISIBLE,
                PageRequest.of(Math.max(0, pageNumber - 1), PAGE_SIZE));
    }

    @Transactional(readOnly = true)
    public ReviewSummary summaryOf(Integer productId) {
        return new ReviewSummary(productReviewRepository.findAverageRating(productId, ReviewStatus.VISIBLE),
                productReviewRepository.countByProductIdAndStatus(productId, ReviewStatus.VISIBLE));
    }

    /**
     * Dòng đơn CÒN được phép đánh giá: đơn của chính khách, đã giao (DELIVERED), đúng sản phẩm
     * và chưa có đánh giá nào. Rỗng = không còn lượt đánh giá nào cho sản phẩm này.
     */
    @Transactional(readOnly = true)
    public Optional<Integer> findReviewableOrderItemId(Integer productId, Integer userId) {
        return orderItemRepository.findDeliveredItemIdsOf(userId, productId).stream()
                .filter(orderItemId -> !productReviewRepository.existsByOrderItemId(orderItemId))
                .findFirst();
    }

    /** Khách đã từng mua và nhận sản phẩm này chưa – dùng để hiện lời cảm ơn thay vì form đánh giá. */
    @Transactional(readOnly = true)
    public boolean hasPurchased(Integer productId, Integer userId) {
        return !orderItemRepository.findDeliveredItemIdsOf(userId, productId).isEmpty();
    }

    /** Thêm đánh giá: chỉ chạy khi thật sự còn lượt đánh giá từ đơn đã giao. */
    @Transactional
    public ProductReview addReview(Integer productId, Integer userId, byte rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Vui lòng chọn số sao từ 1 đến 5.");
        }
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty()) {
            throw new BusinessException("Bạn hãy viết vài dòng cảm nhận về sản phẩm.");
        }
        if (text.length() > MAX_COMMENT_LENGTH) {
            throw new BusinessException("Nội dung đánh giá tối đa " + MAX_COMMENT_LENGTH + " ký tự.");
        }

        Integer orderItemId = findReviewableOrderItemId(productId, userId)
                .orElseThrow(() -> new BusinessException("Chỉ khách đã mua và đã nhận sản phẩm này mới đánh giá được. "
                        + "Mỗi lượt mua chỉ đánh giá một lần."));

        ProductReview review = new ProductReview();
        review.setProduct(productRepository.findDetailById(productId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm cần đánh giá.")));
        review.setUser(userRepository.getReferenceById(userId));
        review.setOrderItem(orderItemRepository.getReferenceById(orderItemId));
        review.setRating(rating);
        review.setComment(text);
        review.setStatus(ReviewStatus.VISIBLE);
        return productReviewRepository.save(review);
    }
}
