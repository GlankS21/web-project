package com.shop.payment.mapper;

import com.shop.payment.dto.response.PaymentResponse;
import com.shop.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper
public interface PaymentMapper {

    PaymentResponse toResponse(Payment payment);
}
