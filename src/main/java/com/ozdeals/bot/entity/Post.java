package com.ozdeals.bot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts", indexes = {
        @Index(name = "idx_post_asin", columnList = "asin"),
        @Index(name = "idx_post_posted_at", columnList = "posted_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String asin;
    private BigDecimal price;
    private LocalDateTime postedAt;
    private String messageHash;
}
