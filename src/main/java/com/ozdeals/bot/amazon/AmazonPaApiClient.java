package com.ozdeals.bot.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
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

    private static final String PATH = "/paapi5/searchitems";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AmazonPaApiClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<DiscoveredProduct> searchItems(String keyword) {
        String body = buildRequestBody(keyword);
        Map<String, String> signedHeaders = AwsV4Signer.sign(accessKey, secretKey, region, host, PATH, body);

        HttpHeaders headers = new HttpHeaders();
        signedHeaders.forEach(headers::set);

        String url = "https://" + host + PATH;
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            String response = restTemplate.postForObject(url, entity, String.class);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("PA-API request failed for keyword '{}': {}", keyword, e.getMessage());
            return List.of();
        }
    }

    private String buildRequestBody(String keyword) {
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
                  "ItemCount": 10
                }
                """.formatted(keyword, marketplace, partnerTag);
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
