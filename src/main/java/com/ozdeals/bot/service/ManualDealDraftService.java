package com.ozdeals.bot.service;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.amazon.AmazonAsinExtractor;
import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.DealStatus;
import com.ozdeals.bot.entity.ManualDealInputStep;
import com.ozdeals.bot.entity.Product;
import com.ozdeals.bot.entity.TelegramAdminConversation;
import com.ozdeals.bot.repository.DealRepository;
import com.ozdeals.bot.repository.ProductRepository;
import com.ozdeals.bot.repository.TelegramAdminConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

@Service
public class ManualDealDraftService {

    private final DealRepository dealRepository;
    private final ProductRepository productRepository;
    private final TelegramAdminConversationRepository conversationRepository;
    private final AmazonAsinExtractor asinExtractor;
    private final ManualAmazonDealValidator validator;
    private final DealPriceCalculator priceCalculator;
    private final ScoringService scoringService;
    private final PriceTrackingService priceTrackingService;

    public ManualDealDraftService(DealRepository dealRepository,
                                   ProductRepository productRepository,
                                   TelegramAdminConversationRepository conversationRepository,
                                   AmazonAsinExtractor asinExtractor,
                                   ManualAmazonDealValidator validator,
                                   DealPriceCalculator priceCalculator,
                                   ScoringService scoringService,
                                   PriceTrackingService priceTrackingService) {
        this.dealRepository = dealRepository;
        this.productRepository = productRepository;
        this.conversationRepository = conversationRepository;
        this.asinExtractor = asinExtractor;
        this.validator = validator;
        this.priceCalculator = priceCalculator;
        this.scoringService = scoringService;
        this.priceTrackingService = priceTrackingService;
    }

    @Transactional
    public DraftStepResult startDraft(Long telegramUserId) {
        conversationRepository.findByTelegramUserId(telegramUserId)
                .ifPresent(this::deleteConversationAndDraftDeal);

        Deal deal = dealRepository.save(Deal.builder().status(DealStatus.DRAFT).build());

        TelegramAdminConversation conversation = conversationRepository.save(TelegramAdminConversation.builder()
                .telegramUserId(telegramUserId)
                .deal(deal)
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build());

        return new DraftStepResult(deal.getId(), conversation.getCurrentStep(), true, List.of());
    }

    @Transactional
    public DraftStepResult submitSourceUrl(Long telegramUserId, String sourceUrl) {
        return withConversation(telegramUserId, ManualDealInputStep.SOURCE_URL, (conversation, deal) -> {
            if (isBlank(sourceUrl)) {
                return rejected(deal, conversation.getCurrentStep(), "sourceUrl is required");
            }

            deal.setSourceUrl(sourceUrl);

            Optional<String> asin = asinExtractor.extractAsin(sourceUrl);
            if (asin.isPresent()) {
                deal.setProduct(findOrCreateAmazonProduct(asin.get()));
                dealRepository.save(deal);
                return advance(conversation, ManualDealInputStep.AFFILIATE_URL);
            }

            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.MANUAL_ASIN);
        });
    }

    @Transactional
    public DraftStepResult submitManualAsin(Long telegramUserId, String asin) {
        return withConversation(telegramUserId, ManualDealInputStep.MANUAL_ASIN, (conversation, deal) -> {
            if (!asinExtractor.isValidAsin(asin)) {
                return rejected(deal, conversation.getCurrentStep(), "asin must be exactly 10 alphanumeric characters");
            }

            deal.setProduct(findOrCreateAmazonProduct(asin));
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.AFFILIATE_URL);
        });
    }

    @Transactional
    public DraftStepResult submitAffiliateUrl(Long telegramUserId, String affiliateUrl) {
        return withConversation(telegramUserId, ManualDealInputStep.AFFILIATE_URL, (conversation, deal) -> {
            if (isBlank(affiliateUrl)) {
                return rejected(deal, conversation.getCurrentStep(), "affiliateUrl is required");
            }

            deal.setAffiliateUrl(affiliateUrl);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.TITLE);
        });
    }

    @Transactional
    public DraftStepResult submitTitle(Long telegramUserId, String title) {
        return withConversation(telegramUserId, ManualDealInputStep.TITLE, (conversation, deal) -> {
            if (isBlank(title)) {
                return rejected(deal, conversation.getCurrentStep(), "title is required");
            }

            Product product = deal.getProduct();
            if (product == null) {
                return rejected(deal, conversation.getCurrentStep(), "Deal has no associated Product yet");
            }

            product.setTitle(title);
            productRepository.save(product);
            return advance(conversation, ManualDealInputStep.SHORT_DESCRIPTION);
        });
    }

    @Transactional
    public DraftStepResult submitShortDescription(Long telegramUserId, String shortDescription) {
        return withConversation(telegramUserId, ManualDealInputStep.SHORT_DESCRIPTION, (conversation, deal) -> {
            if (isBlank(shortDescription)) {
                return rejected(deal, conversation.getCurrentStep(), "shortDescription is required");
            }

            deal.setShortDescription(shortDescription);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.CURRENT_PRICE);
        });
    }

    @Transactional
    public DraftStepResult submitCurrentPrice(Long telegramUserId, BigDecimal currentPrice) {
        return withConversation(telegramUserId, ManualDealInputStep.CURRENT_PRICE, (conversation, deal) -> {
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return rejected(deal, conversation.getCurrentStep(), "currentPrice must be greater than zero");
            }

            deal.setCurrentPrice(currentPrice);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.ORIGINAL_PRICE);
        });
    }

    @Transactional
    public DraftStepResult submitOriginalPrice(Long telegramUserId, BigDecimal originalPrice) {
        return withConversation(telegramUserId, ManualDealInputStep.ORIGINAL_PRICE, (conversation, deal) -> {
            if (originalPrice == null || deal.getCurrentPrice() == null
                    || originalPrice.compareTo(deal.getCurrentPrice()) <= 0) {
                return rejected(deal, conversation.getCurrentStep(), "originalPrice must be greater than currentPrice");
            }

            deal.setOriginalPrice(originalPrice);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.IMAGE);
        });
    }

    @Transactional
    public DraftStepResult submitImageUrl(Long telegramUserId, String imageUrl) {
        return withConversation(telegramUserId, ManualDealInputStep.IMAGE, (conversation, deal) -> {
            if (isBlank(imageUrl)) {
                return rejected(deal, conversation.getCurrentStep(), "imageUrl is required");
            }

            deal.setImageUrl(imageUrl);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.EXPIRES_AT);
        });
    }

    @Transactional
    public DraftStepResult submitTelegramImageFileId(Long telegramUserId, String telegramImageFileId) {
        return withConversation(telegramUserId, ManualDealInputStep.IMAGE, (conversation, deal) -> {
            if (isBlank(telegramImageFileId)) {
                return rejected(deal, conversation.getCurrentStep(), "telegramImageFileId is required");
            }

            deal.setTelegramImageFileId(telegramImageFileId);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.EXPIRES_AT);
        });
    }

    @Transactional
    public DraftStepResult submitExpiresAt(Long telegramUserId, LocalDateTime expiresAt) {
        return withConversation(telegramUserId, ManualDealInputStep.EXPIRES_AT, (conversation, deal) -> {
            if (expiresAt == null || !expiresAt.isAfter(LocalDateTime.now())) {
                return rejected(deal, conversation.getCurrentStep(), "expiresAt must be in the future");
            }

            deal.setExpiresAt(expiresAt);
            dealRepository.save(deal);
            return advance(conversation, ManualDealInputStep.PREVIEW);
        });
    }

    @Transactional
    public DraftStepResult skipExpiration(Long telegramUserId) {
        return withConversation(telegramUserId, ManualDealInputStep.EXPIRES_AT, (conversation, deal) ->
                advance(conversation, ManualDealInputStep.PREVIEW));
    }

    @Transactional(readOnly = true)
    public Optional<DraftState> getDraftState(Long telegramUserId) {
        return conversationRepository.findByTelegramUserId(telegramUserId).map(this::toDraftState);
    }

    @Transactional
    public boolean cancelDraft(Long telegramUserId) {
        Optional<TelegramAdminConversation> conversation = conversationRepository.findByTelegramUserId(telegramUserId);
        if (conversation.isEmpty()) {
            return false;
        }

        deleteConversationAndDraftDeal(conversation.get());
        return true;
    }

    private void deleteConversationAndDraftDeal(TelegramAdminConversation conversation) {
        Deal deal = conversation.getDeal();
        conversationRepository.delete(conversation);
        conversationRepository.flush();
        if (deal != null && deal.getStatus() == DealStatus.DRAFT) {
            dealRepository.delete(deal);
        }
    }

    private DraftState toDraftState(TelegramAdminConversation conversation) {
        Deal deal = conversation.getDeal();
        Product product = deal.getProduct();

        List<String> errors = List.of();
        BigDecimal savingAmount = null;
        BigDecimal discountPercentage = null;
        Integer score = null;

        if (conversation.getCurrentStep() == ManualDealInputStep.PREVIEW) {
            errors = validator.validate(deal).errors();

            if (hasValidPrices(deal)) {
                DealPriceResult priceResult = priceCalculator.calculate(deal.getCurrentPrice(), deal.getOriginalPrice());
                savingAmount = priceResult.savingAmount();
                discountPercentage = priceResult.discountPercentage();
            }

            if (product != null && deal.getCurrentPrice() != null) {
                DiscoveredProduct discovered = DiscoveredProduct.builder()
                        .source(product.getSource())
                        .externalId(product.getExternalId())
                        .currentPrice(deal.getCurrentPrice())
                        .listPrice(deal.getOriginalPrice())
                        .build();
                Optional<BigDecimal> historicalLow = priceTrackingService.getHistoricalLow(product.getId());
                score = scoringService.score(discovered, historicalLow);
            }
        }

        return new DraftState(
                deal.getId(),
                conversation.getCurrentStep(),
                deal.getSourceUrl(),
                product != null ? product.getExternalId() : null,
                deal.getAffiliateUrl(),
                product != null ? product.getTitle() : null,
                deal.getShortDescription(),
                deal.getCurrentPrice(),
                deal.getOriginalPrice(),
                deal.getImageUrl(),
                deal.getTelegramImageFileId(),
                deal.getExpiresAt(),
                savingAmount,
                discountPercentage,
                score,
                errors
        );
    }

    private boolean hasValidPrices(Deal deal) {
        return deal.getCurrentPrice() != null
                && deal.getOriginalPrice() != null
                && deal.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0
                && deal.getOriginalPrice().compareTo(deal.getCurrentPrice()) > 0;
    }

    private Product findOrCreateAmazonProduct(String asin) {
        return productRepository.findBySourceAndExternalId(ProductSource.AMAZON, asin)
                .orElseGet(() -> productRepository.save(Product.builder()
                        .source(ProductSource.AMAZON)
                        .externalId(asin)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    private DraftStepResult withConversation(Long telegramUserId, ManualDealInputStep expectedStep,
                                              BiFunction<TelegramAdminConversation, Deal, DraftStepResult> action) {
        TelegramAdminConversation conversation = conversationRepository.findByTelegramUserId(telegramUserId).orElse(null);
        if (conversation == null) {
            return new DraftStepResult(null, null, false, List.of("No active draft for telegramUserId " + telegramUserId));
        }

        if (conversation.getCurrentStep() != expectedStep) {
            return new DraftStepResult(conversation.getDeal().getId(), conversation.getCurrentStep(), false,
                    List.of("Expected step " + expectedStep + " but conversation is at " + conversation.getCurrentStep()));
        }

        return action.apply(conversation, conversation.getDeal());
    }

    private DraftStepResult rejected(Deal deal, ManualDealInputStep currentStep, String error) {
        return new DraftStepResult(deal.getId(), currentStep, false, List.of(error));
    }

    private DraftStepResult advance(TelegramAdminConversation conversation, ManualDealInputStep nextStep) {
        conversation.setCurrentStep(nextStep);
        conversationRepository.save(conversation);
        return new DraftStepResult(conversation.getDeal().getId(), nextStep, true, List.of());
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
