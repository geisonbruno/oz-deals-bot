package com.ozdeals.bot.source;

import com.ozdeals.bot.dto.DiscoveredProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "sources.awin.enabled", havingValue = "true")
@Slf4j
public class AwinProductSourceClient implements ProductSourceClient {

    @Override
    public List<DiscoveredProduct> discoverProducts() {
        log.warn("[Awin] AwinProductSourceClient is not implemented yet — returning empty list");
        return List.of();
    }
}
