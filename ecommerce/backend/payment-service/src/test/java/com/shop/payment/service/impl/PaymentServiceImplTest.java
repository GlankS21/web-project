package com.shop.payment.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.constant.MessagingConstants;
import com.shop.common.event.DomainEvent;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRefundedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.config.PaymentProperties;
import com.shop.payment.entity.NotificationStatus;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentNotification;
import com.shop.payment.entity.PaymentProvider;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.entity.PaymentTransaction;
import com.shop.payment.entity.TransactionStatus;
import com.shop.payment.entity.TransactionType;
import com.shop.payment.gateway.GatewayException;
import com.shop.payment.gateway.PaymentGateway;
import com.shop.payment.gateway.PaymentGateway.CreatePaymentRequest;
import com.shop.payment.gateway.PaymentGateway.CreateRefundRequest;
import com.shop.payment.gateway.PaymentGateway.GatewayPayment;
import com.shop.payment.gateway.PaymentGateway.GatewayRefund;
import com.shop.payment.gateway.PaymentGateway.Status;
import com.shop.payment.mapper.PaymentMapperImpl;
import com.shop.payment.messaging.outbox.OutboxService;
import com.shop.payment.repository.PaymentNotificationRepository;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.repository.PaymentTransactionRepository;
import com.shop.payment.service.PaymentFailureMessages;
import com.shop.payment.service.PaymentLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service and ledger together, with repositories backed by in-memory maps and a mocked provider.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
    private static final String AMOUNT = "12990.50";

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTransactionRepository transactionRepository;
    @Mock
    private PaymentNotificationRepository notificationRepository;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private OutboxService outboxService;

    private final Map<UUID, Payment> payments = new LinkedHashMap<>();
    private final Map<UUID, PaymentTransaction> transactions = new LinkedHashMap<>();
    private final Map<UUID, PaymentNotification> notifications = new LinkedHashMap<>();
    private final UUID orderId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final AuthenticatedUser customer = new AuthenticatedUser(userId, "jane@example.com", Set.of("ROLE_CUSTOMER"));
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        storeInMemory();
        when(paymentGateway.provider()).thenReturn(PaymentProvider.YOOKASSA);

        MessageSource messageSource = new CommonMessagesAutoConfiguration().messageSource();
        PaymentFailureMessages failureMessages = new PaymentFailureMessages(messageSource);
        PaymentProperties properties = new PaymentProperties(PaymentProperties.Provider.YOOKASSA, "RUB", 3, null,
                null, null);
        service = new PaymentServiceImpl(paymentRepository, transactionRepository, notificationRepository,
                paymentGateway,
                new PaymentLedger(paymentRepository, transactionRepository, outboxService, clock, failureMessages),
                new PaymentMapperImpl(), properties, messageSource, failureMessages,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), clock);
    }

    @Test
    void cashOnDeliveryIsAcceptedWithoutTheProvider() {
        service.processPaymentRequest(request(orderId, "COD", "RUB"));

        Payment payment = payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.COD);
        assertThat(onlyTransaction().getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        PaymentSucceededEvent event = captured(MessagingConstants.PAYMENT_SUCCEEDED_KEY, PaymentSucceededEvent.class);
        assertThat(event.transactionId()).isEqualTo("COD-ORD-1");
        verify(paymentGateway, never()).createPayment(any());
    }

    @Test
    void onlinePaymentIsCreatedAtTheProviderWithItsIdempotenceKey() {
        ArgumentCaptor<CreatePaymentRequest> sent = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        when(paymentGateway.createPayment(sent.capture())).thenReturn(pending("yk-1"));

        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));

        Payment payment = payment();
        PaymentTransaction charge = onlyTransaction();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getConfirmationToken()).isEqualTo("ct-yk-1");
        assertThat(payment.getAttempts()).isEqualTo(1);
        assertThat(charge.getProviderId()).isEqualTo("yk-1");
        CreatePaymentRequest request = sent.getValue();
        assertThat(request.idempotenceKey()).isEqualTo(charge.getId().toString());
        assertThat(request.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(request.currency()).isEqualTo("RUB");
        assertThat(request.description()).isEqualTo("Заказ № ORD-1");
        assertThat(request.metadata()).containsEntry("transactionId", charge.getId().toString());
        // Nothing is reported to order-service before the customer pays
        verify(outboxService, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void invalidRequestsAreDeclinedWithoutReachingTheProvider() {
        service.processPaymentRequest(request(UUID.randomUUID(), "ONLINE", "USD"));
        service.processPaymentRequest(request(UUID.randomUUID(), "BITCOIN", "RUB"));
        service.processPaymentRequest(new PaymentRequestedEvent(UUID.randomUUID(), NOW, UUID.randomUUID(), "ORD-2",
                userId, "jane@example.com", BigDecimal.ZERO, "RUB", "ONLINE"));

        ArgumentCaptor<PaymentFailedEvent> events = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(outboxService, times(3)).enqueue(eq(MessagingConstants.PAYMENT_EXCHANGE),
                eq(MessagingConstants.PAYMENT_FAILED_KEY), events.capture(), any());
        assertThat(events.getAllValues()).extracting(PaymentFailedEvent::reason)
                .containsExactly("Валюта платежа не поддерживается", "Способ оплаты не поддерживается",
                        "Некорректная сумма платежа");
        verify(paymentGateway, never()).createPayment(any());
    }

    @Test
    void redeliveredRequestIsNotChargedTwice() {
        when(paymentGateway.createPayment(any())).thenReturn(pending("yk-1"));

        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));
        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));

        verify(paymentGateway, times(1)).createPayment(any());
        assertThat(transactions).hasSize(1);
    }

    @Test
    void notificationIsVerifiedWithTheProviderBeforeTheCapture() {
        startOnlinePayment();
        // A notification whose payment is still pending at the provider changes nothing
        when(paymentGateway.getPayment("yk-1")).thenReturn(pending("yk-1"));
        deliver("payment.succeeded", "yk-1");
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PENDING);

        when(paymentGateway.getPayment("yk-1")).thenReturn(succeeded("yk-1"));
        deliver("payment.succeeded", "yk-1");
        deliver("payment.succeeded", "yk-1");

        Payment payment = payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getTransactionId()).isEqualTo("yk-1");
        assertThat(payment.getConfirmationToken()).isNull();
        assertThat(payment.getPaidAt()).isEqualTo(NOW);
        verify(outboxService, times(1)).enqueue(eq(MessagingConstants.PAYMENT_EXCHANGE),
                eq(MessagingConstants.PAYMENT_SUCCEEDED_KEY), any(), eq(orderId));
        // Every notification is kept with its body; the pending one does not block the final one
        assertThat(notifications.values()).extracting(PaymentNotification::getStatus).containsExactly(
                NotificationStatus.IGNORED, NotificationStatus.PROCESSED, NotificationStatus.DUPLICATE);
        assertThat(notifications.values()).allSatisfy(notification -> {
            assertThat(notification.getPayload()).contains("yk-1");
            assertThat(notification.getProvider()).isEqualTo(PaymentProvider.YOOKASSA);
        });
        assertThat(notifications.values()).extracting(PaymentNotification::getTransactionId)
                .containsExactly(onlyTransaction().getId(), onlyTransaction().getId(), null);
        // The duplicate was not even verified with the provider
        verify(paymentGateway, times(2)).getPayment("yk-1");
    }

    @Test
    void notificationThatCannotBeVerifiedIsRecordedAsFailed() {
        when(paymentGateway.getPayment("forged")).thenThrow(GatewayException.rejected("HTTP 404"));

        assertThatThrownBy(() -> deliver("payment.succeeded", "forged")).isInstanceOf(ResourceNotFoundException.class);

        PaymentNotification notification = notifications.values().iterator().next();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getError()).isEqualTo("HTTP 404");
    }

    @Test
    void notificationThatOvertookTheCreationIsMatchedByMetadata() {
        PaymentTransaction charge = transactionOf(startOnlinePaymentWithoutAnswer());
        when(paymentGateway.getPayment("yk-9")).thenReturn(new GatewayPayment("yk-9", Status.SUCCEEDED,
                new BigDecimal(AMOUNT), "RUB", null, null, Map.of("transactionId", charge.getId().toString())));

        deliver("payment.succeeded", "yk-9");

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(charge.getProviderId()).isEqualTo("yk-9");
    }

    @Test
    void stateWithAnotherAmountIsIgnored() {
        startOnlinePayment();
        when(paymentGateway.getPayment("yk-1")).thenReturn(new GatewayPayment("yk-1", Status.SUCCEEDED,
                new BigDecimal("1.00"), "RUB", null, null, null));

        deliver("payment.succeeded", "yk-1");

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void declinedAttemptCanBePaidAgainByItsOwner() {
        startOnlinePayment();
        when(paymentGateway.getPayment("yk-1")).thenReturn(canceled("yk-1", "insufficient_funds"));
        deliver("payment.canceled", "yk-1");

        Payment payment = payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("insufficient_funds");
        // The order keeps waiting: the customer may pay again until it expires
        verify(outboxService, never()).enqueue(any(), eq(MessagingConstants.PAYMENT_FAILED_KEY), any(), any());

        assertThatThrownBy(() -> service.retryPayment(orderId,
                new AuthenticatedUser(UUID.randomUUID(), "x@example.com", Set.of("ROLE_CUSTOMER"))))
                .isInstanceOf(ResourceNotFoundException.class);

        ArgumentCaptor<CreatePaymentRequest> sent = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        when(paymentGateway.createPayment(sent.capture())).thenReturn(pending("yk-2"));
        var response = service.retryPayment(orderId, customer);

        assertThat(response.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.attempts()).isEqualTo(2);
        assertThat(response.confirmationToken()).isEqualTo("ct-yk-2");
        assertThat(response.transactions()).extracting(t -> t.status())
                .containsExactly(TransactionStatus.FAILED, TransactionStatus.PENDING);
        assertThat(sent.getAllValues()).extracting(CreatePaymentRequest::idempotenceKey).doesNotHaveDuplicates();
    }

    @Test
    void attemptsAreLimited() {
        startOnlinePayment();
        for (int attempt = 1; attempt <= 3; attempt++) {
            String id = "yk-" + attempt;
            when(paymentGateway.getPayment(id)).thenReturn(canceled(id, "expired_on_confirmation"));
            deliver("payment.canceled", id);
            if (attempt < 3) {
                when(paymentGateway.createPayment(any())).thenReturn(pending("yk-" + (attempt + 1)));
                service.retryPayment(orderId, customer);
            }
        }

        assertThatThrownBy(() -> service.retryPayment(orderId, customer))
                .isInstanceOf(BusinessException.class)
                .hasMessage("payment.error.too-many-attempts");
    }

    @Test
    void providerFailureEndsTheAttempt() {
        when(paymentGateway.createPayment(any())).thenThrow(GatewayException.unavailable("timeout", null));

        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment().getFailureReason()).isEqualTo("provider_unavailable");
        assertThat(onlyTransaction().getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void cancellationOfAPaidOrderRefundsOnce() {
        capturedOnlinePayment();
        ArgumentCaptor<CreateRefundRequest> sent = ArgumentCaptor.forClass(CreateRefundRequest.class);
        when(paymentGateway.createRefund(sent.capture())).thenReturn(refund("rf-1", Status.SUCCEEDED));

        service.handleOrderCancelled(cancelled());
        service.handleOrderCancelled(cancelled());

        Payment payment = payment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundTransactionId()).isEqualTo("rf-1");
        assertThat(payment.getRefundedAt()).isEqualTo(NOW);
        CreateRefundRequest request = sent.getValue();
        assertThat(request.paymentId()).isEqualTo("yk-1");
        assertThat(request.amount()).isEqualByComparingTo(AMOUNT);
        assertThat(request.description()).isEqualTo("Возврат по заказу № ORD-1");
        PaymentTransaction refund = transactions.values().stream()
                .filter(transaction -> transaction.getType() == TransactionType.REFUND).findFirst().orElseThrow();
        assertThat(request.idempotenceKey()).isEqualTo(refund.getId().toString());
        verify(paymentGateway, times(1)).createRefund(any());
        PaymentRefundedEvent event = captured(MessagingConstants.PAYMENT_REFUNDED_KEY, PaymentRefundedEvent.class);
        assertThat(event.refundId()).isEqualTo("rf-1");
    }

    @Test
    void asynchronousRefundCompletesWithItsNotification() {
        capturedOnlinePayment();
        when(paymentGateway.createRefund(any())).thenReturn(refund("rf-1", Status.PENDING));

        service.handleOrderCancelled(cancelled());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

        when(paymentGateway.getRefund("rf-1")).thenReturn(refund("rf-1", Status.SUCCEEDED));
        deliver("refund.succeeded", "rf-1");

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void rejectedRefundIsReportedAsFailed() {
        capturedOnlinePayment();
        when(paymentGateway.createRefund(any())).thenThrow(GatewayException.rejected("HTTP 400"));

        service.handleOrderCancelled(cancelled());

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        assertThat(payment().getFailureReason()).isEqualTo("provider_rejected");
    }

    @Test
    void captureAfterTheCancellationIsRefunded() {
        startOnlinePayment();
        service.handleOrderCancelled(cancelled());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> service.retryPayment(orderId, customer)).isInstanceOf(BusinessException.class);

        when(paymentGateway.getPayment("yk-1")).thenReturn(succeeded("yk-1"));
        when(paymentGateway.createRefund(any())).thenReturn(refund("rf-1", Status.SUCCEEDED));
        deliver("payment.succeeded", "yk-1");

        verify(paymentGateway).createRefund(any());
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(outboxService, never()).enqueue(any(), eq(MessagingConstants.PAYMENT_SUCCEEDED_KEY), any(), any());
    }

    @Test
    void cancelledCashOnDeliveryHasNothingToRefund() {
        service.processPaymentRequest(request(orderId, "COD", "RUB"));

        service.handleOrderCancelled(cancelled());

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment().getFailureReason()).isEqualTo("cash_not_collected");
        verify(paymentGateway, never()).createRefund(any());
    }

    @Test
    void cancellationBeforePaymentLeavesAMarker() {
        service.handleOrderCancelled(cancelled());

        Payment marker = payment();
        assertThat(marker.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(marker.getAmount()).isNull();

        // The late request is not charged
        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));
        verify(paymentGateway, never()).createPayment(any());
    }

    @Test
    void reconciliationAppliesTheStateOfThePendingCharge() {
        startOnlinePayment();
        when(paymentGateway.getPayment("yk-1")).thenReturn(succeeded("yk-1"));

        assertThat(service.reconcilePendingTransactions()).isEqualTo(1);

        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void reconciliationResendsAChargeThatNeverReachedTheProvider() {
        PaymentTransaction charge = transactionOf(startOnlinePaymentWithoutAnswer());
        ArgumentCaptor<CreatePaymentRequest> sent = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        when(paymentGateway.createPayment(sent.capture())).thenReturn(pending("yk-1"));

        service.reconcilePendingTransactions();

        assertThat(sent.getValue().idempotenceKey()).isEqualTo(charge.getId().toString());
        assertThat(payment().getConfirmationToken()).isNotNull();
    }

    @Test
    void refreshReadsPendingOperationsFromTheProvider() {
        startOnlinePayment();
        when(paymentGateway.getPayment("yk-1")).thenReturn(succeeded("yk-1"));

        assertThat(service.refreshPayment(orderId, customer).status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void paymentIsVisibleToItsOwnerAndAdministrators() {
        startOnlinePayment();
        when(paymentRepository.findByOrderId(orderId)).thenAnswer(invocation -> Optional.of(payment()));

        assertThat(service.getPaymentOfOrder(orderId, customer).transactions()).hasSize(1);
        assertThat(service.getPaymentOfOrder(orderId,
                new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Set.of("ROLE_ADMIN")))).isNotNull();
        assertThatThrownBy(() -> service.getPaymentOfOrder(orderId,
                new AuthenticatedUser(UUID.randomUUID(), "x@example.com", Set.of("ROLE_CUSTOMER"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------------------------------------------------------------- scenarios

    private void startOnlinePayment() {
        when(paymentGateway.createPayment(any())).thenReturn(pending("yk-1"));
        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));
    }

    private void capturedOnlinePayment() {
        startOnlinePayment();
        when(paymentGateway.getPayment("yk-1")).thenReturn(succeeded("yk-1"));
        deliver("payment.succeeded", "yk-1");
        assertThat(payment().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    /**
     * The creation call timed out after the provider created the payment: no provider id is known.
     */
    private Payment startOnlinePaymentWithoutAnswer() {
        when(paymentGateway.createPayment(any())).thenThrow(GatewayException.unavailable("read timeout", null));
        service.processPaymentRequest(request(orderId, "ONLINE", "RUB"));
        Payment payment = payment();
        PaymentTransaction charge = transactionOf(payment);
        // as if the process stopped before recording the failure
        charge.setStatus(TransactionStatus.PENDING);
        charge.setFailureReason(null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setFailureReason(null);
        reset(paymentGateway);
        when(paymentGateway.provider()).thenReturn(PaymentProvider.YOOKASSA);
        return payment;
    }

    // ---------------------------------------------------------------- fixtures

    private void deliver(String event, String objectId) {
        service.handleProviderNotification(event, objectId,
                "{\"type\":\"notification\",\"event\":\"%s\",\"object\":{\"id\":\"%s\"}}".formatted(event, objectId));
    }

    private PaymentRequestedEvent request(UUID order, String method, String currency) {
        return new PaymentRequestedEvent(UUID.randomUUID(), NOW, order, "ORD-1", userId, "jane@example.com",
                new BigDecimal(AMOUNT), currency, method);
    }

    private OrderCancelledEvent cancelled() {
        return new OrderCancelledEvent(UUID.randomUUID(), NOW, orderId, "ORD-1", userId, "jane@example.com",
                "Cancelled by customer");
    }

    private static GatewayPayment pending(String id) {
        return new GatewayPayment(id, Status.PENDING, new BigDecimal(AMOUNT), "RUB",
                "ct-" + id, null, null);
    }

    private static GatewayPayment succeeded(String id) {
        return new GatewayPayment(id, Status.SUCCEEDED, new BigDecimal(AMOUNT), "RUB", null, null, null);
    }

    private static GatewayPayment canceled(String id, String reason) {
        return new GatewayPayment(id, Status.CANCELED, new BigDecimal(AMOUNT), "RUB", null, reason, null);
    }

    private static GatewayRefund refund(String id, Status status) {
        return new GatewayRefund(id, "yk-1", status, new BigDecimal(AMOUNT), "RUB", null);
    }

    private Payment payment() {
        return payments.values().stream().filter(payment -> payment.getOrderId().equals(orderId))
                .findFirst().orElseThrow();
    }

    private PaymentTransaction onlyTransaction() {
        assertThat(transactions).hasSize(1);
        return transactions.values().iterator().next();
    }

    private PaymentTransaction transactionOf(Payment payment) {
        return transactions.get(payment.getCurrentChargeId());
    }

    private <T extends DomainEvent> T captured(String routingKey, Class<T> type) {
        ArgumentCaptor<T> event = ArgumentCaptor.forClass(type);
        verify(outboxService).enqueue(eq(MessagingConstants.PAYMENT_EXCHANGE), eq(routingKey), event.capture(),
                eq(orderId));
        return event.getValue();
    }

    private void storeInMemory() {
        when(notificationRepository.save(any(PaymentNotification.class))).thenAnswer(invocation -> {
            PaymentNotification notification = invocation.getArgument(0);
            notifications.put(notification.getId(), notification);
            return notification;
        });
        when(notificationRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(notifications.get(invocation.<UUID>getArgument(0))));
        when(notificationRepository.existsByProviderAndEventAndObjectIdAndStatus(any(), any(), any(), any()))
                .thenAnswer(invocation -> notifications.values().stream().anyMatch(notification ->
                        notification.getProvider() == invocation.getArgument(0)
                                && notification.getEvent().equals(invocation.getArgument(1))
                                && notification.getObjectId().equals(invocation.getArgument(2))
                                && notification.getStatus() == invocation.getArgument(3)));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(UUID.randomUUID());
            }
            payments.put(payment.getId(), payment);
            return payment;
        });
        when(paymentRepository.findByOrderIdForUpdate(any())).thenAnswer(invocation -> payments.values().stream()
                .filter(payment -> payment.getOrderId().equals(invocation.getArgument(0))).findFirst());
        when(paymentRepository.findByOrderId(any())).thenAnswer(invocation -> payments.values().stream()
                .filter(payment -> payment.getOrderId().equals(invocation.getArgument(0))).findFirst());
        when(paymentRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(payments.get(invocation.<UUID>getArgument(0))));
        when(paymentRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                Optional.ofNullable(payments.get(invocation.<UUID>getArgument(0))));

        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getCreatedAt() == null) {
                transaction.setCreatedAt(NOW);
            }
            transactions.put(transaction.getId(), transaction);
            return transaction;
        });
        when(transactionRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(transactions.get(invocation.<UUID>getArgument(0))));
        when(transactionRepository.findPaymentIdById(any())).thenAnswer(invocation ->
                Optional.ofNullable(transactions.get(invocation.<UUID>getArgument(0)))
                        .map(PaymentTransaction::getPaymentId));
        when(transactionRepository.findAllByPaymentIdOrderByCreatedAtAsc(any())).thenAnswer(invocation ->
                transactions.values().stream()
                        .filter(transaction -> transaction.getPaymentId().equals(invocation.getArgument(0)))
                        .toList());
        when(transactionRepository.findByProviderAndProviderId(any(), any())).thenAnswer(invocation ->
                transactions.values().stream()
                        .filter(transaction -> transaction.getProvider() == invocation.getArgument(0)
                                && invocation.getArgument(1).equals(transaction.getProviderId()))
                        .findFirst());
        when(transactionRepository.findPendingIdsCreatedBefore(any(), any())).thenAnswer(invocation ->
                transactions.values().stream()
                        .filter(PaymentTransaction::isPending)
                        .map(PaymentTransaction::getId)
                        .toList());
    }
}
