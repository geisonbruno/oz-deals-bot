package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.entity.PriceHistory;
import com.ozdeals.bot.entity.Product;
import com.ozdeals.bot.repository.PriceHistoryRepository;
import com.ozdeals.bot.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceTrackingServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private PriceTrackingService priceTrackingService;

    // --- saveProduct: new product ---

    @Test
    void saveProduct_newProduct_persistsAndReturns() {
        DiscoveredProduct discovered = discovered(ProductSource.MOCK, "MOCK_001");
        Product saved = entity(1L, ProductSource.MOCK, "MOCK_001");

        when(productRepository.findBySourceAndExternalId(ProductSource.MOCK, "MOCK_001")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        Product result = priceTrackingService.saveProduct(discovered);

        assertThat(result).isEqualTo(saved);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void saveProduct_newProduct_mapsFieldsCorrectly() {
        DiscoveredProduct discovered = DiscoveredProduct.builder()
                .source(ProductSource.AMAZON)
                .externalId("B09G3HRMVB")
                .title("Apple iPhone 13")
                .brand("Apple")
                .category("Smartphones")
                .imageUrl("https://example.com/img.jpg")
                .currentPrice(new BigDecimal("999.00"))
                .build();

        when(productRepository.findBySourceAndExternalId(ProductSource.AMAZON, "B09G3HRMVB")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = priceTrackingService.saveProduct(discovered);

        assertThat(result.getSource()).isEqualTo(ProductSource.AMAZON);
        assertThat(result.getExternalId()).isEqualTo("B09G3HRMVB");
        assertThat(result.getTitle()).isEqualTo("Apple iPhone 13");
        assertThat(result.getBrand()).isEqualTo("Apple");
        assertThat(result.getCategory()).isEqualTo("Smartphones");
        assertThat(result.getImageUrl()).isEqualTo("https://example.com/img.jpg");
        assertThat(result.getCreatedAt()).isNotNull();
    }

    // --- saveProduct: existing product ---

    @Test
    void saveProduct_existingProduct_returnsExistingWithoutSaving() {
        DiscoveredProduct discovered = discovered(ProductSource.MOCK, "MOCK_001");
        Product existing = entity(1L, ProductSource.MOCK, "MOCK_001");

        when(productRepository.findBySourceAndExternalId(ProductSource.MOCK, "MOCK_001")).thenReturn(Optional.of(existing));

        Product result = priceTrackingService.saveProduct(discovered);

        assertThat(result).isEqualTo(existing);
        verify(productRepository, never()).save(any());
    }

    // --- saveProduct: cross-source identity ---

    @Test
    void saveProduct_sameExternalIdDifferentSources_createsSeparateProducts() {
        when(productRepository.findBySourceAndExternalId(ProductSource.AMAZON, "SHARED_ID")).thenReturn(Optional.empty());
        when(productRepository.findBySourceAndExternalId(ProductSource.MOCK, "SHARED_ID")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        priceTrackingService.saveProduct(discovered(ProductSource.AMAZON, "SHARED_ID"));
        priceTrackingService.saveProduct(discovered(ProductSource.MOCK, "SHARED_ID"));

        verify(productRepository, times(2)).save(any(Product.class));
    }

    @Test
    void saveProduct_sameSourceAndExternalId_reusesExistingProduct() {
        Product existing = entity(1L, ProductSource.MOCK, "MOCK_001");

        when(productRepository.findBySourceAndExternalId(ProductSource.MOCK, "MOCK_001"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(productRepository.save(any())).thenReturn(existing);

        priceTrackingService.saveProduct(discovered(ProductSource.MOCK, "MOCK_001"));
        priceTrackingService.saveProduct(discovered(ProductSource.MOCK, "MOCK_001"));

        verify(productRepository, times(1)).save(any(Product.class));
    }

    // --- recordPrice ---

    @Test
    void recordPrice_validPrice_savesHistoryWithCorrectFields() {
        priceTrackingService.recordPrice(42L, new BigDecimal("279.00"));

        ArgumentCaptor<PriceHistory> captor = ArgumentCaptor.forClass(PriceHistory.class);
        verify(priceHistoryRepository).save(captor.capture());

        PriceHistory saved = captor.getValue();
        assertThat(saved.getProductId()).isEqualTo(42L);
        assertThat(saved.getPrice()).isEqualByComparingTo("279.00");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void recordPrice_nullPrice_doesNotSave() {
        priceTrackingService.recordPrice(1L, null);

        verify(priceHistoryRepository, never()).save(any());
    }

    // --- getHistoricalLow ---

    @Test
    void getHistoricalLow_noHistory_returnsEmpty() {
        when(priceHistoryRepository.findMinPriceByProductId(1L)).thenReturn(Optional.empty());

        assertThat(priceTrackingService.getHistoricalLow(1L)).isEmpty();
    }

    @Test
    void getHistoricalLow_withHistory_returnsMinimumPrice() {
        when(priceHistoryRepository.findMinPriceByProductId(1L)).thenReturn(Optional.of(new BigDecimal("79.00")));

        assertThat(priceTrackingService.getHistoricalLow(1L)).hasValue(new BigDecimal("79.00"));
    }

    // --- helpers ---

    private DiscoveredProduct discovered(ProductSource source, String externalId) {
        return DiscoveredProduct.builder()
                .source(source)
                .externalId(externalId)
                .title("Test Product")
                .currentPrice(new BigDecimal("99.00"))
                .build();
    }

    private Product entity(Long id, ProductSource source, String externalId) {
        return Product.builder()
                .id(id)
                .source(source)
                .externalId(externalId)
                .title("Test Product")
                .build();
    }
}
