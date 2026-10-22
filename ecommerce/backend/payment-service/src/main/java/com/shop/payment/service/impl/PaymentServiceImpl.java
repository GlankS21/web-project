package com.shop.payment.service.impl;

import com.shop.common.autoconfigure.CommonMessagesAutoConfiguration;
import com.shop.common.dto.PageResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.exception.BusinessException;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.exception.ServiceUnavailableException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.config.PaymentProperties;
import com.shop.payment.dto.response.MockCardPaymentResponse;
import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.NotificationStatus;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentNotification;
import com.shop.payment.entity.PaymentProvider;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.entity.PaymentTransaction;
import com.shop.payment.entity.TransactionType;
import com.shop.payment.gateway.GatewayException;
import com.shop.payment.gateway.PaymentGateway;
import com.shop.payment.gateway.PaymentGateway.CreatePaymentRequest;
import com.shop.payment.gateway.PaymentGateway.CreateRefundRequest;
import com.shop.payment.gateway.PaymentGateway.GatewayPayment;
import com.shop.payment.gateway.PaymentGateway.GatewayRefund;
import com.shop.payment.gateway.mock.MockPaymentGateway;
import com.shop.payment.mapper.PaymentMapper;
import com.shop.payment.repository.PaymentNotificationRepository;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.repository.PaymentTransactionRepository;
import com.shop.payment.service.PaymentFailureMessages;
import com.shop.payment.service.PaymentLedger;
import com.shop.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Database work runs in short transactions of its own: no connection or row lock is held while the
 * payment provider is called. A transaction id is stored before its operation is sent to the provider
 * (as idempotence key), so a crash in between is repaired by sending it again with the same key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    static final String PAYMENT_EVENT_PREFIX = "payment.";
    static final String REFUND_SUCCEEDED_EVENT = "refund.succeeded";
    static final String METADATA_ORDER_ID = "orderId";
    static final String METADATA_ORDER_NUMBER = "orderNumber";
    static final String METADATA_TRANSACTION_ID = "transactionId";
    static final String PROVIDER_MISMATCH = "provider_mismatch";

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentNotificationRepository notificationRepository;
    private final PaymentGateway paymentGateway;
    private final PaymentLedger ledger;
    private final PaymentMapper paymentMapper;
    private final PaymentProperties properties;
    private final MessageSource messageSource;
    private final PaymentFailureMessages failureMessages;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void processPaymentRequest(PaymentRequestedEvent event) {
        UUID chargeId = transactionTemplate.execute(status -> {
            Optional<Payment> existing = paymentRepository.findByOrderIdForUpdate(event.orderId());
            if (existing.isPresent()) {
                log.info("Order {} already has a {} payment, payment request ignored", event.orderNumber(),
                        existing.get().getStatus());
                return null;
            }
            Payment payment = new Payment();
            payment.setOrderId(event.orderId());
            payment.setOrderNumber(event.orderNumber());
            payment.setUserId(event.userId());
            payment.setAmount(event.amount());
            payment.setCurrency(event.currency());
            payment.setStatus(PaymentStatus.PENDING);
            Optional<PaymentMethod> method = PaymentMethod.parse(event.paymentMethod());
            method.ifPresent(payment::setMethod);

            String invalid = validate(event, method.orElse(null));
            if (invalid != null) {
                ledger.decline(payment, invalid);
                return null;
            }
            paymentRepository.save(payment);
            if (method.orElseThrow() == PaymentMethod.COD) {
                ledger.acceptCashOnDelivery(payment);
                return null;
            }
            return ledger.openCharge(payment, paymentGateway.provider()).getId();
        });
        if (chargeId != null) {
            submitCharge(chargeId);
        }
    }

    @Override
    public void handleOrderCancelled(OrderCancelledEvent event) {
        UUID refundId = transactionTemplate.execute(status -> {
            Optional<Payment> existing = paymentRepository.findByOrderIdForUpdate(event.orderId());
            if (existing.isEmpty()) {
                Payment marker = new Payment();
                marker.setOrderId(event.orderId());
                marker.setOrderNumber(event.orderNumber());
                marker.setUserId(event.userId());
                marker.setStatus(PaymentStatus.CANCELLED);
                marker.setFailureReason(PaymentLedger.ORDER_CANCELLED);
                paymentRepository.save(marker);
                log.info("Order {} cancelled before payment; it will not be charged", event.orderNumber());
                return null;
            }
            return ledger.cancel(existing.get()).map(PaymentTransaction::getId).orElse(null);
        });
        if (refundId != null) {
            submitRefund(refundId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentOfOrder(UUID orderId, AuthenticatedUser caller) {
        return paymentRepository.findByOrderId(orderId)
                .filter(readableBy(caller))
                .map(this::toResponse)
                .orElseThrow(PaymentServiceImpl::paymentNotFound);
    }

    @Override
    public PaymentResponse retryPayment(UUID orderId, AuthenticatedUser caller) {
        UUID chargeId = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                    .filter(found -> caller.id().equals(found.getUserId()))
                    .orElseThrow(PaymentServiceImpl::paymentNotFound);
            if (payment.getMethod() != PaymentMethod.ONLINE) {
                throw new BusinessException("payment.error.retry-not-online");
            }
            return switch (payment.getStatus()) {
                // The current attempt can still be paid; one that never reached the provider is sent again
                case PENDING -> payment.getConfirmationToken() == null ? payment.getCurrentChargeId() : null;
                case FAILED -> {
                    if (payment.getCurrentChargeId() == null) {
                        // declined before any attempt: the order is being cancelled
                        throw new BusinessException("payment.error.retry-not-allowed");
                    }
                    if (payment.getAttempts() >= properties.maxAttempts()) {
                        throw new BusinessException("payment.error.too-many-attempts", properties.maxAttempts());
                    }
                    yield ledger.openCharge(payment, paymentGateway.provider()).getId();
                }
                default -> throw new BusinessException("payment.error.retry-not-allowed");
            };
        });
        if (chargeId != null) {
            submitCharge(chargeId);
        }
        return transactionTemplate.execute(status -> toResponse(paymentRepository.findByOrderId(orderId).orElseThrow()));
    }

    @Override
    public PaymentResponse refreshPayment(UUID orderId, AuthenticatedUser caller) {
        List<UUID> pending = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByOrderId(orderId)
                    .filter(readableBy(caller))
                    .orElseThrow(PaymentServiceImpl::paymentNotFound);
            return transactionRepository.findAllByPaymentIdOrderByCreatedAtAsc(payment.getId()).stream()
                    .filter(PaymentTransaction::isPending)
                    .map(PaymentTransaction::getId)
                    .toList();
        });
        for (UUID transactionId : pending) {
            try {
                synchronize(transactionId);
            } catch (GatewayException ex) {
                log.warn("Could not refresh transaction {} of order {}: {}", transactionId, orderId, ex.getMessage());
            }
        }
        return transactionTemplate.execute(status -> toResponse(paymentRepository.findByOrderId(orderId).orElseThrow()));
    }

    @Override
    public void handleProviderNotification(String event, String objectId, String payload) {
        PaymentProvider provider = paymentGateway.provider();
        UUID notificationId = transactionTemplate.execute(status -> {
            PaymentNotification notification = PaymentNotification.received(provider, event, objectId, payload,
                    clock.instant());
            boolean duplicate = notificationRepository.existsByProviderAndEventAndObjectIdAndStatus(provider, event,
                    objectId, NotificationStatus.PROCESSED);
            if (duplicate) {
                notification.finish(NotificationStatus.DUPLICATE, null, null, clock.instant());
            }
            notificationRepository.save(notification);
            return duplicate ? null : notification.getId();
        });
        if (notificationId == null) {
            log.info("Notification {} for {} already processed, duplicate ignored", event, objectId);
            return;
        }
        NotificationOutcome outcome;
        try {
            outcome = verifyAndApply(event, objectId);
        } catch (GatewayException ex) {
            log.warn("Notification {} for {} could not be verified: {}", event, objectId, ex.getMessage());
            finishNotification(notificationId, NotificationStatus.FAILED, null, ex.getMessage());
            if (!ex.isRetryable()) {
                // The provider does not know the object: the notification did not come from it
                throw ResourceNotFoundException.of("payment.error.not-found");
            }
            // The provider sends the notification again later
            throw new ServiceUnavailableException("payment.error.provider-unavailable");
        }
        finishNotification(notificationId,
                outcome.finalState() ? NotificationStatus.PROCESSED : NotificationStatus.IGNORED,
                outcome.transactionId(), null);
    }

    /**
     * Reads the notified object from the provider and applies its state.
     */
    private NotificationOutcome verifyAndApply(String event, String objectId) {
        if (event.startsWith(PAYMENT_EVENT_PREFIX)) {
            GatewayPayment state = paymentGateway.getPayment(objectId);
            Optional<UUID> chargeId = transactionTemplate.execute(status -> findCharge(state));
            if (chargeId == null || chargeId.isEmpty()) {
                log.warn("Notification {} for unknown payment {}, ignored", event, objectId);
                return NotificationOutcome.IGNORED;
            }
            log.info("Notification {} for payment {}: provider reports {}{}", event, objectId, state.status(),
                    state.cancellationReason() == null ? "" : " (" + state.cancellationReason() + ")");
            applyChargeState(chargeId.get(), state);
            return new NotificationOutcome(chargeId.get(), isFinal(state.status()));
        }
        if (REFUND_SUCCEEDED_EVENT.equals(event)) {
            GatewayRefund state = paymentGateway.getRefund(objectId);
            Optional<UUID> refundId = transactionTemplate.execute(status -> transactionRepository
                    .findByProviderAndProviderId(paymentGateway.provider(), state.id())
                    .filter(transaction -> transaction.getType() == TransactionType.REFUND)
                    .map(PaymentTransaction::getId));
            if (refundId == null || refundId.isEmpty()) {
                // A refund not recorded yet is picked up by the reconciliation job
                log.warn("Notification {} for unknown refund {}, ignored", event, objectId);
                return NotificationOutcome.IGNORED;
            }
            log.info("Notification {} for refund {}: provider reports {}", event, objectId, state.status());
            applyRefundState(refundId.get(), state);
            return new NotificationOutcome(refundId.get(), isFinal(state.status()));
        }
        log.info("Notification {} for {} ignored", event, objectId);
        return NotificationOutcome.IGNORED;
    }

    private void finishNotification(UUID notificationId, NotificationStatus status, UUID transactionId, String error) {
        transactionTemplate.executeWithoutResult(tx -> notificationRepository.findById(notificationId)
                .ifPresent(notification -> notification.finish(status, transactionId, error, clock.instant())));
    }

    private static boolean isFinal(PaymentGateway.Status status) {
        return status == PaymentGateway.Status.SUCCEEDED || status == PaymentGateway.Status.CANCELED;
    }

    @Override
    public MockCardPaymentResponse payWithMockCard(String providerPaymentId, String cardNumber, YearMonth expiry,
                                                   String threeDSecureCode, AuthenticatedUser caller) {
        if (!(paymentGateway instanceof MockPaymentGateway mockGateway)) {
            throw ResourceNotFoundException.of("payment.error.not-found");
        }
        PaymentTransaction charge = transactionTemplate.execute(status -> transactionRepository
                .findByProviderAndProviderId(PaymentProvider.MOCK, providerPaymentId)
                .filter(transaction -> transaction.getType() == TransactionType.CHARGE)
                .filter(transaction -> paymentRepository.findById(transaction.getPaymentId())
                        .map(payment -> caller.id().equals(payment.getUserId()))
                        .orElse(false))
                .orElseThrow(PaymentServiceImpl::paymentNotFound));
        MockPaymentGateway.CardOutcome outcome = mockGateway.pay(providerPaymentId, cardNumber, expiry, threeDSecureCode);
        if (!outcome.threeDSecureRequired()) {
            applyChargeState(charge.getId(), outcome.payment());
        }
        PaymentResponse payment = transactionTemplate.execute(status ->
                toResponse(paymentRepository.findById(charge.getPaymentId()).orElseThrow()));
        return new MockCardPaymentResponse(outcome.threeDSecureRequired(), payment);
    }

    @Override
    public int reconcilePendingTransactions() {
        Instant now = clock.instant();
        PaymentProperties.Reconciliation settings = properties.reconciliation();
        List<UUID> pending = transactionTemplate.execute(status -> transactionRepository
                .findPendingIdsCreatedBefore(now.minus(settings.minAge()), PageRequest.of(0, settings.batchSize())));
        if (pending == null) {
            return 0;
        }
        Instant deadline = now.minus(settings.maxAge());
        for (UUID transactionId : pending) {
            try {
                synchronize(transactionId);
            } catch (RuntimeException ex) {
                log.warn("Reconciliation of transaction {} failed: {}", transactionId, ex.getMessage());
            }
            giveUpIfOlderThan(transactionId, deadline);
        }
        return pending.size();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> searchPayments(PaymentStatus status, Pageable pageable) {
        Page<Payment> page = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findAllByStatus(status, pageable);
        return PageResponse.from(page, payment -> paymentMapper.toSummary(payment, failureMessages));
    }

    /**
     * Sends a charge that has not reached the provider yet. A failure ends the attempt: the customer
     * can pay again.
     */
    private void submitCharge(UUID chargeId) {
        CreatePaymentRequest request = transactionTemplate.execute(status -> transactionRepository.findById(chargeId)
                .filter(charge -> charge.isPending() && charge.getProviderId() == null)
                .map(charge -> createPaymentRequest(paymentRepository.findById(charge.getPaymentId()).orElseThrow(),
                        charge))
                .orElse(null));
        if (request == null) {
            return;
        }
        GatewayPayment created;
        try {
            created = paymentGateway.createPayment(request);
        } catch (GatewayException ex) {
            log.warn("Payment attempt {} of order {} could not be created: {}", chargeId, request.orderNumber(),
                    ex.getMessage());
            withLockedTransaction(chargeId, (payment, charge) -> {
                if (charge.isPending() && charge.getProviderId() == null) {
                    ledger.failCharge(payment, charge, ex.getReasonCode());
                }
                return null;
            });
            return;
        }
        applyChargeState(chargeId, created);
    }

    /**
     * Sends a refund that has not reached the provider yet. Temporary failures are retried by the
     * reconciliation job with the same idempotence key.
     */
    private void submitRefund(UUID refundId) {
        RefundSubmission submission = transactionTemplate.execute(status -> transactionRepository.findById(refundId)
                .filter(refund -> refund.isPending() && refund.getProviderId() == null)
                .map(this::refundSubmission)
                .orElse(null));
        if (submission == null) {
            return;
        }
        if (submission.provider() == PaymentProvider.LEGACY) {
            // Paid with the simulated gateway of the first version: no real money to give back
            applyRefundState(refundId, new GatewayRefund("LEGACY-RFD-" + refundId, submission.request().paymentId(),
                    PaymentGateway.Status.SUCCEEDED, submission.request().amount(), submission.request().currency(),
                    null));
            return;
        }
        if (submission.provider() != paymentGateway.provider()) {
            giveUp(refundId, PROVIDER_MISMATCH);
            return;
        }
        GatewayRefund created;
        try {
            created = paymentGateway.createRefund(submission.request());
        } catch (GatewayException ex) {
            if (ex.isRetryable()) {
                log.warn("Refund {} could not be sent, it will be retried: {}", refundId, ex.getMessage());
                return;
            }
            log.error("Refund {} rejected by the provider: {}", refundId, ex.getMessage());
            giveUp(refundId, ex.getReasonCode());
            return;
        }
        applyRefundState(refundId, created);
    }

    /**
     * Brings a pending transaction up to date with the provider.
     */
    private void synchronize(UUID transactionId) {
        PaymentTransaction transaction = transactionTemplate.execute(status ->
                transactionRepository.findById(transactionId).orElse(null));
        if (transaction == null || !transaction.isPending()) {
            return;
        }
        if (transaction.getType() == TransactionType.REFUND) {
            if (transaction.getProviderId() == null) {
                submitRefund(transactionId);
            } else if (transaction.getProvider() != paymentGateway.provider()) {
                giveUp(transactionId, PROVIDER_MISMATCH);
            } else {
                applyRefundState(transactionId, paymentGateway.getRefund(transaction.getProviderId()));
            }
            return;
        }
        if (transaction.getProvider() != paymentGateway.provider()) {
            // e.g. a mock attempt while the shop now runs on YooKassa: nobody can complete it
            giveUp(transactionId, PROVIDER_MISMATCH);
        } else if (transaction.getProviderId() == null) {
            submitCharge(transactionId);
        } else {
            applyChargeState(transactionId, paymentGateway.getPayment(transaction.getProviderId()));
        }
    }

    private void applyChargeState(UUID chargeId, GatewayPayment state) {
        Optional<UUID> refund = withLockedTransaction(chargeId, (payment, charge) ->
                ledger.applyCharge(payment, charge, state).map(PaymentTransaction::getId));
        if (refund != null) {
            refund.ifPresent(this::submitRefund);
        }
    }

    private void applyRefundState(UUID refundId, GatewayRefund state) {
        withLockedTransaction(refundId, (payment, refund) -> {
            ledger.applyRefund(payment, refund, state);
            return null;
        });
    }

    private void giveUp(UUID transactionId, String reason) {
        withLockedTransaction(transactionId, (payment, transaction) -> {
            if (transaction.isPending()) {
                if (transaction.getType() == TransactionType.CHARGE) {
                    ledger.failCharge(payment, transaction, reason);
                } else {
                    ledger.failRefund(payment, transaction, reason);
                }
            }
            return null;
        });
    }

    private void giveUpIfOlderThan(UUID transactionId, Instant deadline) {
        withLockedTransaction(transactionId, (payment, transaction) -> {
            if (transaction.isPending() && transaction.getCreatedAt().isBefore(deadline)) {
                log.warn("Transaction {} of order {} still pending at the provider, given up",
                        transactionId, payment.getOrderNumber());
                if (transaction.getType() == TransactionType.CHARGE) {
                    ledger.failCharge(payment, transaction, PaymentLedger.EXPIRED);
                } else {
                    ledger.failRefund(payment, transaction, PaymentLedger.EXPIRED);
                }
            }
            return null;
        });
    }

    /**
     * Runs the action in a transaction, holding the row lock of the payment the transaction belongs to.
     * The transaction is loaded after the lock, so its state is current.
     */
    private <T> T withLockedTransaction(UUID transactionId, BiFunction<Payment, PaymentTransaction, T> action) {
        return transactionTemplate.execute(status -> {
            UUID paymentId = transactionRepository.findPaymentIdById(transactionId)
                    .orElseThrow(() -> new IllegalStateException("Unknown payment transaction " + transactionId));
            Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
            PaymentTransaction transaction = transactionRepository.findById(transactionId).orElseThrow();
            return action.apply(payment, transaction);
        });
    }

    /**
     * The charge of a notified payment: by its provider id, or by the transaction id stored in the
     * metadata when the notification overtook the answer to the creation call.
     */
    private Optional<UUID> findCharge(GatewayPayment state) {
        Optional<PaymentTransaction> byProviderId = transactionRepository
                .findByProviderAndProviderId(paymentGateway.provider(), state.id());
        if (byProviderId.isPresent()) {
            return byProviderId.map(PaymentTransaction::getId);
        }
        String transactionId = state.metadata().get(METADATA_TRANSACTION_ID);
        if (transactionId == null) {
            return Optional.empty();
        }
        try {
            return transactionRepository.findById(UUID.fromString(transactionId))
                    .filter(charge -> charge.getType() == TransactionType.CHARGE)
                    .filter(charge -> charge.getProvider() == paymentGateway.provider())
                    .filter(charge -> charge.getProviderId() == null || charge.getProviderId().equals(state.id()))
                    .map(PaymentTransaction::getId);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private CreatePaymentRequest createPaymentRequest(Payment payment, PaymentTransaction charge) {
        return new CreatePaymentRequest(
                charge.getId().toString(),
                payment.getOrderId(),
                payment.getOrderNumber(),
                charge.getAmount(),
                charge.getCurrency(),
                message("payment.description", payment.getOrderNumber()),
                Map.of(METADATA_ORDER_ID, payment.getOrderId().toString(),
                        METADATA_ORDER_NUMBER, payment.getOrderNumber(),
                        METADATA_TRANSACTION_ID, charge.getId().toString()));
    }

    private RefundSubmission refundSubmission(PaymentTransaction refund) {
        PaymentTransaction charge = transactionRepository.findById(refund.getParentId()).orElseThrow();
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
        return new RefundSubmission(refund.getProvider(), new CreateRefundRequest(
                refund.getId().toString(),
                charge.getProviderId(),
                refund.getAmount(),
                refund.getCurrency(),
                message("payment.refund-description", payment.getOrderNumber())));
    }

    private String validate(PaymentRequestedEvent event, PaymentMethod method) {
        if (event.amount() == null || event.amount().signum() <= 0 || event.currency() == null) {
            return PaymentLedger.INVALID_AMOUNT;
        }
        if (method == null) {
            return PaymentLedger.UNSUPPORTED_METHOD;
        }
        if (method == PaymentMethod.ONLINE && !properties.currency().equals(event.currency())) {
            return PaymentLedger.UNSUPPORTED_CURRENCY;
        }
        return null;
    }

    private PaymentResponse toResponse(Payment payment) {
        return paymentMapper.toResponse(payment,
                transactionRepository.findAllByPaymentIdOrderByCreatedAtAsc(payment.getId()), failureMessages);
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, CommonMessagesAutoConfiguration.DEFAULT_LOCALE);
    }

    private static Predicate<Payment> readableBy(AuthenticatedUser caller) {
        return payment -> caller.isAdmin() || caller.id().equals(payment.getUserId());
    }

    private static ResourceNotFoundException paymentNotFound() {
        return ResourceNotFoundException.of("payment.error.not-found");
    }

    private record RefundSubmission(PaymentProvider provider, CreateRefundRequest request) {
    }

    /**
     * @param transactionId the charge or refund concerned, {@code null} when unknown
     * @param finalState    the provider confirmed a final state: the same notification needs no handling again
     */
    private record NotificationOutcome(UUID transactionId, boolean finalState) {

        static final NotificationOutcome IGNORED = new NotificationOutcome(null, false);
    }
}
