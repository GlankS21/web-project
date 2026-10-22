package com.shop.product.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void transliteratesRussianNames() {
        assertThat(SlugUtils.slugify("Ноутбуки и ультрабуки", 120)).isEqualTo("noutbuki-i-ultrabuki");
        assertThat(SlugUtils.slugify("Смартфоны", 120)).isEqualTo("smartfony");
        assertThat(SlugUtils.slugify("Зарядные устройства и кабели", 120)).isEqualTo("zariadnye-ustroistva-i-kabeli");
    }

    @Test
    void usesThePassportStyleTableForEveryLetter() {
        assertThat(SlugUtils.slugify("абвгдеёжзийклмнопрстуфхцчшщъыьэюя", 120))
                .isEqualTo("abvgdeezhziiklmnoprstufkhtschshshchieyeiuia");
        assertThat(SlugUtils.slugify("ЁЛКА Щука ЖЁЛТЫЙ", 120)).isEqualTo("elka-shchuka-zheltyi");
    }

    @Test
    void handlesMixedCyrillicAndLatin() {
        assertThat(SlugUtils.slugify("Смартфон Apple iPhone 15 128 ГБ", 220))
                .isEqualTo("smartfon-apple-iphone-15-128-gb");
        assertThat(SlugUtils.slugify("Кабель UGREEN USB-C — USB-C 100 Вт, 2 м", 220))
                .isEqualTo("kabel-ugreen-usb-c-usb-c-100-vt-2-m");
    }

    @Test
    void stripsLatinDiacritics() {
        // French, German and Polish words
        assertThat(SlugUtils.slugify("Cr\u00E8me br\u00FBl\u00E9e", 120)).isEqualTo("creme-brulee");
        assertThat(SlugUtils.slugify("Stra\u00DFe", 120)).isEqualTo("strasse");
        assertThat(SlugUtils.slugify("\u0141\u00F3d\u017A", 120)).isEqualTo("lodz");
    }

    @Test
    void collapsesSeparatorsAndTrimsDashes() {
        assertThat(SlugUtils.slugify("  --iPhone 15 (128GB)!! ", 120)).isEqualTo("iphone-15-128gb");
    }

    @Test
    void truncatesWithoutTrailingDash() {
        assertThat(SlugUtils.slugify("abc def ghi", 8)).isEqualTo("abc-def");
        assertThat(SlugUtils.slugify("Щит щит", 5)).isEqualTo("shchi");
    }

    @Test
    void returnsEmptyWhenNothingIsUsable() {
        assertThat(SlugUtils.slugify("!!!", 120)).isEmpty();
        assertThat(SlugUtils.slugify("ъь", 120)).isEqualTo("ie");
        assertThat(SlugUtils.slugify("ь", 120)).isEmpty();
        assertThat(SlugUtils.slugify(null, 120)).isEmpty();
    }
}
