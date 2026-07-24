package com.ozdeals.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "deals", uniqueConstraints = {
        @UniqueConstraint(name = "uq_deal_slug", columnNames = "slug")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(length = 2000)
    private String sourceUrl;

    @Column(length = 2000)
    private String affiliateUrl;

    @Column(length = 1000)
    private String shortDescription;

    private BigDecimal currentPrice;
    private BigDecimal originalPrice;

    @Column(length = 2000)
    private String imageUrl;

    private String telegramImageFileId;

    private String slug;

    @Enumerated(EnumType.STRING)
    private DealStatus status;

    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = DealStatus.DRAFT;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
