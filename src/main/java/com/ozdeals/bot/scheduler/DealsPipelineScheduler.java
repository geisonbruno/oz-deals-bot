package com.ozdeals.bot.scheduler;

import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.entity.Post;
import com.ozdeals.bot.entity.Product;
import com.ozdeals.bot.repository.PostRepository;
import com.ozdeals.bot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class DealsPipelineScheduler {

    private final ProductDiscoveryService productDiscoveryService;
    private final PriceTrackingService priceTrackingService;
    private final ScoringService scoringService;
    private final ValidationService validationService;
    private final TelegramPublisherService telegramPublisherService;
    private final PostRepository postRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public DealsPipelineScheduler(ProductDiscoveryService productDiscoveryService,
                                  PriceTrackingService priceTrackingService,
                                  ScoringService scoringService,
                                  ValidationService validationService,
                                  TelegramPublisherService telegramPublisherService,
                                  PostRepository postRepository) {
        this.productDiscoveryService = productDiscoveryService;
        this.priceTrackingService = priceTrackingService;
        this.scoringService = scoringService;
        this.validationService = validationService;
        this.telegramPublisherService = telegramPublisherService;
        this.postRepository = postRepository;
    }

    @Scheduled(cron = "${deals.scheduler.cron}")
    public void runPipeline() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Pipeline already running — skipping this execution");
            return;
        }
        try {
            log.info("Pipeline started");
            List<DiscoveredProduct> products = productDiscoveryService.discoverAll();
            int published = 0;

            for (DiscoveredProduct discovered : products) {
                try {
                    if (!validationService.isValid(discovered)) continue;

                    Product product = priceTrackingService.saveProduct(discovered);

                    priceTrackingService.recordPrice(product.getId(), discovered.getCurrentPrice());

                    if (wasRecentlyPosted(product.getId())) continue;

                    Optional<BigDecimal> historicalLow = priceTrackingService.getHistoricalLow(product.getId());
                    int score = scoringService.score(discovered, historicalLow);

                    if (score < 70) continue;

                    int discountPercent = scoringService.discountPercent(discovered);
                    if (telegramPublisherService.publish(discovered, discountPercent)) {
                        savePost(product, discovered);
                        published++;
                    }

                } catch (Exception e) {
                    log.error("Error processing [{}:{}]: {}", discovered.getSource(), discovered.getExternalId(), e.getMessage());
                }
            }

            log.info("Pipeline complete — processed {}, published {}", products.size(), published);
        } finally {
            running.set(false);
        }
    }

    private boolean wasRecentlyPosted(Long productId) {
        return postRepository.findTopByProductIdAndPostedAtAfter(productId, LocalDateTime.now().minusHours(24)).isPresent();
    }

    private void savePost(Product product, DiscoveredProduct discovered) {
        postRepository.save(Post.builder()
                .productId(product.getId())
                .price(discovered.getCurrentPrice())
                .postedAt(LocalDateTime.now())
                .messageHash(product.getSource() + "_" + product.getExternalId() + "_" + discovered.getCurrentPrice())
                .build());
    }
}
