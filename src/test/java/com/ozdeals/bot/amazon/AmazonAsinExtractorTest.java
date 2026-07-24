package com.ozdeals.bot.amazon;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AmazonAsinExtractorTest {

    private final AmazonAsinExtractor extractor = new AmazonAsinExtractor();

    @Test
    void extractAsin_dpPath_returnsAsin() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com.au/dp/B0ABCDEFGH");

        assertThat(result).contains("B0ABCDEFGH");
    }

    @Test
    void extractAsin_gpProductPath_returnsAsin() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com.au/gp/product/B0ABCDEFGH");

        assertThat(result).contains("B0ABCDEFGH");
    }

    @Test
    void extractAsin_queryStringAfterAsin_returnsAsin() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com.au/dp/B0ABCDEFGH?tag=test-22&th=1");

        assertThat(result).contains("B0ABCDEFGH");
    }

    @Test
    void extractAsin_additionalPathAfterAsin_returnsAsin() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com.au/Some-Product-Title/dp/B0ABCDEFGH/ref=sr_1_1");

        assertThat(result).contains("B0ABCDEFGH");
    }

    @Test
    void extractAsin_invalidHost_returnsEmpty() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com/dp/B0ABCDEFGH");

        assertThat(result).isEmpty();
    }

    @Test
    void extractAsin_invalidAsinLength_returnsEmpty() {
        Optional<String> result = extractor.extractAsin("https://www.amazon.com.au/dp/B0ABC");

        assertThat(result).isEmpty();
    }

    @Test
    void extractAsin_blankInput_returnsEmpty() {
        assertThat(extractor.extractAsin("")).isEmpty();
        assertThat(extractor.extractAsin("   ")).isEmpty();
        assertThat(extractor.extractAsin(null)).isEmpty();
    }

    @Test
    void isValidAsin_tenAlphanumericChars_returnsTrue() {
        assertThat(extractor.isValidAsin("B0ABCDEFGH")).isTrue();
    }

    @Test
    void isValidAsin_wrongLength_returnsFalse() {
        assertThat(extractor.isValidAsin("B0ABC")).isFalse();
        assertThat(extractor.isValidAsin("B0ABCDEFGHIJK")).isFalse();
    }

    @Test
    void isValidAsin_null_returnsFalse() {
        assertThat(extractor.isValidAsin(null)).isFalse();
    }
}
