package com.shop.product.repository;

import com.shop.product.entity.Product;
import com.shop.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Locale;

/**
 * Composable catalogue filters. Each factory returns {@code null} when its filter is not set,
 * which {@link Specification#allOf} ignores.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /**
     * Case-insensitive match on name, SKU or brand. LIKE wildcards typed by the user are escaped.
     */
    public static Specification<Product> matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                cb.like(cb.lower(root.get("brand")), pattern, '\\'));
    }

    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        return categoryIds == null ? null : (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> hasBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            return null;
        }
        String normalized = brand.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("brand")), normalized);
    }

    public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return minPrice == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
