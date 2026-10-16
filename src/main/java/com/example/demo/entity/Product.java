package com.example.demo.entity;

import com.example.demo.enums.BalanceType;
import com.example.demo.enums.ProductStatus;
import com.example.demo.enums.ShaftFlexibility;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/**
 * Sản phẩm (bảng products) – chỉ bán vợt cầu lông.
 * Giá bán nằm ở {@link ProductVariant#getPrice()}, sản phẩm không có giá riêng.
 * Các cột kỹ thuật (chiều dài, điểm cân bằng, độ cứng, mức căng…) dùng cho trang so sánh sản phẩm.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "product_code", nullable = false, length = 30)
    private String productCode;

    @Nationalized
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** NVARCHAR(MAX) trong DB. */
    @Nationalized
    @Column(name = "description", columnDefinition = "nvarchar(max)")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "length_mm")
    private Integer lengthMm;

    @Column(name = "balance_point_mm")
    private Integer balancePointMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "balance_type", length = 20)
    private BalanceType balanceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "shaft_flexibility", length = 20)
    private ShaftFlexibility shaftFlexibility;

    @Column(name = "max_tension_kg", precision = 4, scale = 1)
    private BigDecimal maxTensionKg;

    @Nationalized
    @Column(name = "frame_material", length = 150)
    private String frameMaterial;

    @Nationalized
    @Column(name = "shaft_material", length = 150)
    private String shaftMaterial;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductVariant> variants = new ArrayList<>();

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

    public void addImage(ProductImage image) {
        images.add(image);
        image.setProduct(this);
    }
}
