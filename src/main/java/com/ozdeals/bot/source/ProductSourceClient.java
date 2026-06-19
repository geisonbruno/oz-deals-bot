package com.ozdeals.bot.source;

import com.ozdeals.bot.dto.DiscoveredProduct;

import java.util.List;

public interface ProductSourceClient {
    List<DiscoveredProduct> discoverProducts();
}
