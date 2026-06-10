package com.ozdeals.bot.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class DiscoveredProduct {

    private String asin;
    private String title;
    private String brand;
    private String category;
    private String imageUrl;
    private String affiliateLink;
    private BigDecimal currentPrice;
    private BigDecimal listPrice;
}
