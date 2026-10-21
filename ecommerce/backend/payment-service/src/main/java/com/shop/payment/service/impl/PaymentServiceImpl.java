package com.shop.payment.service.impl;

import com.shop.common.constant.MessagingConstants;
import com.shop.common.dto.PageResponse;
import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.PaymentFailedEvent;
import com.shop.common.event.PaymentRequestedEvent;
import com.shop.common.event.PaymentSucceededEvent;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.common.security.AuthenticatedUser;
import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentMethod;
import com.shop.payment.entity.PaymentStatus;
import com.shop.payment.gateway.PaymentGateway;
import com.shop.payment.gateway.PaymentGateway.ChargeRequest;
import com.shop.payment.gateway.PaymentGateway.ChargeResult;
import com.shop.payment.mapper.PaymentMapper;
import com.shop.payment.messaging.outbox.OutboxService;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    static final int MAX_REASON_LENGTH = 500;

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final OutboxService outboxService;
    private final PaymentMapper paymentMapper;
    private final Clock clock;

    @Override
    @Transactional
    public void processPaymentRequest(PaymentRequestedEvent event) {
        Optional<Payment> existing = paymentRepository.findByOrderIdForUpdate(event.orderId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (payment.getStatus() == PaymentStatus.CANCELLED) {
                log.info("Order {} was cancelled before its payment request, not charged", event.orderNumber());
            } else {
                log.info("Order {} already has a {} payment, {} ignored", event.orderNumber(), payment.getStatus(),
                        MessagingConstants.ORDER_PAYMENT_REQUESTED_KEY);
            }
            return;
        }

        Payment payment = new Payment();
        payment.setOrderId(event.orderId());
        payment.setOrderNumber(event.orderNumber());
        payment.setUserId(event.userId());
        payment.setAmount(event.amount());
        payment.setCurrency(event.currency());

        Optional<PaymentMethod> method = PaymentMethod.parse(event.paymentMethod());
        ChargeResult result;
        if (event.amount() == null || event.amount().signum() <= 0 || event.currency() == null) {
            result = ChargeResult.declined("Invalid payment amount");
        } else if (method.isEmpty()) {
            result = ChargeResult.declined("Unsupported payment method " + event.paymentMethod());
        } else {
            payment.setMethod(method.get());
            result = paymentGateway.charge(new ChargeRequest(event.orderId(), event.orderNumber(), event.amount(),
                    event.currency(), method.get()));
        }

        if (result.approved()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setTransactionId(result.transactionId());
            paymentRepository.save(payment);
            outboxService.enqueue(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_SUCCEEDED_KEY,
                    new PaymentSucceededEvent(UUID.randomUUID(), clock.instant(), payment.getId(), event.orderId(),
                            event.orderNumber(), result.transactionId(), event.amount(), event.currency()),
                    event.orderId());
            log.info("Payment of order {} succeeded ({} {}, transaction {})", event.orderNumber(), event.amount(),
                    event.currency(), result.transactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(truncate(result.declineReason()));
            paymentRepository.save(payment);
            outboxService.enqueue(MessagingConstants.PAYMENT_EXCHANGE, MessagingConstants.PAYMENT_FAILED_KEY,
                    new PaymentFailedEvent(UUID.randomUUID(), clock.instant(), payment.getId(), event.orderId(),
                            event.orderNumber(), event.amount(), event.currency(), payment.getFailureReason()),
                    event.orderId());
            log.warn("Payment of order {} failed: {}", event.orderNumber(), result.declineReason());
        }
    }

    @Override
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event) {
        Optional<Payment> existing = paymentRepository.findByOrderIdForUpdate(event.orderId());
        if (existing.isEmpty()) {
            Payment marker = new Payment();
            marker.setOrderId(event.orderId());
            marker.setOrderNumber(event.orderNumber());
            marker.setUserId(event.userId());
            marker.setStatus(PaymentStatus.CANCELLED);
            marker.setFailureReason(truncate("Order cancelled before payment: " + event.reason()));
            paymentRepository.save(marker);
            log.info("Order {} cancelled before payment; it will not be charged", event.orderNumber());
            return;
        }
        Payment payment = existing.get();
        switch (payment.getStatus()) {
            case SUCCEEDED -> {
                payment.setRefundTransactionId(paymentGateway.refund(payment.getTransactionId(), payment.getAmount(),
                        payment.getCurrency()));
                payment.setStatus(PaymentStatus.REFUNDED);
                payment.setRefundedAt(clock.instant());
                log.info("Refunded payment {} of cancelled order {}", payment.getId(), event.orderNumber());
            }
            case FAILED, REFUNDED, CANCELLED -> log.info("Payment of order {} is {}, nothing to refund",
                    event.orderNumber(), payment.getStatus());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentOfOrder(UUID orderId, AuthenticatedUser caller) {
        return paymentRepository.findByOrderId(orderId)
                .filter(payment -> caller.isAdmin() || caller.id().equals(payment.getUserId()))
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> searchPayments(PaymentStatus status, Pageable pageable) {
        Page<Payment> page = status == null
                ? paymentRepository.findAll(pageable)
                : paymentRepository.findAllByStatus(status, pageable);
        return PageResponse.from(page, paymentMapper::toResponse);
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
