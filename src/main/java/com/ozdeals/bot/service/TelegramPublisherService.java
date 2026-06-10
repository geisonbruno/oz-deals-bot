package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class TelegramPublisherService {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.channel.id}")
    private String channelId;

    private final RestTemplate restTemplate;

    public TelegramPublisherService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void publish(DiscoveredProduct product, int discountPercent) {
        String caption = buildMessage(product, discountPercent);

        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            sendPhoto(product.getImageUrl(), caption);
        } else {
            sendMessage(caption);
        }
    }

    private String buildMessage(DiscoveredProduct product, int discountPercent) {
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

    private void sendPhoto(String photoUrl, String caption) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";
        try {
            restTemplate.postForEntity(url, Map.of(
                    "chat_id", channelId,
                    "photo", photoUrl,
                    "caption", caption
            ), String.class);
            log.info("Published deal with photo to Telegram");
        } catch (Exception e) {
            log.error("Failed to send photo to Telegram: {}", e.getMessage());
        }
    }

    private void sendMessage(String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        try {
            restTemplate.postForEntity(url, Map.of(
                    "chat_id", channelId,
                    "text", text
            ), String.class);
            log.info("Published deal (text only) to Telegram");
        } catch (Exception e) {
            log.error("Failed to send message to Telegram: {}", e.getMessage());
        }
    }
}
