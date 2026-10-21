package com.shop.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorrelationIdUtilsTest {

    @AfterEach
    void tearDown() {
        CorrelationIdUtils.clear();
    }

    @Test
    void resolveOrGenerate_keepsSafeIncomingValue() {
        assertThat(CorrelationIdUtils.resolveOrGenerate("abc-123_X.y")).isEqualTo("abc-123_X.y");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "bad value", "line\nbreak", "<script>"})
    void resolveOrGenerate_replacesUnsafeValueWithUuid(String candidate) {
        String result = CorrelationIdUtils.resolveOrGenerate(candidate);

        assertThatCode(() -> UUID.fromString(result)).doesNotThrowAnyException();
    }

    @Test
    void resolveOrGenerate_replacesTooLongValue() {
        String tooLong = "a".repeat(65);

        assertThat(CorrelationIdUtils.resolveOrGenerate(tooLong)).isNotEqualTo(tooLong);
    }

    @Test
    void bindAndClear_manageMdcValue() {
        CorrelationIdUtils.bind("req-1");
        assertThat(CorrelationIdUtils.current()).isEqualTo("req-1");
        assertThat(CorrelationIdUtils.currentOrGenerate()).isEqualTo("req-1");

        CorrelationIdUtils.clear();
        assertThat(CorrelationIdUtils.current()).isNull();
        assertThat(CorrelationIdUtils.currentOrGenerate()).isNotBlank();
    }
}
