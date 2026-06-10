package com.ozdeals.bot.service;

import com.ozdeals.bot.amazon.AmazonPaApiClient;
import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ProductDiscoveryService {

    private static final List<String> KEYWORDS = List.of(
            "iphone", "samsung", "sony", "headphones", "tv", "laptop",
            "gaming monitor", "air fryer", "protein", "creatine", "fitness", "smartwatch"
    );

    private final AmazonPaApiClient amazonPaApiClient;

    public ProductDiscoveryService(AmazonPaApiClient amazonPaApiClient) {
        this.amazonPaApiClient = amazonPaApiClient;
    }

    public List<DiscoveredProduct> discoverAll() {
        List<DiscoveredProduct> all = new ArrayList<>();
        for (String keyword : KEYWORDS) {
            try {
                List<DiscoveredProduct> products = amazonPaApiClient.searchItems(keyword);
                all.addAll(products);
                log.info("Discovered {} products for keyword '{}'", products.size(), keyword);
            } catch (Exception e) {
                log.error("Failed to search keyword '{}': {}", keyword, e.getMessage());
            }
        }
        return all;
    }
}
