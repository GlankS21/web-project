package com.shop.payment.mapper;

import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.dto.response.PaymentTransactionResponse;
import com.shop.payment.entity.Payment;
import com.shop.payment.entity.PaymentTransaction;
import com.shop.payment.service.PaymentFailureMessages;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * The failure reason codes are returned as they are, with their Russian text in {@code failureMessage}.
 */
@Mapper
public interface PaymentMapper {

    @Mapping(target = "failureMessage", expression = "java(failureMessages.describe(payment.getFailureReason()))")
    PaymentResponse toResponse(Payment payment, List<PaymentTransaction> transactions,
                               @Context PaymentFailureMessages failureMessages);

    @Mapping(target = "failureMessage",
            expression = "java(failureMessages.describe(transaction.getFailureReason()))")
    PaymentTransactionResponse toResponse(PaymentTransaction transaction,
                                          @Context PaymentFailureMessages failureMessages);

    /**
     * Without the transaction history, for lists.
     */
    default PaymentResponse toSummary(Payment payment, PaymentFailureMessages failureMessages) {
        return toResponse(payment, List.of(), failureMessages);
    }
}
