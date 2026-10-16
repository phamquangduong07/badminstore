package com.example.demo.entity;

import com.example.demo.enums.VariantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Biến thể bán được của vợt (bảng product_variants): màu × trọng lượng × cỡ cán.
 * Tồn kho và giá bán nằm ở đây – mọi nghiệp vụ trừ/cộng kho đều thao tác trên bảng này.
 */
@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku", nullable = false, length = 50, unique = true)
    private String sku;

    @Nationalized
    @Column(name = "color", nullable = false, length = 50)
    private String color;

    /** 2U / 3U / 4U / 5U (quy ước trọng lượng vợt, KHÔNG phải gram). */
    @Column(name = "weight_class", nullable = false, length = 10)
    private String weightClass;

    /** G3 / G4 / G5 / G6. */
    @Column(name = "grip_size", nullable = false, length = 10)
    private String gripSize;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Giá gạch ngang (nếu đang giảm giá), NULL nếu không giảm. */
    @Column(name = "original_price", precision = 18, scale = 0)
    private BigDecimal originalPrice;

    @Column(name = "price", nullable = false, precision = 18, scale = 0)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VariantStatus status = VariantStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isInStock() {
        return status == VariantStatus.ACTIVE && stockQuantity > 0;
    }

    public boolean isDiscounted() {
        return originalPrice != null && originalPrice.compareTo(price) > 0;
    }
}
