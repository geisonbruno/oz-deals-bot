package com.ozdeals.bot.source;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@ConditionalOnProperty(name = "sources.mock.enabled", havingValue = "true")
@Slf4j
public class MockProductSourceClient implements ProductSourceClient {

    @Override
    public List<DiscoveredProduct> discoverProducts() {
        log.info("[Mock] Returning mock products for local testing");
        return List.of(
                DiscoveredProduct.builder()
                        .source(ProductSource.MOCK)
                        .externalId("MOCK_IPHONE_13")
                        .title("Apple iPhone 13 128GB Blue")
                        .brand("Apple")
                        .category("Smartphones")
                        .imageUrl("https://m.media-amazon.com/images/I/placeholder.jpg")
                        .affiliateLink("https://www.amazon.com.au/dp/B09G3HRMVB?tag=test-tag-22")
                        .currentPrice(new BigDecimal("999.00"))
                        .listPrice(new BigDecimal("1399.00"))
                        .build(),
                DiscoveredProduct.builder()
                        .source(ProductSource.MOCK)
                        .externalId("MOCK_SONY_XM4")
                        .title("Sony WH-1000XM4 Wireless Noise Cancelling Headphones")
                        .brand("Sony")
                        .category("Headphones")
                        .imageUrl("https://m.media-amazon.com/images/I/placeholder2.jpg")
                        .affiliateLink("https://www.amazon.com.au/dp/B08N5WRWNW?tag=test-tag-22")
                        .currentPrice(new BigDecimal("279.00"))
                        .listPrice(new BigDecimal("549.00"))
                        .build(),
                DiscoveredProduct.builder()
                        .source(ProductSource.MOCK)
                        .externalId("MOCK_SAMSUNG_TV")
                        .title("Samsung 65-Inch 4K QLED TV")
                        .brand("Samsung")
                        .category("Televisions")
                        .imageUrl("https://m.media-amazon.com/images/I/placeholder3.jpg")
                        .affiliateLink("https://www.amazon.com.au/dp/B07ZPKN6YR?tag=test-tag-22")
                        .currentPrice(new BigDecimal("1199.00"))
                        .listPrice(new BigDecimal("1999.00"))
                        .build()
        );
    }
}
