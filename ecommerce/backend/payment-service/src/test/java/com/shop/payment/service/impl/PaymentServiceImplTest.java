package com.shop.payment.service.impl;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.gateway.PaymentGateway;
import com.shop.payment.gateway.PaymentGateway.ChargeRequest;
import com.shop.payment.gateway.PaymentGateway.ChargeResult;
import com.shop.payment.mapper.PaymentMapperImpl;
import com.shop.payment.messaging.outbox.OutboxService;
import com.shop.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private OutboxService outboxService;

    private PaymentServiceImpl service;
    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, paymentGateway, outboxService, new PaymentMapperImpl(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approvedChargeIsRecordedAndReported() {
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
        when(paymentGateway.charge(any())).thenReturn(ChargeResult.approved("TXN-1"));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);

        service.processPaymentRequest(request("380000", "CARD"));

        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getValue().getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(saved.getValue().getTransactionId()).isEqualTo("TXN-1");
        ArgumentCaptor<PaymentSucceededEvent> event = ArgumentCaptor.forClass(PaymentSucceededEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.PAYMENT_EXCHANGE),
                eq(MessagingConstants.PAYMENT_SUCCEEDED_KEY), event.capture(), eq(orderId));
        assertThat(event.getValue().transactionId()).isEqualTo("TXN-1");
        assertThat(event.getValue().amount()).isEqualByComparingTo("380000");
    }

    @Test
    void declinedChargeIsRecordedAndReported() {
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
        when(paymentGateway.charge(any(ChargeRequest.class))).thenReturn(ChargeResult.declined("Declined by the issuer"));

        service.processPaymentRequest(request("380000", "E_WALLET"));

        ArgumentCaptor<PaymentFailedEvent> event = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(outboxService).enqueue(eq(MessagingConstants.PAYMENT_EXCHANGE), eq(MessagingConstants.PAYMENT_FAILED_KEY),
                event.capture(), eq(orderId));
        assertThat(event.getValue().reason()).isEqualTo("Declined by the issuer");
    }

    @Test
    void invalidRequestsFailWithoutReachingTheGateway() {
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());

        service.processPaymentRequest(request("380000", "BITCOIN"));
        service.processPaymentRequest(request("0", "CARD"));

        verifyNoInteractions(paymentGateway);
        ArgumentCaptor<PaymentFailedEvent> event = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(outboxService, times(2)).enqueue(any(), eq(MessagingConstants.PAYMENT_FAILED_KEY),
                event.capture(), any());
        assertThat(event.getAllValues()).extracting(PaymentFailedEvent::reason)
                .containsExactly("Unsupported payment method BITCOIN", "Invalid payment amount");
    }

    @Test
    void duplicateRequestOrCancelledOrderIsNeverCharged() {
        when(paymentRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(payment(PaymentStatus.SUCCEEDED)))
                .thenReturn(Optional.of(payment(PaymentStatus.CANCELLED)));

        service.processPaymentRequest(request("380000", "CARD"));
        service.processPaymentRequest(request("380000", "CARD"));

        verifyNoInteractions(paymentGateway, outboxService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void cancellationBeforePaymentLeavesAMarker() {
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.empty());
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);

        service.handleOrderCancelled(cancelled());

        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(saved.getValue().getAmount()).isNull();
        assertThat(saved.getValue().getFailureReason()).isEqualTo("Order cancelled before payment: Out of stock");
        verifyNoInteractions(paymentGateway);
    }

    @Test
    void cancellationAfterCaptureRefunds() {
        Payment payment = payment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByOrderIdForUpdate(orderId)).thenReturn(Optional.of(payment));
        when(paymentGateway.refund("TXN-1", payment.getAmount(), "VND")).thenReturn("RFD-1");

        service.handleOrderCancelled(cancelled());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundTransactionId()).isEqualTo("RFD-1");
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);

        // Redelivery: already refunded, the gateway is not called again
        service.handleOrderCancelled(cancelled());
        verify(paymentGateway).refund(any(), any(), any());
    }

    @Test
    void paymentIsVisibleToItsOwnerAndAdministrators() {
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment(PaymentStatus.SUCCEEDED)));

        assertThat(service.getPaymentOfOrder(orderId, new AuthenticatedUser(userId, "a@b.c", Set.of("ROLE_CUSTOMER")))
                .transactionId()).isEqualTo("TXN-1");
        assertThat(service.getPaymentOfOrder(orderId,
                new AuthenticatedUser(UUID.randomUUID(), "admin@b.c", Set.of("ROLE_ADMIN")))).isNotNull();
        assertThatThrownBy(() -> service.getPaymentOfOrder(orderId,
                new AuthenticatedUser(UUID.randomUUID(), "x@b.c", Set.of("ROLE_CUSTOMER"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PaymentRequestedEvent request(String amount, String method) {
        return new PaymentRequestedEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", userId, "jane@example.com",
                new BigDecimal(amount), "VND", method);
    }

    private OrderCancelledEvent cancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", userId, "jane@example.com",
                "Out of stock");
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setOrderId(orderId);
        payment.setOrderNumber("ORD-1");
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("380000"));
        payment.setCurrency("VND");
        payment.setMethod(PaymentMethod.CARD);
        payment.setStatus(status);
        payment.setTransactionId("TXN-1");
        return payment;
    }
}
