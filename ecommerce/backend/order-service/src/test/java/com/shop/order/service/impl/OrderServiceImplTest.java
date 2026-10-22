package com.shop.order.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.ApiResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderDeliveredEvent;
import com.shop.common.exception.BadRequestException;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.order.client.ProductClient;
import com.shop.order.client.ProductSummary;
import com.shop.order.config.OrderProperties;
import com.shop.order.dto.request.CouponPreviewRequest;
import com.shop.order.dto.request.CreateOrderRequest;
import com.shop.order.dto.request.OrderItemRequest;
import com.shop.order.dto.request.ShippingAddressRequest;
import com.shop.order.dto.request.UpdateOrderStatusRequest;
import com.shop.order.dto.response.CouponPreviewResponse;
import com.shop.order.dto.response.OrderResponse;
import com.shop.order.entity.Coupon;
import com.shop.order.entity.CouponDiscountType;
import com.shop.order.entity.Order;
import com.shop.order.entity.OrderItem;
import com.shop.order.entity.OrderStatus;
import com.shop.order.entity.PaymentMethod;
import com.shop.order.mapper.OrderMapperImpl;
import com.shop.order.messaging.outbox.OutboxService;
import com.shop.order.repository.OrderRepository;
import com.shop.order.service.CouponLedger;
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
    @Mock
    private CouponLedger couponLedger;

    private OrderServiceImpl service;
    private final AuthenticatedUser customer =
            new AuthenticatedUser(UUID.randomUUID(), "ivan.petrov@example.ru", Set.of("ROLE_CUSTOMER"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderProperties properties = new OrderProperties(3, 5,
                new OrderProperties.Shipping(new BigDecimal("30000"), new BigDecimal("500000")),
                Duration.ofMinutes(15), null, null, null);
        service = new OrderServiceImpl(orderRepository, productClient,
                new OrderLifecycle(orderRepository, outboxService, couponLedger, clock,
                        new CommonMessagesAutoConfiguration().messageSource()), couponLedger,
                new OrderNumberGenerator(clock),
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
        assertThat(order.currency()).isEqualTo("RUB");
        assertThat(order.userEmail()).isEqualTo("ivan.petrov@example.ru");

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CREATED_KEY),
                event.capture(), eq(order.id()));
        assertThat(event.getValue().items()).hasSize(2);
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("380000");
    }

    @Test
    void placeOrder_appliesAndRedeemsTheCoupon() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(
                product(1L, "120000"), product(2L, "55000"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        Coupon coupon = coupon(CouponDiscountType.PERCENTAGE, "10");
        when(couponLedger.lockApplicable(" welcome10 ", customer.id(), new BigDecimal("350000")))
                .thenReturn(Optional.of(coupon));

        OrderResponse order = service.placeOrder(customer,
                request(" welcome10 ", line(1L, 2), line(2L, 2)), null).order();

        // Shipping is computed on the subtotal before the discount
        assertThat(order.subtotal()).isEqualByComparingTo("350000");
        assertThat(order.couponCode()).isEqualTo("WELCOME10");
        assertThat(order.discountAmount()).isEqualByComparingTo("35000");
        assertThat(order.shippingFee()).isEqualByComparingTo("30000");
        assertThat(order.totalAmount()).isEqualByComparingTo("345000");
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(couponLedger).redeem(eq(coupon), saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(order.id());
        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CREATED_KEY),
                event.capture(), eq(order.id()));
        assertThat(event.getValue().totalAmount()).isEqualByComparingTo("345000");
    }

    @Test
    void placeOrder_failsWithoutSavingWhenTheCouponCannotBeUsed() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(product(1L, "1000"))));
        when(couponLedger.lockApplicable("EXPIRED", customer.id(), new BigDecimal("1000")))
                .thenThrow(new BusinessException("coupon.error.expired"));

        assertThatThrownBy(() -> service.placeOrder(customer, request("EXPIRED", line(1L, 1)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("coupon.error.expired");
        verify(orderRepository, never()).save(any());
        verify(couponLedger, never()).redeem(any(), any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void previewCoupon_pricesTheCartLikeTheCheckout() {
        when(productClient.getProductsByIds(anyList())).thenReturn(ApiResponse.success(List.of(product(1L, "250000"))));
        when(couponLedger.findApplicable("sale", customer.id(), new BigDecimal("500000")))
                .thenReturn(coupon(CouponDiscountType.FIXED_AMOUNT, "20000"));

        CouponPreviewResponse preview = service.previewCoupon(customer,
                new CouponPreviewRequest("sale", List.of(line(1L, 1), line(1L, 1))));

        assertThat(preview.code()).isEqualTo("WELCOME10");
        assertThat(preview.discountType()).isEqualTo(CouponDiscountType.FIXED_AMOUNT);
        assertThat(preview.subtotal()).isEqualByComparingTo("500000");
        assertThat(preview.discountAmount()).isEqualByComparingTo("20000");
        // Free shipping from the threshold, judged before the discount
        assertThat(preview.shippingFee()).isEqualByComparingTo("0");
        assertThat(preview.totalAmount()).isEqualByComparingTo("480000");
        assertThat(preview.currency()).isEqualTo("RUB");
        verifyNoInteractions(orderRepository, outboxService);
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
                .hasMessage("order.error.products-unavailable")
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageArgs()).containsExactly("9"));
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void placeOrder_enforcesLimitsBeforeCallingTheCatalogue() {
        assertThatThrownBy(() -> service.placeOrder(customer, request(line(1L, 3), line(1L, 3)), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("order.error.too-many-units")
                .satisfies(ex -> assertThat(((BadRequestException) ex).getMessageArgs()).containsExactly(5, 1L));
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

        OrderResponse response = service.cancelOrder(order.getId(), customer, " Передумал ");

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancellationReason()).isEqualTo("Отменён покупателем: Передумал");
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE), eq(MessagingConstants.ORDER_CANCELLED_KEY),
                any(OrderCancelledEvent.class), eq(order.getId()));
    }

    @Test
    void cancelOrder_byAnAdministratorSaysSo() {
        AuthenticatedUser admin = new AuthenticatedUser(UUID.randomUUID(), "admin@example.ru", Set.of("ROLE_ADMIN"));
        Order order = order(OrderStatus.PAID, customer.id());
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        assertThat(service.cancelOrder(order.getId(), admin, null).cancellationReason())
                .isEqualTo("Отменён администратором");
    }

    @Test
    void cancelOrder_hidesOrdersOfOtherCustomersAndRefusesShippedOrders() {
        Order foreign = order(OrderStatus.PENDING, UUID.randomUUID());
        when(orderRepository.findByIdForUpdate(foreign.getId())).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.cancelOrder(foreign.getId(), customer, null))
                .isInstanceOf(ResourceNotFoundException.class);

        Order shipped = order(OrderStatus.SHIPPED, customer.id());
        when(orderRepository.findByIdForUpdate(shipped.getId())).thenReturn(Optional.of(shipped));
        assertThatThrownBy(() -> service.cancelOrder(shipped.getId(), customer, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("order.error.not-cancellable")
                .satisfies(ex -> assertThat(new CommonMessagesAutoConfiguration().messageSource().getMessage(
                        ex.getMessage(), ((BusinessException) ex).getMessageArgs(),
                        CommonMessagesAutoConfiguration.DEFAULT_LOCALE))
                        .isEqualTo("Заказ ORD-20261021-ABCDEF в статусе «Отправлен» больше нельзя отменить"));
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
                new UpdateOrderStatusRequest(OrderStatus.SHIPPED, "Трек-номер RA123456789RU"), adminId);

        assertThat(shipped.status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(shipped.shippedAt()).isEqualTo(NOW);
        assertThat(shipped.statusHistory().getLast().changedBy()).isEqualTo(adminId);
        verifyNoInteractions(outboxService);
    }

    @Test
    void updateStatus_publishesTheDeliveryWithTheItems() {
        UUID adminId = UUID.randomUUID();
        Order order = order(OrderStatus.SHIPPED, customer.id());
        OrderItem item = new OrderItem();
        item.setProductId(7L);
        item.setSku("SKU-7");
        item.setProductName("Product 7");
        item.setUnitPrice(new BigDecimal("50000"));
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("100000"));
        order.addItem(item);
        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResponse delivered = service.updateStatus(order.getId(),
                new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Получен покупателем лично"), adminId);

        assertThat(delivered.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(delivered.deliveredAt()).isEqualTo(NOW);
        ArgumentCaptor<OrderDeliveredEvent> event = ArgumentCaptor.forClass(OrderDeliveredEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.ORDER_EXCHANGE),
                eq(MessagingConstants.ORDER_DELIVERED_KEY), event.capture(), eq(order.getId()));
        assertThat(event.getValue().orderId()).isEqualTo(order.getId());
        assertThat(event.getValue().userId()).isEqualTo(customer.id());
        assertThat(event.getValue().items()).singleElement().satisfies(payload -> {
            assertThat(payload.productId()).isEqualTo(7L);
            assertThat(payload.quantity()).isEqualTo(2);
        });
    }

    private Order order(OrderStatus status, UUID userId) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setOrderNumber("ORD-20261021-ABCDEF");
        order.setUserId(userId);
        order.setUserEmail("ivan.petrov@example.ru");
        order.setPaymentMethod(PaymentMethod.ONLINE);
        order.setTotalAmount(new BigDecimal("100000"));
        order.setCurrency("RUB");
        order.markPlaced(NOW);
        order.setStatus(status);
        return order;
    }

    private static Order withId(Order order) {
        order.setId(UUID.randomUUID());
        return order;
    }

    private static ProductSummary product(Long id, String price) {
        return new ProductSummary(id, "SKU-" + id, "Product " + id, new BigDecimal(price), "RUB",
                "https://img/" + id + ".jpg", "ACTIVE");
    }

    private static OrderItemRequest line(Long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }

    private static CreateOrderRequest request(OrderItemRequest... lines) {
        return request(null, lines);
    }

    private static CreateOrderRequest request(String couponCode, OrderItemRequest... lines) {
        return new CreateOrderRequest(List.of(lines), new ShippingAddressRequest("Иван Петров", "+79161234567",
                "ул. Тверская, д. 7", "кв. 12", "Тверской", "ЦАО", "Москва", "125009", "RU"),
                PaymentMethod.ONLINE, null, couponCode);
    }

    private static Coupon coupon(CouponDiscountType type, String value) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setCode("WELCOME10");
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(value));
        coupon.setActive(true);
        return coupon;
    }
}
