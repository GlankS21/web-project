package com.shop.product.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void removesVietnameseDiacriticsIncludingD() {
        assertThat(SlugUtils.slugify("Điện thoại & Máy tính bảng", 120)).isEqualTo("dien-thoai-may-tinh-bang");
        assertThat(SlugUtils.slugify("Sạc nhanh ĐỜI MỚI", 120)).isEqualTo("sac-nhanh-doi-moi");
    }

    @Test
    void collapsesSeparatorsAndTrimsDashes() {
        assertThat(SlugUtils.slugify("  --iPhone 15 (128GB)!! ", 120)).isEqualTo("iphone-15-128gb");
    }

    @Test
    void truncatesWithoutTrailingDash() {
        assertThat(SlugUtils.slugify("abc def ghi", 8)).isEqualTo("abc-def");
    }

    @Test
    void returnsEmptyWhenNothingIsUsable() {
        assertThat(SlugUtils.slugify("!!!", 120)).isEmpty();
        assertThat(SlugUtils.slugify(null, 120)).isEmpty();
    }
}
