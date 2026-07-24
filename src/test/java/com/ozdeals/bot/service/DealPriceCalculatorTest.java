package com.ozdeals.bot.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DealPriceCalculatorTest {

    private final DealPriceCalculator calculator = new DealPriceCalculator();

    @Test
    void calculate_savingAmount_isOriginalMinusCurrent() {
        DealPriceResult result = calculator.calculate(new BigDecimal("80.00"), new BigDecimal("100.00"));

        assertThat(result.savingAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void calculate_discountPercentage_isCorrect() {
        DealPriceResult result = calculator.calculate(new BigDecimal("80.00"), new BigDecimal("100.00"));

        assertThat(result.discountPercentage()).isEqualByComparingTo("20.00");
    }

    @Test
    void calculate_decimalRounding_roundsHalfUpToTwoDecimals() {
        DealPriceResult result = calculator.calculate(new BigDecimal("66.67"), new BigDecimal("100.00"));

        assertThat(result.savingAmount()).isEqualByComparingTo("33.33");
        assertThat(result.discountPercentage()).isEqualByComparingTo("33.33");
    }

    @Test
    void calculate_currentPriceZero_throws() {
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ZERO, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculate_equalPrices_throws() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculate_originalPriceBelowCurrent_throws() {
        assertThatThrownBy(() -> calculator.calculate(new BigDecimal("100.00"), new BigDecimal("80.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
