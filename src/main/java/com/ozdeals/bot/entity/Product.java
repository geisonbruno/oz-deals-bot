package com.ozdeals.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    private String asin;

    @Column(length = 1000)
    private String title;

    private String brand;
    private String category;

    @Column(length = 2000)
    private String imageUrl;

    private LocalDateTime createdAt;
}
