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
import com.shop.cart.util.RussianPlural;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final UUID USER = UUID.randomUUID();

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductCatalog productCatalog;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(cartRepository, productCatalog, new CartProperties(2, 5),
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    void getCart_repricesEachLineAndDerivesItsStatus() {
        Cart cart = cart(line(1L, 2, "100000"), line(2L, 3, "50000"), line(3L, 1, "70000"), line(4L, 1, "20000"));
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cart));
        // Product 1 got cheaper; product 4 is no longer sold
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of(
                1L, product(1L, "90000"), 2L, product(2L, "50000"), 3L, product(3L, "70000")));
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of(1L, 10, 2L, 2, 3L, 0)));

        CartResponse response = service.getCart(USER);

        assertThat(response.items()).extracting(CartItemResponse::status).containsExactly(
                CartItemStatus.AVAILABLE, CartItemStatus.INSUFFICIENT_STOCK, CartItemStatus.OUT_OF_STOCK,
                CartItemStatus.UNAVAILABLE);
        CartItemResponse first = response.items().getFirst();
        assertThat(first.unitPrice()).isEqualByComparingTo("90000");
        assertThat(first.lineTotal()).isEqualByComparingTo("180000");
        assertThat(first.priceChanged()).isTrue();
        assertThat(response.items().get(1).priceChanged()).isFalse();
        // The unavailable line is left out of the subtotal but still counted
        assertThat(response.subtotal()).isEqualByComparingTo("400000");
        assertThat(response.totalQuantity()).isEqualTo(7);
        assertThat(response.currency()).isEqualTo("RUB");
        assertThat(response.checkoutReady()).isFalse();
        assertThat(response.pricesUpToDate()).isTrue();
    }

    @Test
    void getCart_isReadyForCheckoutWhenStockIsUnknown() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cart(line(1L, 2, "100000"))));
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of(1L, product(1L, "100000")));
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.empty());

        CartResponse response = service.getCart(USER);

        assertThat(response.items().getFirst().status()).isEqualTo(CartItemStatus.UNKNOWN);
        assertThat(response.items().getFirst().availableQuantity()).isNull();
        // The order saga makes the final stock check
        assertThat(response.checkoutReady()).isTrue();
    }

    @Test
    void getCart_fallsBackToSavedPricesWhenTheCatalogueIsDown() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cart(line(1L, 2, "100000"))));
        when(productCatalog.findSellableProducts(anyCollection()))
                .thenThrow(new ServiceUnavailableException("product-service is unavailable"));
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of(1L, 5)));

        CartResponse response = service.getCart(USER);

        assertThat(response.items().getFirst().unitPrice()).isEqualByComparingTo("100000");
        assertThat(response.items().getFirst().status()).isEqualTo(CartItemStatus.UNKNOWN);
        assertThat(response.subtotal()).isEqualByComparingTo("200000");
        assertThat(response.pricesUpToDate()).isFalse();
    }

    @Test
    void getCart_returnsAnEmptyCartWithoutCallingOtherServices() {
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.empty());

        assertThat(service.getCart(USER)).isEqualTo(CartResponse.empty());
        verify(productCatalog, never()).findSellableProducts(anyCollection());
    }

    @Test
    void addItem_mergesWithAnExistingLineAndRefreshesTheSnapshot() {
        Cart cart = cart(line(1L, 2, "100000"));
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of(1L, product(1L, "95000")));
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of(1L, 5)));
        when(cartRepository.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart));
        when(cartRepository.findByUserId(USER)).thenReturn(Optional.of(cart));

        service.addItem(USER, new AddCartItemRequest(1L, 3));

        CartItem item = cart.getItems().getFirst();
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getUnitPrice()).isEqualByComparingTo("95000");
        verify(cartRepository).saveAndFlush(cart);
    }

    @Test
    void addItem_rejectsQuantitiesBeyondStockOrLimits() {
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of(1L, product(1L, "100000")));
        when(cartRepository.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart()));

        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of(1L, 2)));
        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(1L, 3)))
                .isInstanceOf(BusinessException.class).hasMessage(CartServiceImpl.INSUFFICIENT_STOCK)
                .extracting(ex -> ((BusinessException) ex).getMessageArgs())
                .isEqualTo(new Object[]{"Товар 1", 2, RussianPlural.FEW});

        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of()));
        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(1L, 1)))
                .isInstanceOf(BusinessException.class).hasMessage(CartServiceImpl.OUT_OF_STOCK)
                .extracting(ex -> ((BusinessException) ex).getMessageArgs())
                .isEqualTo(new Object[]{"Товар 1"});

        // Unknown stock is accepted, but never above the per-line limit
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(1L, 6)))
                .isInstanceOf(BadRequestException.class).hasMessage(CartServiceImpl.QUANTITY_LIMIT)
                .extracting(ex -> ((BadRequestException) ex).getMessageArgs())
                .isEqualTo(new Object[]{5, RussianPlural.MANY});
        verify(cartRepository, never()).saveAndFlush(any());
    }

    @Test
    void addItem_limitsTheNumberOfDifferentProducts() {
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of(3L, product(3L, "100000")));
        when(productCatalog.findAvailability(anyCollection())).thenReturn(Optional.of(Map.of(3L, 5)));
        when(cartRepository.findByUserIdForUpdate(USER))
                .thenReturn(Optional.of(cart(line(1L, 1, "1000"), line(2L, 1, "1000"))));

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(3L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage(CartServiceImpl.TOO_MANY_PRODUCTS)
                .extracting(ex -> ((BusinessException) ex).getMessageArgs())
                .isEqualTo(new Object[]{2, RussianPlural.FEW});
    }

    @Test
    void addItem_rejectsProductsThatAreNotSold() {
        when(productCatalog.findSellableProducts(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest(9L, 1)))
                .isInstanceOf(BusinessException.class).hasMessage(CartServiceImpl.PRODUCT_NOT_AVAILABLE);
    }

    @Test
    void removeItem_failsForAProductThatIsNotInTheCart() {
        when(cartRepository.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart(line(1L, 1, "1000"))));

        assertThatThrownBy(() -> service.removeItem(USER, 2L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CartServiceImpl.NOT_IN_CART);
    }

    @Test
    void removeOrderedProducts_keepsTheOtherLines() {
        Cart cart = cart(line(1L, 1, "1000"), line(2L, 1, "1000"));
        when(cartRepository.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart));

        service.removeOrderedProducts(new OrderCreatedEvent(UUID.randomUUID(), Instant.now(), UUID.randomUUID(),
                "ORD-20261021-ABCDEF", USER, "jane@example.com",
                List.of(new OrderItemPayload(1L, "SKU-1", "Товар 1", 1, new BigDecimal("1000"))),
                new BigDecimal("1000"), "RUB"));

        assertThat(cart.getItems()).extracting(CartItem::getProductId).containsExactly(2L);
    }

    private static Cart cart(CartItem... items) {
        Cart cart = new Cart();
        cart.setUserId(USER);
        for (CartItem item : items) {
            cart.addItem(item);
        }
        return cart;
    }

    private static CartItem line(Long productId, int quantity, String unitPrice) {
        CartItem item = new CartItem();
        item.setProductId(productId);
        item.setSku("SKU-" + productId);
        item.setProductName("Товар " + productId);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setCurrency("RUB");
        item.setQuantity(quantity);
        return item;
    }

    private static ProductSummary product(Long id, String price) {
        return new ProductSummary(id, "SKU-" + id, "Товар " + id, new BigDecimal(price), "RUB", null, "ACTIVE");
    }
}
