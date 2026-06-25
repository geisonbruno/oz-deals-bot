package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationServiceTest {

    private final ValidationService validationService = new ValidationService();

    // --- valid product ---

    @Test
    void isValid_allFieldsPresent_returnsTrue() {
        assertThat(validationService.isValid(validProduct())).isTrue();
    }

    // --- source ---

    @Test
    void isValid_nullSource_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(null)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- externalId ---

    @Test
    void isValid_nullExternalId_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId(null)
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_blankExternalId_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("  ")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- title ---

    @Test
    void isValid_nullTitle_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title(null)
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_blankTitle_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- currentPrice ---

    @Test
    void isValid_nullCurrentPrice_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(null)
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_zeroCurrentPrice_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(BigDecimal.ZERO)
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_negativeCurrentPrice_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("-1"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- imageUrl ---

    @Test
    void isValid_nullImageUrl_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl(null)
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_blankImageUrl_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("  ")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- affiliateLink ---

    @Test
    void isValid_nullAffiliateLink_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink(null)
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    @Test
    void isValid_blankAffiliateLink_returnsFalse() {
        DiscoveredProduct p = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("")
                .currentPrice(new BigDecimal("999.00"))
                .build();
        assertThat(validationService.isValid(p)).isFalse();
    }

    // --- helper ---

    private DiscoveredProduct validProduct() {
        return DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13")
                .imageUrl("https://example.com/image.jpg")
                .affiliateLink("https://amzn.to/abc123")
                .currentPrice(new BigDecimal("999.00"))
                .build();
    }
}
