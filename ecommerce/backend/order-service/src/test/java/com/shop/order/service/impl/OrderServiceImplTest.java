package com.shop.order.service.impl;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.ApiResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductSummary;
import com.shop.order.config.OrderProperties;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.request.OrderItemRequest;
import com.shop.order.dto.request.ShippingAddressRequest;
import com.shop.order.dto.request.UpdateOrderStatusRequest;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.PaymentMethod;
import com.shop.order.mapper.OrderMapperImpl;
import com.shop.order.messaging.outbox.OutboxService;
import com.shop.order.repository.OrderRepository;
import com.shop.order.service.OrderLifecycle;
import com.shop.order.service.OrderService.OrderPlacement;
import com.shop.order.util.OrderNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductClient productClient;
    @Mock
    private OutboxService outboxService;

    private OrderServiceImpl service;
    private final AuthenticatedUser customer =
            new AuthenticatedUser(UUID.randomUUID(), "jane@example.com", Set.of("ROLE_CUSTOMER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderProperties properties = new OrderProperties(3, 5,
                new OrderProperties.Shipping(new BigDecimal("30000"), new BigDecimal("500000")),
                Duration.ofMinutes(15), null);
        service = new OrderServiceImpl(orderRepository, productClient,
                new OrderLifecycle(orderRepository, outboxService, clock), new OrderNumberGenerator(clock),
                new OrderMapperImpl(), properties, new TransactionTemplate(mock(PlatformTransactionManager.class)),
                clock);
    }

    @Test
    void placeOrder_pricesFromCatalogueAndStartsTheSaga() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(
                product(1L, "120000"), product(2L, "55000"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        // Two lines of product 1 are merged
        OrderPlacement placement = service.placeOrder(customer, request(line(1L, 1), line(2L, 2), line(1L, 1)), null);

        OrderResponse order = placement.order();
        assertThat(placement.created()).isTrue();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.orderNumber()).matches("ORD-20261021-[2-9A-Z]{6}");
        assertThat(order.items()).extracting(item -> item.productId() + "x" + item.quantity())
                .containsExactly("1x2", "2x2");
        assertThat(order.subtotal()).isEqualByComparingTo("350000");
        assertThat(order.shippingFee()).isEqualByComparingTo("30000");
        assertThat(order.totalAmount()).isEqualByComparingTo("380000");
        assertThat(order.currency()).isEqualTo("VND");
        assertThat(order.userEmail()).isEqualTo("jane@example.com");

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CREATED_KEY),
                event.capture(), eq(order.id()));
        assertThat(event.getValue().items()).hasSize(2);
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("380000");
    }

    @Test
    void placeOrder_waivesShippingFromTheThreshold() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(product(1L, "250000"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        OrderResponse order = service.placeOrder(customer, request(line(1L, 2)), null).order();

        assertThat(order.shippingFee()).isEqualByComparingTo("0");
        assertThat(order.totalAmount()).isEqualByComparingTo("500000");
    }

    @Test
    void placeOrder_refusesProductsThatAreNotSold() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(product(1L, "1000"))));

        assertThatThrownBy(() -> service.placeOrder(customer, request(line(1L, 1), line(9L, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("[9]");
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void placeOrder_enforcesLimitsBeforeCallingTheCatalogue() {
        assertThatThrownBy(() -> service.placeOrder(customer, request(line(1L, 3), line(1L, 3)), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At most 5 units");
        assertThatThrownBy(() -> service.placeOrder(customer,
                request(line(1L, 1), line(2L, 1), line(3L, 1), line(4L, 1)), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.placeOrder(customer, request(line(1L, 1)), "bad key!"))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(productClient);
    }

    @Test
    void placeOrder_returnsThePreviousOrderForARepeatedIdempotencyKey() {
        Order previous = order(OrderStatus.AWAITING_PAYMENT, customer.id());
        when(orderRepository.findByUserIdAndIdempotencyKey(customer.id(), "checkout-123"))
                .thenReturn(Optional.of(previous));

        OrderPlacement placement = service.placeOrder(customer, request(line(1L, 1)), "checkout-123");

        assertThat(placement.created()).isFalse();
        assertThat(placement.order().id()).isEqualTo(previous.getId());
        verifyNoInteractions(productClient, outboxService);
    }

    @Test
    void cancelOrder_publishesCancellationForTheOwner() {
        Order order = order(OrderStatus.AWAITING_PAYMENT, customer.id());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse response = service.cancelOrder(order.getId(), customer, "Changed my mind");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Cancelled by customer: Changed my mind");
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CANCELLED_KEY),
                any(OrderCancelledEvent.class), eq(order.getId()));
    }

    @Test
    void cancelOrder_hidesOrdersOfOtherCustomersAndRefusesPaidOrders() {
        Order foreign = order(OrderStatus.PENDING, UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.cancelOrder(foreign.getId(), customer, null))
                .isInstanceOf(ResourceNotFoundException.class);

        Order paid = order(OrderStatus.PAID, customer.id());
        when(orderRepository.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        assertThatThrownBy(() -> service.cancelOrder(paid.getId(), customer, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("can no longer be cancelled");
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateStatus_onlyDrivesTheDeliveryFlow() {
        UUID adminId = UUID.randomUUID();
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
                new UpdateOrderStatusRequest(OrderStatus.CANCELLED, null), adminId))
                .isInstanceOf(BadRequestException.class);

        Order order = order(OrderStatus.PAID, customer.id());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse shipped = service.updateStatus(order.getId(),
                new UpdateOrderStatusRequest(OrderStatus.SHIPPED, "Tracking VN123"), adminId);

        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.shippedAt()).isEqualTo(NOW);
        assertThat(shipped.statusHistory().getLast().changedBy()).isEqualTo(adminId);
    }

    private Order order(OrderStatus status, UUID userId) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20261021-ABCDEF");
        order.setUserId(userId);
        order.setUserEmail("jane@example.com");
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setTotalAmount(new BigDecimal("100000"));
        order.setCurrency("VND");
        order.markPlaced(NOW);
        order.setStatus(status);
        return order;
    }

    private static Order withId(Order order) {
        order.setId(UUID.randomUUID());
        return order;
    }

    private static ProductSummary product(Long id, String price) {
        return new ProductSummary(id, "SKU-" + id, "Product " + id, new BigDecimal(price), "VND",
                "https://img/" + id + ".jpg", "ACTIVE");
    }

    private static OrderItemRequest line(Long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    private static CreateOrderRequest request(OrderItemRequest... lines) {
        return new CreateOrderRequest(List.of(lines), new ShippingAddressRequest("Jane Doe", "+84901234567",
                "12 Nguyen Hue", null, "Ben Nghe", "District 1", "Ho Chi Minh City", null, "VN"),
                PaymentMethod.CARD, null);
    }
}
