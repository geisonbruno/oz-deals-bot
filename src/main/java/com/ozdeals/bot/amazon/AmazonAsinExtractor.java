package com.ozdeals.bot.amazon;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AmazonAsinExtractor {

    private static final List<String> ALLOWED_HOSTS = List.of("amazon.com.au", "www.amazon.com.au");

    private static final Pattern ASIN_PATTERN = Pattern.compile("^[A-Za-z0-9]{10}$");

    private static final Pattern DP_PATH = Pattern.compile("/dp/([A-Za-z0-9]{10})(?:/|$)");
    private static final Pattern GP_PRODUCT_PATH = Pattern.compile("/gp/product/([A-Za-z0-9]{10})(?:/|$)");

    public Optional<String> extractAsin(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            return Optional.empty();
        }

        String host = uri.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase())) {
            return Optional.empty();
        }

        String path = uri.getRawPath();
        if (path == null) {
            return Optional.empty();
        }

        Matcher dpMatcher = DP_PATH.matcher(path);
        if (dpMatcher.find()) {
            return Optional.of(dpMatcher.group(1));
        }

        Matcher gpMatcher = GP_PRODUCT_PATH.matcher(path);
        if (gpMatcher.find()) {
            return Optional.of(gpMatcher.group(1));
        }

        return Optional.empty();
    }

    public boolean isValidAsin(String asin) {
        return asin != null && ASIN_PATTERN.matcher(asin).matches();
    }
}
