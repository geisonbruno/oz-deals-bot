package com.ozdeals.bot.service;

import java.math.BigDecimal;

public record DealPriceResult(BigDecimal savingAmount, BigDecimal discountPercentage) {
}
