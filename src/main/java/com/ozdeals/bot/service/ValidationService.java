package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ValidationService {

    public boolean isValid(DiscoveredProduct product) {
        return product.getSource() != null
                && hasValue(product.getExternalId())
                && hasValue(product.getTitle())
                && product.getCurrentPrice() != null && product.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0
                && hasValue(product.getImageUrl())
                && hasValue(product.getAffiliateLink());
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
