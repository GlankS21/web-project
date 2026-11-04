package ru.itmo.vkr.channels;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatternsTest {

    @Test
    void joinsPathFragments() {
        assertThat(Patterns.joinPaths("/api/items", "/{id}")).isEqualTo("/api/items/{id}");
        assertThat(Patterns.joinPaths("/api/items/", "search")).isEqualTo("/api/items/search");
        assertThat(Patterns.joinPaths("", "")).isEqualTo("/");
        assertThat(Patterns.joinPaths(null, "/api//x/")).isEqualTo("/api/x");
    }

    @Test
    void pathVariablesMatchAnySegmentOnEitherSide() {
        assertThat(Patterns.pathTemplatesMatch("/api/items/{itemId}", "/api/items/{id}")).isTrue();
        assertThat(Patterns.pathTemplatesMatch("/api/items/{id}", "/api/items/by-ids")).isTrue();
        assertThat(Patterns.pathTemplatesMatch("/api/items/{id}", "/api/items")).isFalse();
        assertThat(Patterns.pathTemplatesMatch("/api/items/search", "/api/items/find")).isFalse();
    }

    @Test
    void segmentKindsDecideTheClosestTemplate() {
        assertThat(Patterns.segmentKindMismatches("/api/items/featured", "/api/items/featured")).isZero();
        assertThat(Patterns.segmentKindMismatches("/api/items/{id}", "/api/items/{itemId}")).isZero();
        assertThat(Patterns.segmentKindMismatches("/api/items/{id}", "/api/items/featured")).isEqualTo(1);
        assertThat(Patterns.segmentKindMismatches("/api/items/featured", "/api/items/{id}")).isEqualTo(1);
    }

    @Test
    void topicWildcards() {
        assertThat(Patterns.topicMatches("order.created", "order.created")).isTrue();
        assertThat(Patterns.topicMatches("order.*", "order.created")).isTrue();
        assertThat(Patterns.topicMatches("order.*", "order.created.v2")).isFalse();
        assertThat(Patterns.topicMatches("order.#", "order")).isTrue();
        assertThat(Patterns.topicMatches("order.#", "order.created.v2")).isTrue();
        assertThat(Patterns.topicMatches("#", "anything.at.all")).isTrue();
        assertThat(Patterns.topicMatches("*.created", "stock.created")).isTrue();
        assertThat(Patterns.topicMatches("order.created", "order.cancelled")).isFalse();
    }

    @Test
    void gatewayPathPredicates() {
        assertThat(Patterns.antPattern("/api/items/**").matcher("/api/items").matches()).isTrue();
        assertThat(Patterns.antPattern("/api/items/**").matcher("/api/items/{id}/reviews").matches()).isTrue();
        assertThat(Patterns.antPattern("/api/items/**").matcher("/api/itemsx").matches()).isFalse();
        assertThat(Patterns.antPattern("/api/*/stats").matcher("/api/orders/stats").matches()).isTrue();
        assertThat(Patterns.antPattern("/api/{segment}/x").matcher("/api/a.b/x").matches()).isTrue();
    }
}
