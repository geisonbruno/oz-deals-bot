package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import org.springframework.stereotype.Component;

@Component
public class TelegramMessageFormatter {

    public String format(DiscoveredProduct product, int discountPercent) {
        StringBuilder msg = new StringBuilder();
        msg.append("🔥 ").append(discountPercent).append("% OFF\n\n");
        msg.append(product.getTitle()).append("\n\n");
        msg.append("💰 Now: $").append(product.getCurrentPrice()).append("\n");
        if (product.getListPrice() != null) {
            msg.append("💸 Was: $").append(product.getListPrice()).append("\n");
        }
        msg.append("\n👉 ").append(product.getAffiliateLink());
        return msg.toString();
    }
}
