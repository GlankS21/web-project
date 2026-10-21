package com.shop.order.repository;

import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

/**
 * Optional filters of the order administration list. A {@code null} filter matches everything.
 */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> belongsTo(UUID userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("userId"), userId);
    }

    /**
     * Part of the order number or of the customer e-mail, case-insensitive.
     */
    public static Specification<Order> matchesKeyword(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return null;
            }
            String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), pattern, '\\'),
                    cb.like(cb.lower(root.get("userEmail")), pattern, '\\'));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
