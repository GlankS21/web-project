package com.shop.order.scheduler;

import com.shop.order.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Cancels orders stuck in the saga (a service or the broker was down for too long), which releases
 * their reserved stock. Each order is cancelled in its own transaction under its row lock, so a reply
 * arriving at the same time is applied either before (and the order is skipped) or not at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    static final int BATCH_SIZE = 100;

    private final OrderSagaService orderSagaService;

    @Scheduled(fixedDelayString = "${app.order.expiry-check-interval:1m}")
    public void expireStaleOrders() {
        try {
            List<UUID> orderIds = orderSagaService.findExpiredOrderIds(BATCH_SIZE);
            int expired = 0;
            for (UUID orderId : orderIds) {
                if (orderSagaService.expireOrder(orderId)) {
                    expired++;
                }
            }
            if (expired > 0) {
                log.info("Cancelled {} order(s) not confirmed in time", expired);
            }
        } catch (RuntimeException ex) {
            // e.g. database unavailable; the next run tries again
            log.error("Order expiry run failed", ex);
        }
    }
}
