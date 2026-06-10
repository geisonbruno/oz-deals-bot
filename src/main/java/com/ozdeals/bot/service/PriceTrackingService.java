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

    public void saveProduct(DiscoveredProduct discovered) {
        if (!productRepository.existsById(discovered.getAsin())) {
            productRepository.save(Product.builder()
                    .asin(discovered.getAsin())
                    .title(discovered.getTitle())
                    .brand(discovered.getBrand())
                    .category(discovered.getCategory())
                    .imageUrl(discovered.getImageUrl())
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    public void recordPrice(DiscoveredProduct discovered) {
        if (discovered.getCurrentPrice() == null) return;
        priceHistoryRepository.save(PriceHistory.builder()
                .asin(discovered.getAsin())
                .price(discovered.getCurrentPrice())
                .timestamp(LocalDateTime.now())
                .build());
    }

    public Optional<BigDecimal> getHistoricalLow(String asin) {
        return priceHistoryRepository.findMinPriceByAsin(asin);
    }
}
