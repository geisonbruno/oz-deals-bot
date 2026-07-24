package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.amazon.AmazonAsinExtractor;
import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ManualAmazonDealValidator {

    private static final Set<String> ALLOWED_AFFILIATE_HOSTS = Set.of(
            "amazon.com.au", "www.amazon.com.au", "amzn.to"
    );

    private final AmazonAsinExtractor asinExtractor;

    public ManualAmazonDealValidator(AmazonAsinExtractor asinExtractor) {
        this.asinExtractor = asinExtractor;
    }

    public ManualDealValidationResult validate(Deal deal) {
        List<String> errors = new ArrayList<>();

        if (deal == null) {
            errors.add("Deal is required");
            return new ManualDealValidationResult(errors);
        }

        validateProduct(deal.getProduct(), errors);

        if (isBlank(deal.getSourceUrl()) || asinExtractor.extractAsin(deal.getSourceUrl()).isEmpty()) {
            errors.add("sourceUrl must be a supported Amazon Australia product URL");
        }

        if (isBlank(deal.getAffiliateUrl()) || !hasAllowedAffiliateHost(deal.getAffiliateUrl())) {
            errors.add("affiliateUrl must be present and use an accepted Amazon Australia or official short-link host");
        }

        if (isBlank(deal.getShortDescription())) {
            errors.add("shortDescription must not be blank");
        }

        BigDecimal currentPrice = deal.getCurrentPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("currentPrice must be greater than zero");
        }

        BigDecimal originalPrice = deal.getOriginalPrice();
        if (originalPrice == null || currentPrice == null || originalPrice.compareTo(currentPrice) <= 0) {
            errors.add("originalPrice must be greater than currentPrice");
        }

        if (isBlank(deal.getImageUrl()) && isBlank(deal.getTelegramImageFileId())) {
            errors.add("Either imageUrl or telegramImageFileId must be present");
        }

        if (deal.getExpiresAt() != null && !deal.getExpiresAt().isAfter(LocalDateTime.now())) {
            errors.add("expiresAt must be in the future");
        }

        return new ManualDealValidationResult(errors);
    }

    private void validateProduct(Product product, List<String> errors) {
        if (product == null) {
            errors.add("Deal must reference a Product");
            return;
        }

        if (product.getSource() != ProductSource.AMAZON) {
            errors.add("Product source must be AMAZON");
        }

        if (!asinExtractor.isValidAsin(product.getExternalId())) {
            errors.add("Product externalId must be a valid ASIN");
        }

        if (isBlank(product.getTitle())) {
            errors.add("Product title must not be blank");
        }
    }

    private boolean hasAllowedAffiliateHost(String url) {
        try {
            String host = new URI(url.trim()).getHost();
            return host != null && ALLOWED_AFFILIATE_HOSTS.contains(host.toLowerCase());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
