package com.ozdeals.bot.service;

import com.ozdeals.bot.entity.ManualDealInputStep;

import java.util.List;

public record DraftStepResult(Long dealId, ManualDealInputStep currentStep, boolean success, List<String> errors) {
}
