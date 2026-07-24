package com.ozdeals.bot.service;

import com.ozdeals.bot.entity.ManualDealInputStep;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DraftState(
        Long dealId,
        ManualDealInputStep currentStep,
        String sourceUrl,
        String asin,
        String affiliateUrl,
        String title,
        String shortDescription,
        BigDecimal currentPrice,
        BigDecimal originalPrice,
        String imageUrl,
        String telegramImageFileId,
        LocalDateTime expiresAt,
        BigDecimal savingAmount,
        BigDecimal discountPercentage,
        Integer score,
        List<String> validationErrors
) {
}
