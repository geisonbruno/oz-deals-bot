package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.amazon.AmazonAsinExtractor;
import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.DealStatus;
import com.ozdeals.bot.entity.ManualDealInputStep;
import com.ozdeals.bot.entity.Product;
import com.ozdeals.bot.entity.TelegramAdminConversation;
import com.ozdeals.bot.repository.DealRepository;
import com.ozdeals.bot.repository.ProductRepository;
import com.ozdeals.bot.repository.TelegramAdminConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ManualDealDraftServiceTest {

    private static final String VALID_SOURCE_URL = "https://www.amazon.com.au/dp/B0ABCDEFGH";
    private static final String INVALID_SOURCE_URL = "https://www.somewhere-else.com/product/123";
    private static final String VALID_ASIN = "B0ABCDEFGH";
    private static final String VALID_AFFILIATE_URL = "https://www.amazon.com.au/dp/B0ABCDEFGH?tag=test-22";
    private static final String TITLE = "Apple iPhone 13 128GB Blue";
    private static final String DESCRIPTION = "A great phone at a great price";
    private static final BigDecimal CURRENT_PRICE = new BigDecimal("999.00");
    private static final BigDecimal ORIGINAL_PRICE = new BigDecimal("1399.00");
    private static final String IMAGE_URL = "https://example.com/image.jpg";

    @Autowired
    private ManualDealDraftService draftService;
    @Autowired
    private DealRepository dealRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TelegramAdminConversationRepository conversationRepository;
    @Autowired
    private AmazonAsinExtractor asinExtractor;
    @Autowired
    private ManualAmazonDealValidator validator;
    @Autowired
    private DealPriceCalculator priceCalculator;
    @Autowired
    private ScoringService scoringService;
    @Autowired
    private PriceTrackingService priceTrackingService;

    @Test
    void startDraft_createsPersistedDraftAtSourceUrlStep() {
        DraftStepResult result = draftService.startDraft(1001L);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.SOURCE_URL);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getStatus()).isEqualTo(DealStatus.DRAFT);
        assertThat(conversationRepository.findByTelegramUserId(1001L)).isPresent();
    }

    @Test
    void startDraft_replacesPreviousUnfinishedDraft_onlyOneActiveConversationPerUser() {
        DraftStepResult first = draftService.startDraft(1002L);
        DraftStepResult second = draftService.startDraft(1002L);

        assertThat(conversationRepository.findByTelegramUserId(1002L)).isPresent();
        assertThat(dealRepository.findById(first.dealId())).isEmpty();
        assertThat(dealRepository.findById(second.dealId())).isPresent();
        assertThat(first.dealId()).isNotEqualTo(second.dealId());
    }

    @Test
    void submitSourceUrl_extractableAsin_advancesToAffiliateUrl_andCreatesProduct() {
        draftService.startDraft(1003L);

        DraftStepResult result = draftService.submitSourceUrl(1003L, VALID_SOURCE_URL);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.AFFILIATE_URL);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getProduct()).isNotNull();
        assertThat(deal.getProduct().getExternalId()).isEqualTo(VALID_ASIN);
        assertThat(deal.getProduct().getSource()).isEqualTo(ProductSource.AMAZON);
    }

    @Test
    void submitSourceUrl_extractableAsin_reusesExistingProduct() {
        Product existing = productRepository.save(Product.builder()
                .source(ProductSource.AMAZON)
                .externalId(VALID_ASIN)
                .createdAt(LocalDateTime.now())
                .build());

        draftService.startDraft(1004L);
        DraftStepResult result = draftService.submitSourceUrl(1004L, VALID_SOURCE_URL);

        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getProduct().getId()).isEqualTo(existing.getId());
        assertThat(productRepository.findAll())
                .filteredOn(p -> VALID_ASIN.equals(p.getExternalId()))
                .hasSize(1);
    }

    @Test
    void submitSourceUrl_failedExtraction_advancesToManualAsin() {
        draftService.startDraft(1005L);

        DraftStepResult result = draftService.submitSourceUrl(1005L, INVALID_SOURCE_URL);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.MANUAL_ASIN);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getSourceUrl()).isEqualTo(INVALID_SOURCE_URL);
        assertThat(deal.getProduct()).isNull();
    }

    @Test
    void submitManualAsin_valid_advancesToAffiliateUrl() {
        draftService.startDraft(1006L);
        draftService.submitSourceUrl(1006L, INVALID_SOURCE_URL);

        DraftStepResult result = draftService.submitManualAsin(1006L, VALID_ASIN);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.AFFILIATE_URL);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getProduct().getExternalId()).isEqualTo(VALID_ASIN);
    }

    @Test
    void submitManualAsin_invalid_doesNotAdvance() {
        draftService.startDraft(1007L);
        draftService.submitSourceUrl(1007L, INVALID_SOURCE_URL);

        DraftStepResult result = draftService.submitManualAsin(1007L, "TOO_SHORT");

        assertThat(result.success()).isFalse();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.MANUAL_ASIN);
        assertThat(conversationRepository.findByTelegramUserId(1007L).orElseThrow().getCurrentStep())
                .isEqualTo(ManualDealInputStep.MANUAL_ASIN);
    }

    @Test
    void submit_outOfOrderInput_isRejected() {
        draftService.startDraft(1008L);

        DraftStepResult result = draftService.submitAffiliateUrl(1008L, VALID_AFFILIATE_URL);

        assertThat(result.success()).isFalse();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.SOURCE_URL);
        assertThat(conversationRepository.findByTelegramUserId(1008L).orElseThrow().getCurrentStep())
                .isEqualTo(ManualDealInputStep.SOURCE_URL);
    }

    @Test
    void submitAffiliateUrl_progression() {
        Long userId = 1009L;
        advanceToAffiliateUrl(userId);

        DraftStepResult result = draftService.submitAffiliateUrl(userId, VALID_AFFILIATE_URL);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.TITLE);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getAffiliateUrl()).isEqualTo(VALID_AFFILIATE_URL);
    }

    @Test
    void submitTitle_updatesProductAndAdvances() {
        Long userId = 1010L;
        advanceToAffiliateUrl(userId);
        draftService.submitAffiliateUrl(userId, VALID_AFFILIATE_URL);

        DraftStepResult result = draftService.submitTitle(userId, TITLE);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.SHORT_DESCRIPTION);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getProduct().getTitle()).isEqualTo(TITLE);
    }

    @Test
    void submitShortDescription_progression() {
        Long userId = 1011L;
        advanceToTitle(userId);
        draftService.submitTitle(userId, TITLE);

        DraftStepResult result = draftService.submitShortDescription(userId, DESCRIPTION);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.CURRENT_PRICE);
    }

    @Test
    void submitCurrentPrice_invalid_isRejected() {
        Long userId = 1012L;
        advanceToCurrentPrice(userId);

        DraftStepResult result = draftService.submitCurrentPrice(userId, BigDecimal.ZERO);

        assertThat(result.success()).isFalse();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.CURRENT_PRICE);
    }

    @Test
    void submitCurrentPrice_valid_advancesToOriginalPrice() {
        Long userId = 1013L;
        advanceToCurrentPrice(userId);

        DraftStepResult result = draftService.submitCurrentPrice(userId, CURRENT_PRICE);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.ORIGINAL_PRICE);
    }

    @Test
    void submitOriginalPrice_invalid_isRejected() {
        Long userId = 1014L;
        advanceToCurrentPrice(userId);
        draftService.submitCurrentPrice(userId, CURRENT_PRICE);

        DraftStepResult result = draftService.submitOriginalPrice(userId, new BigDecimal("500.00"));

        assertThat(result.success()).isFalse();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.ORIGINAL_PRICE);
    }

    @Test
    void submitOriginalPrice_valid_advancesToImage() {
        Long userId = 1015L;
        advanceToCurrentPrice(userId);
        draftService.submitCurrentPrice(userId, CURRENT_PRICE);

        DraftStepResult result = draftService.submitOriginalPrice(userId, ORIGINAL_PRICE);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.IMAGE);
    }

    @Test
    void submitImageUrl_progression() {
        Long userId = 1016L;
        advanceToImage(userId);

        DraftStepResult result = draftService.submitImageUrl(userId, IMAGE_URL);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.EXPIRES_AT);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getImageUrl()).isEqualTo(IMAGE_URL);
    }

    @Test
    void submitTelegramImageFileId_progression() {
        Long userId = 1017L;
        advanceToImage(userId);

        DraftStepResult result = draftService.submitTelegramImageFileId(userId, "AgACAgEAAxk...");

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.EXPIRES_AT);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getTelegramImageFileId()).isEqualTo("AgACAgEAAxk...");
    }

    @Test
    void submitExpiresAt_valid_advancesToPreview() {
        Long userId = 1018L;
        advanceToExpiresAt(userId);

        DraftStepResult result = draftService.submitExpiresAt(userId, LocalDateTime.now().plusDays(3));

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.PREVIEW);
    }

    @Test
    void submitExpiresAt_pastDate_isRejected() {
        Long userId = 1099L;
        advanceToExpiresAt(userId);

        DraftStepResult result = draftService.submitExpiresAt(userId, LocalDateTime.now().minusDays(1));

        assertThat(result.success()).isFalse();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.EXPIRES_AT);
    }

    @Test
    void skipExpiration_advancesToPreview() {
        Long userId = 1019L;
        advanceToExpiresAt(userId);

        DraftStepResult result = draftService.skipExpiration(userId);

        assertThat(result.success()).isTrue();
        assertThat(result.currentStep()).isEqualTo(ManualDealInputStep.PREVIEW);
        Deal deal = dealRepository.findById(result.dealId()).orElseThrow();
        assertThat(deal.getExpiresAt()).isNull();
    }

    @Test
    void getDraftState_atPreview_calculatesPriceAndNoErrorsForCompleteDraft() {
        Long userId = 1020L;
        advanceToExpiresAt(userId);
        draftService.skipExpiration(userId);

        Optional<DraftState> state = draftService.getDraftState(userId);

        assertThat(state).isPresent();
        DraftState draftState = state.get();
        assertThat(draftState.currentStep()).isEqualTo(ManualDealInputStep.PREVIEW);
        assertThat(draftState.savingAmount()).isEqualByComparingTo("400.00");
        assertThat(draftState.discountPercentage()).isEqualByComparingTo("28.59");
        assertThat(draftState.validationErrors()).isEmpty();
        assertThat(draftState.score()).isNotNull();
    }

    @Test
    void getDraftState_atPreview_includesStructuredValidationErrorsForIncompleteDraft() {
        Long userId = 1021L;
        draftService.startDraft(userId);
        draftService.submitSourceUrl(userId, INVALID_SOURCE_URL);

        TelegramAdminConversation conversation = conversationRepository.findByTelegramUserId(userId).orElseThrow();
        conversation.setCurrentStep(ManualDealInputStep.PREVIEW);
        conversationRepository.save(conversation);

        Optional<DraftState> state = draftService.getDraftState(userId);

        assertThat(state).isPresent();
        assertThat(state.get().validationErrors()).isNotEmpty();
    }

    @Test
    void getDraftState_scoreCalculated_doesNotBlockDraft_dealStaysDraft() {
        Long userId = 1022L;
        advanceToExpiresAt(userId);
        draftService.skipExpiration(userId);

        Optional<DraftState> state = draftService.getDraftState(userId);

        assertThat(state.get().score()).isNotNull();
        Deal deal = dealRepository.findById(state.get().dealId()).orElseThrow();
        assertThat(deal.getStatus()).isEqualTo(DealStatus.DRAFT);
        assertThat(deal.getPublishedAt()).isNull();
    }

    @Test
    void cancelDraft_removesConversationAndDeletesDraftDeal() {
        Long userId = 1023L;
        DraftStepResult started = draftService.startDraft(userId);

        boolean canceled = draftService.cancelDraft(userId);

        assertThat(canceled).isTrue();
        assertThat(conversationRepository.findByTelegramUserId(userId)).isEmpty();
        assertThat(dealRepository.findById(started.dealId())).isEmpty();
    }

    @Test
    void cancelDraft_noActiveConversation_returnsFalse() {
        boolean canceled = draftService.cancelDraft(9_999_999L);

        assertThat(canceled).isFalse();
    }

    @Test
    void getDraftState_freshServiceInstance_resumesPersistedConversation() {
        Long userId = 1024L;
        draftService.startDraft(userId);
        draftService.submitSourceUrl(userId, VALID_SOURCE_URL);

        ManualDealDraftService freshInstance = new ManualDealDraftService(
                dealRepository, productRepository, conversationRepository,
                asinExtractor, validator, priceCalculator, scoringService, priceTrackingService);

        Optional<DraftState> state = freshInstance.getDraftState(userId);

        assertThat(state).isPresent();
        assertThat(state.get().currentStep()).isEqualTo(ManualDealInputStep.AFFILIATE_URL);
        assertThat(state.get().asin()).isEqualTo(VALID_ASIN);
    }

    private void advanceToAffiliateUrl(Long userId) {
        draftService.startDraft(userId);
        draftService.submitSourceUrl(userId, VALID_SOURCE_URL);
    }

    private void advanceToTitle(Long userId) {
        advanceToAffiliateUrl(userId);
        draftService.submitAffiliateUrl(userId, VALID_AFFILIATE_URL);
    }

    private void advanceToCurrentPrice(Long userId) {
        advanceToTitle(userId);
        draftService.submitTitle(userId, TITLE);
        draftService.submitShortDescription(userId, DESCRIPTION);
    }

    private void advanceToImage(Long userId) {
        advanceToCurrentPrice(userId);
        draftService.submitCurrentPrice(userId, CURRENT_PRICE);
        draftService.submitOriginalPrice(userId, ORIGINAL_PRICE);
    }

    private void advanceToExpiresAt(Long userId) {
        advanceToImage(userId);
        draftService.submitImageUrl(userId, IMAGE_URL);
    }
}
