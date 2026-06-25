package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.entity.PriceHistory;
import com.ozdeals.bot.entity.Product;
import com.ozdeals.bot.repository.PriceHistoryRepository;
import com.ozdeals.bot.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PriceTrackingService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public PriceTrackingService(ProductRepository productRepository, PriceHistoryRepository priceHistoryRepository) {
        this.productRepository = productRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    public Product saveProduct(DiscoveredProduct discovered) {
        return productRepository.findBySourceAndExternalId(discovered.getSource(), discovered.getExternalId())
                .orElseGet(() -> productRepository.save(Product.builder()
                        .source(discovered.getSource())
                        .externalId(discovered.getExternalId())
                        .title(discovered.getTitle())
                        .brand(discovered.getBrand())
                        .category(discovered.getCategory())
                        .imageUrl(discovered.getImageUrl())
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    public void recordPrice(Long productId, BigDecimal price) {
        if (price == null) return;
        priceHistoryRepository.save(PriceHistory.builder()
                .productId(productId)
                .price(price)
                .timestamp(LocalDateTime.now())
                .build());
    }

    public Optional<BigDecimal> getHistoricalLow(Long productId) {
        return priceHistoryRepository.findMinPriceByProductId(productId);
    }
}
