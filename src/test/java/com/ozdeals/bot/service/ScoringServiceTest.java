package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService();

    // --- discountPercent() ---

    @Test
    void discountPercent_nullListPrice_returnsZero() {
        assertThat(scoringService.discountPercent(product(new BigDecimal("100"), null))).isZero();
    }

    @Test
    void discountPercent_nullCurrentPrice_returnsZero() {
        assertThat(scoringService.discountPercent(product(null, new BigDecimal("100")))).isZero();
    }

    @Test
    void discountPercent_equalPrices_returnsZero() {
        assertThat(scoringService.discountPercent(product(new BigDecimal("100"), new BigDecimal("100")))).isZero();
    }

    @Test
    void discountPercent_normalDiscount_returnsCorrectPercent() {
        // (100 - 70) / 100 * 100 = 30%
        assertThat(scoringService.discountPercent(product(new BigDecimal("70"), new BigDecimal("100")))).isEqualTo(30);
    }

    // --- discountScore() boundaries (tested via score() with empty historicalLow → historicalLowScore = 0) ---

    @Test
    void score_discountBelow10Percent_discountScoreIs5() {
        // 9% off → pct < 10 → discountScore = 5
        assertThat(scoringService.score(product(new BigDecimal("91"), new BigDecimal("100")), Optional.empty())).isEqualTo(5);
    }

    @Test
    void score_discountExactly10Percent_discountScoreIs15() {
        // 10% off → pct < 20 → discountScore = 15
        assertThat(scoringService.score(product(new BigDecimal("90"), new BigDecimal("100")), Optional.empty())).isEqualTo(15);
    }

    @Test
    void score_discountExactly20Percent_discountScoreIs25() {
        // 20% off → pct < 30 → discountScore = 25
        assertThat(scoringService.score(product(new BigDecimal("80"), new BigDecimal("100")), Optional.empty())).isEqualTo(25);
    }

    @Test
    void score_discountExactly30Percent_discountScoreIs35() {
        // 30% off → pct < 40 → discountScore = 35
        assertThat(scoringService.score(product(new BigDecimal("70"), new BigDecimal("100")), Optional.empty())).isEqualTo(35);
    }

    @Test
    void score_discountExactly40Percent_discountScoreIs45() {
        // 40% off → pct < 50 → discountScore = 45
        assertThat(scoringService.score(product(new BigDecimal("60"), new BigDecimal("100")), Optional.empty())).isEqualTo(45);
    }

    @Test
    void score_discountExactly50Percent_discountScoreIs50() {
        // 50% off → pct >= 50 → discountScore = 50
        assertThat(scoringService.score(product(new BigDecimal("50"), new BigDecimal("100")), Optional.empty())).isEqualTo(50);
    }

    // --- historicalLowScore() boundaries (tested via score() with 0% discount → discountScore = 5) ---

    @Test
    void score_emptyHistoricalLow_historicalLowScoreIsZero() {
        // discountScore=5 + historicalLowScore=0 = 5
        assertThat(scoringService.score(product(new BigDecimal("100"), new BigDecimal("100")), Optional.empty())).isEqualTo(5);
    }

    @Test
    void score_currentPriceAtHistoricalLow_historicalLowScoreIs50() {
        // ratio = 100/100 = 1.0 → historicalLowScore = 50; discountScore = 5; total = 55
        assertThat(scoringService.score(product(new BigDecimal("100"), new BigDecimal("100")), Optional.of(new BigDecimal("100")))).isEqualTo(55);
    }

    @Test
    void score_currentPriceAboveHistoricalLow_historicalLowScoreReduced() {
        // historicalLow=90, currentPrice=100 → ratio=1.111 → 1.10 < ratio ≤ 1.20 → historicalLowScore=20
        // discountScore=5 (0% off); total = 25
        assertThat(scoringService.score(product(new BigDecimal("100"), new BigDecimal("100")), Optional.of(new BigDecimal("90")))).isEqualTo(25);
    }

    // --- combined score relative to publish threshold (≥ 70) ---

    @Test
    void score_belowPublishThreshold() {
        // 10% off → discountScore=15; no history → historicalLowScore=0; total=15 < 70
        int score = scoringService.score(product(new BigDecimal("90"), new BigDecimal("100")), Optional.empty());
        assertThat(score).isLessThan(70);
    }

    @Test
    void score_exactlyAtPublishThreshold() {
        // 50% off → discountScore=50; historicalLow=45, ratio=50/45≈1.111 → historicalLowScore=20; total=70
        int score = scoringService.score(product(new BigDecimal("50"), new BigDecimal("100")), Optional.of(new BigDecimal("45")));
        assertThat(score).isEqualTo(70);
    }

    @Test
    void score_abovePublishThreshold() {
        // 50% off → discountScore=50; at historical low → historicalLowScore=50; total=100 > 70
        int score = scoringService.score(product(new BigDecimal("50"), new BigDecimal("100")), Optional.of(new BigDecimal("50")));
        assertThat(score).isGreaterThan(70);
    }

    // --- helper ---

    private DiscoveredProduct product(BigDecimal currentPrice, BigDecimal listPrice) {
        return DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("TEST-001")
                .currentPrice(currentPrice)
                .listPrice(listPrice)
                .build();
    }
}
