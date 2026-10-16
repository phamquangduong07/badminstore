package com.example.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

/** Địa chỉ giao hàng của khách (bảng user_addresses). Xoá mềm bằng is_deleted. */
@Entity
@Table(name = "user_addresses")
@Getter
@Setter
@NoArgsConstructor
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Nationalized
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "province_id")
    private Integer provinceId;

    @Nationalized
    @Column(name = "province_name", length = 100)
    private String provinceName;

    @Column(name = "district_id")
    private Integer districtId;

    @Nationalized
    @Column(name = "district_name", length = 100)
    private String districtName;

    @Column(name = "ward_code", length = 20)
    private String wardCode;

    @Nationalized
    @Column(name = "ward_name", length = 100)
    private String wardName;

    @Nationalized
    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

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

    /** Địa chỉ đầy đủ để hiển thị (dùng chung cho mọi màn hình). */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (addressDetail != null) {
            sb.append(addressDetail);
        }
        if (wardName != null && !wardName.isBlank()) {
            sb.append(", ").append(wardName);
        }
        if (districtName != null && !districtName.isBlank()) {
            sb.append(", ").append(districtName);
        }
        if (provinceName != null && !provinceName.isBlank()) {
            sb.append(", ").append(provinceName);
        }
        return sb.toString();
    }
}
