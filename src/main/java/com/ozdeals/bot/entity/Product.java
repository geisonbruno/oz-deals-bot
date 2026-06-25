package com.ozdeals.bot.entity;

import com.ozdeals.bot.ProductSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(name = "uq_product_source_external_id", columnNames = {"source", "external_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ProductSource source;

    @Column(name = "external_id")
    private String externalId;

    @Column(length = 1000)
    private String title;

    private String brand;
    private String category;

    @Column(length = 2000)
    private String imageUrl;

    private LocalDateTime createdAt;
}
