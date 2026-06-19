package com.ozdeals.bot.scheduler;

import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.entity.Post;
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

            for (DiscoveredProduct product : products) {
                try {
                    priceTrackingService.saveProduct(product);

                    if (!validationService.isValid(product)) continue;

                    priceTrackingService.recordPrice(product);

                    if (wasRecentlyPosted(product.getAsin())) continue;

                    Optional<BigDecimal> historicalLow = priceTrackingService.getHistoricalLow(product.getAsin());
                    int score = scoringService.score(product, historicalLow);

                    if (score < 70) continue;

                    int discountPercent = scoringService.discountPercent(product);
                    telegramPublisherService.publish(product, discountPercent);
                    savePost(product);
                    published++;

                } catch (Exception e) {
                    log.error("Error processing ASIN {}: {}", product.getAsin(), e.getMessage());
                }
            }

            log.info("Pipeline complete — processed {}, published {}", products.size(), published);
        } finally {
            running.set(false);
        }
    }

    private boolean wasRecentlyPosted(String asin) {
        return postRepository.findTopByAsinAndPostedAtAfter(asin, LocalDateTime.now().minusHours(24)).isPresent();
    }

    private void savePost(DiscoveredProduct product) {
        postRepository.save(Post.builder()
                .asin(product.getAsin())
                .price(product.getCurrentPrice())
                .postedAt(LocalDateTime.now())
                .messageHash(product.getAsin() + "_" + product.getCurrentPrice())
                .build());
    }
}
