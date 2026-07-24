package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.amazon.AmazonAsinExtractor;
import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ManualAmazonDealValidatorTest {

    private final ManualAmazonDealValidator validator = new ManualAmazonDealValidator(new AmazonAsinExtractor());

    private Product.ProductBuilder validProduct() {
        return Product.builder()
                .source(ProductSource.AMAZON)
                .externalId("B0ABCDEFGH")
                .title("Apple iPhone 13 128GB Blue");
    }

    private Deal.DealBuilder validDeal() {
        return Deal.builder()
                .product(validProduct().build())
                .sourceUrl("https://www.amazon.com.au/dp/B0ABCDEFGH")
                .affiliateUrl("https://www.amazon.com.au/dp/B0ABCDEFGH?tag=test-22")
                .shortDescription("A great phone at a great price")
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1399.00"))
                .imageUrl("https://example.com/image.jpg");
    }

    @Test
    void validate_fullyValidDeal_hasNoErrors() {
        ManualDealValidationResult result = validator.validate(validDeal().build());

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void validate_invalidAsin_hasError() {
        Deal deal = validDeal()
                .product(validProduct().externalId("TOO_SHORT").build())
                .build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("ASIN"));
    }

    @Test
    void validate_invalidSourceUrl_hasError() {
        Deal deal = validDeal().sourceUrl("https://www.amazon.com/dp/B0ABCDEFGH").build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("sourceUrl"));
    }

    @Test
    void validate_invalidAffiliateUrl_hasError() {
        Deal deal = validDeal().affiliateUrl("https://untrusted-host.com/deal/B0ABCDEFGH").build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("affiliateUrl"));
    }

    @Test
    void validate_missingTitle_hasError() {
        Deal deal = validDeal()
                .product(validProduct().title(" ").build())
                .build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("title"));
    }

    @Test
    void validate_missingDescription_hasError() {
        Deal deal = validDeal().shortDescription(" ").build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("shortDescription"));
    }

    @Test
    void validate_invalidCurrentPrice_hasError() {
        Deal deal = validDeal().currentPrice(BigDecimal.ZERO).build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("currentPrice"));
    }

    @Test
    void validate_originalPriceNotGreaterThanCurrent_hasError() {
        Deal deal = validDeal()
                .currentPrice(new BigDecimal("100.00"))
                .originalPrice(new BigDecimal("100.00"))
                .build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("originalPrice"));
    }

    @Test
    void validate_missingImage_hasError() {
        Deal deal = validDeal().imageUrl(null).telegramImageFileId(null).build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("imageUrl") || e.contains("telegramImageFileId"));
    }

    @Test
    void validate_expirationInThePast_hasError() {
        Deal deal = validDeal().expiresAt(LocalDateTime.now().minusDays(1)).build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("expiresAt"));
    }

    @Test
    void validate_telegramImageFileIdOnly_isValid() {
        Deal deal = validDeal().imageUrl(null).telegramImageFileId("AgACAgEAAxk...").build();

        ManualDealValidationResult result = validator.validate(deal);

        assertThat(result.isValid()).isTrue();
    }
}
