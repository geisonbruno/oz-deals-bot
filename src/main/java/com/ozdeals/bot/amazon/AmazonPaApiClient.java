package com.ozdeals.bot.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "sources.amazon.enabled", havingValue = "true")
@Slf4j
public class AmazonPaApiClient {

    @Value("${amazon.paapi.access-key}")
    private String accessKey;

    @Value("${amazon.paapi.secret-key}")
    private String secretKey;

    @Value("${amazon.paapi.partner-tag}")
    private String partnerTag;

    @Value("${amazon.paapi.host}")
    private String host;

    @Value("${amazon.paapi.region}")
    private String region;

    @Value("${amazon.paapi.marketplace}")
    private String marketplace;

    @Value("${amazon.paapi.max-pages:1}")
    private int maxPages;

    private static final String PATH = "/paapi5/searchitems";
    private static final int MAX_RETRIES = 3;
    private static final long MIN_INTERVAL_MS = 1000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastRequestTimeMs = new AtomicLong(0);

    public AmazonPaApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DiscoveredProduct> searchItems(String keyword) {
        List<DiscoveredProduct> all = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            List<DiscoveredProduct> pageResults = searchPage(keyword, page);
            all.addAll(pageResults);
            if (pageResults.size() < 10) break; // fewer than full page means no more results
        }
        return all;
    }

    private List<DiscoveredProduct> searchPage(String keyword, int page) {
        int attempt = 0;
        while (attempt <= MAX_RETRIES) {
            try {
                enforceRateLimit();
                String body = buildRequestBody(keyword, page);
                Map<String, String> signedHeaders = AwsV4Signer.sign(accessKey, secretKey, region, host, PATH, body);
                HttpHeaders headers = new HttpHeaders();
                signedHeaders.forEach(headers::set);
                HttpEntity<String> entity = new HttpEntity<>(body, headers);
                String url = "https://" + host + PATH;
                String response = restTemplate.postForObject(url, entity, String.class);
                return parseResponse(response);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < MAX_RETRIES) {
                    attempt++;
                    log.warn("PA-API throttled for keyword '{}' page {}, retrying ({}/{})", keyword, page, attempt, MAX_RETRIES);
                    sleepBackoff(attempt);
                } else {
                    log.error("PA-API request failed for keyword '{}' page {}: {}", keyword, page, e.getMessage());
                    return List.of();
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    attempt++;
                    log.warn("PA-API attempt {}/{} failed for keyword '{}' page {}: {}", attempt, MAX_RETRIES, keyword, page, e.getMessage());
                    sleepBackoff(attempt);
                } else {
                    log.error("PA-API request failed after {} attempts for keyword '{}' page {}: {}", MAX_RETRIES + 1, keyword, page, e.getMessage());
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private synchronized void enforceRateLimit() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTimeMs.get();
        if (elapsed < MIN_INTERVAL_MS) {
            try {
                Thread.sleep(MIN_INTERVAL_MS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestTimeMs.set(System.currentTimeMillis());
    }

    private void sleepBackoff(int attempt) {
        long delay = (long) Math.pow(2, attempt - 1) * 1000L; // 1s, 2s, 4s
        log.warn("Backing off for {}ms before retry", delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildRequestBody(String keyword, int page) {
        return """
                {
                  "Keywords": "%s",
                  "Marketplace": "%s",
                  "PartnerTag": "%s",
                  "PartnerType": "Associates",
                  "Resources": [
                    "ItemInfo.Title",
                    "ItemInfo.ByLineInfo",
                    "Offers.Listings.Price",
                    "Offers.Listings.SavingBasis",
                    "Images.Primary.Large",
                    "BrowseNodeInfo.BrowseNodes",
                    "DetailPageURL"
                  ],
                  "SearchIndex": "All",
                  "ItemCount": 10,
                  "ItemPage": %d
                }
                """.formatted(keyword, marketplace, partnerTag, page);
    }

    private List<DiscoveredProduct> parseResponse(String json) throws Exception {
        List<DiscoveredProduct> products = new ArrayList<>();
        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("SearchResult").path("Items");

        if (!items.isArray()) return products;

        for (JsonNode item : items) {
            try {
                products.add(normalize(item));
            } catch (Exception e) {
                log.warn("Skipping item due to parse error: {}", e.getMessage());
            }
        }
        return products;
    }

    private DiscoveredProduct normalize(JsonNode item) {
        String asin = item.path("ASIN").asText(null);
        String detailPageUrl = item.path("DetailPageURL").asText(null);
        String title = item.path("ItemInfo").path("Title").path("DisplayValue").asText(null);
        String brand = item.path("ItemInfo").path("ByLineInfo").path("Brand").path("DisplayValue").asText(null);
        String imageUrl = item.path("Images").path("Primary").path("Large").path("URL").asText(null);

        String category = null;
        JsonNode browseNodes = item.path("BrowseNodeInfo").path("BrowseNodes");
        if (browseNodes.isArray() && !browseNodes.isEmpty()) {
            category = browseNodes.get(0).path("DisplayName").asText(null);
        }

        BigDecimal currentPrice = null;
        BigDecimal listPrice = null;
        JsonNode listings = item.path("Offers").path("Listings");
        if (listings.isArray() && !listings.isEmpty()) {
            JsonNode listing = listings.get(0);
            double priceAmount = listing.path("Price").path("Amount").asDouble(0);
            if (priceAmount > 0) currentPrice = BigDecimal.valueOf(priceAmount);
            double savingBasis = listing.path("SavingBasis").path("Amount").asDouble(0);
            if (savingBasis > 0) listPrice = BigDecimal.valueOf(savingBasis);
        }

        return DiscoveredProduct.builder()
                .asin(asin)
                .title(title)
                .brand(brand)
                .category(category)
                .imageUrl(imageUrl)
                .affiliateLink(detailPageUrl)
                .currentPrice(currentPrice)
                .listPrice(listPrice)
                .build();
    }
}
