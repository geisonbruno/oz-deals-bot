package com.ozdeals.bot.source;

import com.ozdeals.bot.amazon.AmazonPaApiClient;
import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "sources.amazon.enabled", havingValue = "true")
@Slf4j
public class AmazonProductSourceClient implements ProductSourceClient {

    private static final List<String> KEYWORDS = List.of(
            "iphone", "samsung", "sony", "headphones", "tv", "laptop",
            "gaming monitor", "air fryer", "protein", "creatine", "fitness", "smartwatch"
    );

    private final AmazonPaApiClient amazonPaApiClient;

    public AmazonProductSourceClient(AmazonPaApiClient amazonPaApiClient) {
        this.amazonPaApiClient = amazonPaApiClient;
    }

    @Override
    public List<DiscoveredProduct> discoverProducts() {
        List<DiscoveredProduct> all = new ArrayList<>();
        for (String keyword : KEYWORDS) {
            try {
                List<DiscoveredProduct> products = amazonPaApiClient.searchItems(keyword);
                all.addAll(products);
                log.info("[Amazon] Discovered {} products for keyword '{}'", products.size(), keyword);
            } catch (Exception e) {
                log.error("[Amazon] Failed to search keyword '{}': {}", keyword, e.getMessage());
            }
        }
        return all;
    }
}
