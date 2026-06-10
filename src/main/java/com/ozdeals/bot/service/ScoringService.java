package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class ScoringService {

    public int score(DiscoveredProduct product, Optional<BigDecimal> historicalLow) {
        return discountScore(product) + historicalLowScore(product.getCurrentPrice(), historicalLow);
    }

    public int discountPercent(DiscoveredProduct product) {
        if (product.getListPrice() == null || product.getCurrentPrice() == null) return 0;
        if (product.getListPrice().compareTo(BigDecimal.ZERO) == 0) return 0;
        return product.getListPrice()
                .subtract(product.getCurrentPrice())
                .divide(product.getListPrice(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    private int discountScore(DiscoveredProduct product) {
        int pct = discountPercent(product);
        if (pct < 10) return 5;
        if (pct < 20) return 15;
        if (pct < 30) return 25;
        if (pct < 40) return 35;
        if (pct < 50) return 45;
        return 50;
    }

    private int historicalLowScore(BigDecimal currentPrice, Optional<BigDecimal> historicalLow) {
        if (currentPrice == null || historicalLow.isEmpty()) return 0;
        BigDecimal low = historicalLow.get();
        if (low.compareTo(BigDecimal.ZERO) == 0) return 0;

        double ratio = currentPrice.divide(low, 4, RoundingMode.HALF_UP).doubleValue();
        if (ratio <= 1.00) return 50;
        if (ratio <= 1.05) return 40;
        if (ratio <= 1.10) return 30;
        if (ratio <= 1.20) return 20;
        if (ratio <= 1.30) return 10;
        return 5;
    }
}
