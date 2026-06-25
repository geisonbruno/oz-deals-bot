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
    private final TelegramMessageFormatter messageFormatter;

    public TelegramPublisherService(RestTemplate restTemplate, TelegramMessageFormatter messageFormatter) {
        this.restTemplate = restTemplate;
        this.messageFormatter = messageFormatter;
    }

    public boolean publish(DiscoveredProduct product, int discountPercent) {
        String caption = messageFormatter.format(product, discountPercent);

        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            return sendPhoto(product.getImageUrl(), caption);
        } else {
            return sendMessage(caption);
        }
    }

    private boolean sendPhoto(String photoUrl, String caption) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendPhoto";
        try {
            restTemplate.postForEntity(url, Map.of(
                    "chat_id", channelId,
                    "photo", photoUrl,
                    "caption", caption
            ), String.class);
            log.info("Published deal with photo to Telegram");
            return true;
        } catch (Exception e) {
            log.error("Failed to send photo to Telegram: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendMessage(String text) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        try {
            restTemplate.postForEntity(url, Map.of(
                    "chat_id", channelId,
                    "text", text
            ), String.class);
            log.info("Published deal (text only) to Telegram");
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to Telegram: {}", e.getMessage());
            return false;
        }
    }
}
