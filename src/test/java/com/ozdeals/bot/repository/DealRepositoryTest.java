package com.ozdeals.bot.repository;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.DealStatus;
import com.ozdeals.bot.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class DealRepositoryTest {

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ProductRepository productRepository;

    private Product persistProduct(String externalId) {
        return productRepository.save(Product.builder()
                .source(ProductSource.AMAZON)
                .externalId(externalId)
                .title("Apple iPhone 13 128GB Blue")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Deal.DealBuilder dealFor(Product product, String slug) {
        return Deal.builder()
                .product(product)
                .sourceUrl("https://www.amazon.com.au/dp/" + product.getExternalId())
                .affiliateUrl("https://www.amazon.com.au/dp/" + product.getExternalId() + "?tag=test-22")
                .shortDescription("A great deal")
                .currentPrice(new BigDecimal("999.00"))
                .originalPrice(new BigDecimal("1399.00"))
                .imageUrl("https://example.com/image.jpg")
                .slug(slug);
    }

    @Test
    void save_newDeal_defaultsToDraft() {
        Product product = persistProduct("B0ABCDEFGH");

        Deal saved = dealRepository.save(dealFor(product, "iphone-13-deal").build());

        assertThat(saved.getStatus()).isEqualTo(DealStatus.DRAFT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_deal_referencesProduct() {
        Product product = persistProduct("B0ABCDEFGH");

        Deal saved = dealRepository.save(dealFor(product, "iphone-13-deal").build());

        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
    }

    @Test
    void save_duplicateSlug_throwsConstraintViolation() {
        Product product = persistProduct("B0ABCDEFGH");
        dealRepository.saveAndFlush(dealFor(product, "iphone-13-deal").build());

        Deal duplicate = dealFor(product, "iphone-13-deal").build();

        assertThatThrownBy(() -> dealRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findBySlug_existingSlug_returnsDeal() {
        Product product = persistProduct("B0ABCDEFGH");
        dealRepository.save(dealFor(product, "iphone-13-deal").build());

        Optional<Deal> result = dealRepository.findBySlug("iphone-13-deal");

        assertThat(result).isPresent();
    }

    @Test
    void findBySlug_missingSlug_returnsEmpty() {
        Optional<Deal> result = dealRepository.findBySlug("does-not-exist");

        assertThat(result).isEmpty();
    }

    @Test
    void findByStatusOrderByCreatedAtDesc_returnsOnlyMatchingStatus() {
        Product product = persistProduct("B0ABCDEFGH");
        Deal draft = dealRepository.save(dealFor(product, "draft-deal").status(DealStatus.DRAFT).build());
        dealRepository.save(dealFor(product, "archived-deal").status(DealStatus.ARCHIVED).build());

        List<Deal> result = dealRepository.findByStatusOrderByCreatedAtDesc(DealStatus.DRAFT);

        assertThat(result).extracting(Deal::getId).containsExactly(draft.getId());
    }

    @Test
    void findByStatusAndExpiresAtBefore_returnsExpiredPublishedDeals() {
        Product product = persistProduct("B0ABCDEFGH");
        Deal expired = dealRepository.save(dealFor(product, "expired-deal")
                .status(DealStatus.PUBLISHED)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build());
        dealRepository.save(dealFor(product, "future-deal")
                .status(DealStatus.PUBLISHED)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build());

        List<Deal> result = dealRepository.findByStatusAndExpiresAtBefore(DealStatus.PUBLISHED, LocalDateTime.now());

        assertThat(result).extracting(Deal::getId).containsExactly(expired.getId());
    }

    @Test
    void findTopByProductIdOrderByCreatedAtDesc_returnsMostRecentDeal() throws InterruptedException {
        Product product = persistProduct("B0ABCDEFGH");
        dealRepository.saveAndFlush(dealFor(product, "older-deal").build());
        Thread.sleep(5);
        Deal newer = dealRepository.saveAndFlush(dealFor(product, "newer-deal").build());

        Optional<Deal> result = dealRepository.findTopByProduct_IdOrderByCreatedAtDesc(product.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(newer.getId());
    }
}
