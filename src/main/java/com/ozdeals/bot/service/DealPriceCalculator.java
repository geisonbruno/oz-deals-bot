package com.ozdeals.bot.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DealPriceCalculator {

    private static final int RESULT_SCALE = 2;
    private static final int INTERMEDIATE_SCALE = 10;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public DealPriceResult calculate(BigDecimal currentPrice, BigDecimal originalPrice) {
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("currentPrice must be greater than zero");
        }
        if (originalPrice == null || originalPrice.compareTo(currentPrice) <= 0) {
            throw new IllegalArgumentException("originalPrice must be greater than currentPrice");
        }

        BigDecimal rawSaving = originalPrice.subtract(currentPrice);
        BigDecimal savingAmount = rawSaving.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal discountPercentage = rawSaving
                .divide(originalPrice, INTERMEDIATE_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);

        return new DealPriceResult(savingAmount, discountPercentage);
    }
}
