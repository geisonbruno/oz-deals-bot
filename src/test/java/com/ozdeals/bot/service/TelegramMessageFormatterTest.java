package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    private final TelegramMessageFormatter formatter = new TelegramMessageFormatter();

    @Test
    void format_withListPrice_includesAllSections() {
        DiscoveredProduct product = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_IPHONE_13")
                .title("Apple iPhone 13 128GB Blue")
                .currentPrice(new BigDecimal("999.00"))
                .listPrice(new BigDecimal("1399.00"))
                .affiliateLink("https://amzn.to/abc123")
                .imageUrl("https://example.com/img.jpg")
                .build();

        String result = formatter.format(product, 28);

        assertThat(result).contains("🔥 28% OFF");
        assertThat(result).contains("Apple iPhone 13 128GB Blue");
        assertThat(result).contains("💰 Now: $999.00");
        assertThat(result).contains("💸 Was: $1399.00");
        assertThat(result).contains("👉 https://amzn.to/abc123");
    }

    @Test
    void format_withoutListPrice_omitsWasLine() {
        DiscoveredProduct product = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_SONY_XM4")
                .title("Sony WH-1000XM4")
                .currentPrice(new BigDecimal("279.00"))
                .listPrice(null)
                .affiliateLink("https://amzn.to/xyz789")
                .imageUrl("https://example.com/img.jpg")
                .build();

        String result = formatter.format(product, 0);

        assertThat(result).doesNotContain("💸 Was:");
        assertThat(result).contains("💰 Now: $279.00");
    }

    @Test
    void format_discountPercentAppearsCorrectly() {
        DiscoveredProduct product = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_TV")
                .title("Samsung TV")
                .currentPrice(new BigDecimal("1199.00"))
                .listPrice(new BigDecimal("1999.00"))
                .affiliateLink("https://amzn.to/tv")
                .build();

        assertThat(formatter.format(product, 40)).contains("🔥 40% OFF");
        assertThat(formatter.format(product, 50)).contains("🔥 50% OFF");
    }

    @Test
    void format_affiliateLinkIncluded() {
        DiscoveredProduct product = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_001")
                .title("Some Product")
                .currentPrice(new BigDecimal("100.00"))
                .affiliateLink("https://amzn.to/uniquelink")
                .build();

        assertThat(formatter.format(product, 20)).contains("https://amzn.to/uniquelink");
    }

    @Test
    void format_messageStructureOrder() {
        DiscoveredProduct product = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_001")
                .title("Test Product")
                .currentPrice(new BigDecimal("50.00"))
                .listPrice(new BigDecimal("100.00"))
                .affiliateLink("https://amzn.to/test")
                .build();

        String result = formatter.format(product, 50);

        int discountPos = result.indexOf("🔥");
        int titlePos = result.indexOf("Test Product");
        int pricePos = result.indexOf("💰");
        int wasPos = result.indexOf("💸");
        int linkPos = result.indexOf("👉");

        assertThat(discountPos).isLessThan(titlePos);
        assertThat(titlePos).isLessThan(pricePos);
        assertThat(pricePos).isLessThan(wasPos);
        assertThat(wasPos).isLessThan(linkPos);
    }
}
