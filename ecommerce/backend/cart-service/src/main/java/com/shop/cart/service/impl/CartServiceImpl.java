package com.shop.cart.service.impl;

import com.shop.cart.client.ProductCatalog;
import com.shop.cart.client.ProductSummary;
import com.shop.cart.config.CartProperties;
import com.shop.cart.dto.request.AddCartItemRequest;
import com.shop.cart.dto.response.CartItemResponse;
import com.shop.cart.dto.response.CartItemStatus;
import com.shop.cart.dto.response.CartResponse;
import com.shop.cart.entity.Cart;
import com.shop.cart.entity.CartItem;
import com.shop.cart.repository.CartRepository;
import com.shop.cart.service.CartService;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calls to product-service and inventory-service are made outside database transactions, so no
 * connection is held while waiting for them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductCatalog productCatalog;
    private final CartProperties properties;
    private final TransactionTemplate transactionTemplate;

    @Override
    public CartResponse getCart(UUID userId) {
        return cartRepository.findByUserId(userId)
                .filter(cart -> !cart.getItems().isEmpty())
                .map(cart -> price(cart.getItems()))
                .orElseGet(CartResponse::empty);
    }

    @Override
    public CartResponse addItem(UUID userId, AddCartItemRequest request) {
        ProductSummary product = requireSellable(request.productId());
        Optional<Integer> available = availabilityOf(product.id());
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = cartRepository.findByUserIdForUpdate(userId).orElseGet(() -> newCart(userId));
            CartItem item = cart.findItem(product.id()).orElse(null);
            if (item == null) {
                if (cart.getItems().size() >= properties.maxItems()) {
                    throw new BusinessException("A cart can contain at most %d different products"
                            .formatted(properties.maxItems()));
                }
                item = new CartItem();
                item.setProductId(product.id());
                cart.addItem(item);
            }
            int quantity = item.getQuantity() + request.quantity();
            checkQuantity(product, quantity, available);
            item.setQuantity(quantity);
            copySnapshot(product, item);
            cartRepository.saveAndFlush(cart);
            log.info("Cart of {}: product {} now x{}", userId, product.id(), quantity);
        });
        return getCart(userId);
    }

    @Override
    public CartResponse updateItem(UUID userId, Long productId, int quantity) {
        ProductSummary product = requireSellable(productId);
        Optional<Integer> available = availabilityOf(productId);
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = cartRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> notInCart(productId));
            CartItem item = cart.findItem(productId).orElseThrow(() -> notInCart(productId));
            checkQuantity(product, quantity, available);
            item.setQuantity(quantity);
            copySnapshot(product, item);
            cartRepository.saveAndFlush(cart);
        });
        return getCart(userId);
    }

    @Override
    public CartResponse removeItem(UUID userId, Long productId) {
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = cartRepository.findByUserIdForUpdate(userId)
                    .orElseThrow(() -> notInCart(productId));
            if (!cart.removeItem(productId)) {
                throw notInCart(productId);
            }
            cartRepository.saveAndFlush(cart);
        });
        return getCart(userId);
    }

    @Override
    @Transactional
    public void clearCart(UUID userId) {
        cartRepository.findByUserIdForUpdate(userId).ifPresent(cart -> cart.getItems().clear());
    }

    @Override
    @Transactional
    public void removeOrderedProducts(OrderCreatedEvent event) {
        Set<Long> ordered = event.items().stream()
                .map(OrderItemPayload::productId)
                .collect(Collectors.toSet());
        cartRepository.findByUserIdForUpdate(event.userId()).ifPresentOrElse(
                cart -> {
                    boolean changed = cart.getItems().removeIf(item -> ordered.contains(item.getProductId()));
                    log.info("Order {}: {} ordered product(s) {} the cart of {}", event.orderNumber(),
                            ordered.size(), changed ? "removed from" : "already absent from", event.userId());
                },
                () -> log.debug("Order {}: customer {} has no cart", event.orderNumber(), event.userId()));
    }

    /**
     * Prices the lines with the current catalogue and stock; falls back to the saved data when a
     * service cannot be reached.
     */
    private CartResponse price(List<CartItem> items) {
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, ProductSummary> products;
        try {
            products = productCatalog.findSellableProducts(productIds);
        } catch (ServiceUnavailableException ex) {
            log.warn("Cart priced with saved prices: {}", ex.getMessage());
            products = null;
        }
        Optional<Map<Long, Integer>> availability = productCatalog.findAvailability(productIds);

        List<CartItemResponse> lines = new ArrayList<>(items.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalQuantity = 0;
        boolean checkoutReady = true;
        for (CartItem item : items) {
            ProductSummary product = products == null ? null : products.get(item.getProductId());
            Integer available = availability.map(stock -> stock.getOrDefault(item.getProductId(), 0)).orElse(null);
            CartItemStatus status = status(item, products, product, available);
            BigDecimal unitPrice = product != null ? product.price() : item.getUnitPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            lines.add(new CartItemResponse(
                    item.getProductId(),
                    item.getSku(),
                    product != null ? product.name() : item.getProductName(),
                    product != null ? product.thumbnailUrl() : item.getThumbnailUrl(),
                    unitPrice,
                    product != null ? product.currency() : item.getCurrency(),
                    item.getQuantity(),
                    lineTotal,
                    unitPrice.compareTo(item.getUnitPrice()) != 0,
                    available,
                    status,
                    item.getCreatedAt()));
            totalQuantity += item.getQuantity();
            if (status != CartItemStatus.UNAVAILABLE) {
                subtotal = subtotal.add(lineTotal);
            }
            checkoutReady &= !status.blocksCheckout();
        }
        String currency = lines.isEmpty() ? null : lines.getFirst().currency();
        return new CartResponse(lines, totalQuantity, subtotal, currency, checkoutReady, products != null);
    }

    private static CartItemStatus status(CartItem item, Map<Long, ProductSummary> products, ProductSummary product,
                                         Integer available) {
        if (products == null) {
            return CartItemStatus.UNKNOWN;
        }
        if (product == null) {
            return CartItemStatus.UNAVAILABLE;
        }
        if (available == null) {
            return CartItemStatus.UNKNOWN;
        }
        if (available <= 0) {
            return CartItemStatus.OUT_OF_STOCK;
        }
        return available < item.getQuantity() ? CartItemStatus.INSUFFICIENT_STOCK : CartItemStatus.AVAILABLE;
    }

    private ProductSummary requireSellable(Long productId) {
        ProductSummary product = productCatalog.findSellableProducts(List.of(productId)).get(productId);
        if (product == null) {
            throw new BusinessException("Product %d is not available for sale".formatted(productId));
        }
        return product;
    }

    private Optional<Integer> availabilityOf(Long productId) {
        return productCatalog.findAvailability(List.of(productId))
                .map(stock -> stock.getOrDefault(productId, 0));
    }

    /**
     * When the stock is unknown the quantity is accepted: the order saga makes the final check.
     */
    private void checkQuantity(ProductSummary product, int quantity, Optional<Integer> available) {
        if (quantity > properties.maxQuantityPerItem()) {
            throw new BadRequestException("At most %d units of a product can be put in the cart"
                    .formatted(properties.maxQuantityPerItem()));
        }
        available.ifPresent(units -> {
            if (units <= 0) {
                throw new BusinessException("%s is out of stock".formatted(product.name()));
            }
            if (quantity > units) {
                throw new BusinessException("Only %d unit(s) of %s are available".formatted(units, product.name()));
            }
        });
    }

    private static void copySnapshot(ProductSummary product, CartItem item) {
        item.setSku(product.sku());
        item.setProductName(product.name());
        item.setThumbnailUrl(product.thumbnailUrl());
        item.setUnitPrice(product.price());
        item.setCurrency(product.currency());
    }

    private Cart newCart(UUID userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        return cart;
    }

    private static ResourceNotFoundException notInCart(Long productId) {
        return new ResourceNotFoundException("Product %d is not in the cart".formatted(productId));
    }
}
