package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.source.ProductSourceClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductDiscoveryServiceTest {

    private ProductDiscoveryService serviceWith(ProductSourceClient... clients) {
        return new ProductDiscoveryService(List.of(clients));
    }

    private DiscoveredProduct product(ProductSource source, String externalId) {
        return DiscoveredProduct.builder()
                .source(source)
                .externalId(externalId)
                .title("Test Product")
                .imageUrl("https://example.com/img.jpg")
                .affiliateLink("https://example.com/link")
                .currentPrice(new BigDecimal("99.00"))
                .build();
    }

    @Test
    void discoverAll_duplicateSourceAndExternalId_deduplicatesKeepsFirst() {
        DiscoveredProduct first = product(ProductSource.MOCK, "MOCK_001");
        DiscoveredProduct duplicate = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("MOCK_001")
                .title("Different Title")
                .imageUrl("https://example.com/img2.jpg")
                .affiliateLink("https://example.com/link2")
                .currentPrice(new BigDecimal("79.00"))
                .build();

        ProductDiscoveryService service = serviceWith(
                () -> List.of(first),
                () -> List.of(duplicate)
        );

        List<DiscoveredProduct> result = service.discoverAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Product");
    }

    @Test
    void discoverAll_sameExternalIdDifferentSources_notDeduplicated() {
        DiscoveredProduct amazon = product(ProductSource.AMAZON, "SHARED_ID");
        DiscoveredProduct mock = product(ProductSource.MOCK, "SHARED_ID");

        ProductDiscoveryService service = serviceWith(
                () -> List.of(amazon),
                () -> List.of(mock)
        );

        List<DiscoveredProduct> result = service.discoverAll();

        assertThat(result).hasSize(2);
    }

    @Test
    void discoverAll_nullSource_skipped() {
        DiscoveredProduct invalid = DiscoveredProduct.builder()
                .source(null)
                .externalId("SOME_ID")
                .title("Product")
                .currentPrice(new BigDecimal("50.00"))
                .build();
        DiscoveredProduct valid = product(ProductSource.MOCK, "MOCK_001");

        ProductDiscoveryService service = serviceWith(() -> List.of(invalid, valid));

        List<DiscoveredProduct> result = service.discoverAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalId()).isEqualTo("MOCK_001");
    }

    @Test
    void discoverAll_blankExternalId_skipped() {
        DiscoveredProduct invalid = DiscoveredProduct.builder()
                .source(ProductSource.MOCK)
                .externalId("  ")
                .title("Product")
                .currentPrice(new BigDecimal("50.00"))
                .build();
        DiscoveredProduct valid = product(ProductSource.MOCK, "MOCK_001");

        ProductDiscoveryService service = serviceWith(() -> List.of(invalid, valid));

        List<DiscoveredProduct> result = service.discoverAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getExternalId()).isEqualTo("MOCK_001");
    }
}
