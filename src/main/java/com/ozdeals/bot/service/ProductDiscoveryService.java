package com.ozdeals.bot.service;

import com.ozdeals.bot.dto.DiscoveredProduct;
import com.ozdeals.bot.source.ProductSourceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ProductDiscoveryService {

    private final List<ProductSourceClient> sources;

    public ProductDiscoveryService(List<ProductSourceClient> sources) {
        this.sources = sources;
    }

    public List<DiscoveredProduct> discoverAll() {
        Map<String, DiscoveredProduct> seen = new LinkedHashMap<>();
        for (ProductSourceClient source : sources) {
            try {
                List<DiscoveredProduct> products = source.discoverProducts();
                for (DiscoveredProduct p : products) {
                    if (p.getAsin() != null) seen.putIfAbsent(p.getAsin(), p);
                }
            } catch (Exception e) {
                log.error("Source {} failed: {}", source.getClass().getSimpleName(), e.getMessage());
            }
        }
        log.info("Total unique products discovered: {}", seen.size());
        return new ArrayList<>(seen.values());
    }
}
