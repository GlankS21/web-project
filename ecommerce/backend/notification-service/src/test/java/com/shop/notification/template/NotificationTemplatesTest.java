package com.shop.notification.template;

import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.config.NotificationProperties;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.template.NotificationTemplates.RenderedNotification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    private final NotificationTemplates templates =
            new NotificationTemplates(new NotificationProperties(null, "TechShop", null));

    @Test
    void formatsAmountsTheVietnameseWay() {
        assertThat(MoneyFormatter.format(new BigDecimal("25140000"), "VND")).isEqualTo("25.140.000 VND");
        assertThat(MoneyFormatter.format(new BigDecimal("1234.5"), "USD")).isEqualTo("1.234,50 USD");
        assertThat(MoneyFormatter.format(null, "VND")).isEqualTo("-");
    }

    @Test
    void welcomesByFirstName() {
        UUID userId = UUID.randomUUID();
        RenderedNotification mail = templates.welcome(new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId,
                "jane@example.com", "Jane", "Doe"));

        assertThat(mail.type()).isEqualTo(NotificationType.WELCOME);
        assertThat(mail.userId()).isEqualTo(userId);
        assertThat(mail.recipient()).isEqualTo("jane@example.com");
        assertThat(mail.subject()).isEqualTo("[TechShop] Chào mừng bạn đến với TechShop");
        assertThat(mail.body()).startsWith("Chào Jane,").endsWith("Trân trọng,\nTechShop");
    }

    @Test
    void listsTheOrderedItems() {
        RenderedNotification mail = templates.orderPlaced(new OrderCreatedEvent(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), "ORD-20261021-ABCDEF", UUID.randomUUID(), "jane@example.com",
                List.of(new OrderItemPayload(7L, "LAP", "MacBook Air M2", 1, new BigDecimal("24990000")),
                        new OrderItemPayload(19L, "CAB", "USB-C cable", 2, new BigDecimal("75000"))),
                new BigDecimal("25140000"), "VND"));

        assertThat(mail.reference()).isEqualTo("ORD-20261021-ABCDEF");
        assertThat(mail.subject()).isEqualTo("[TechShop] Đã nhận đơn hàng ORD-20261021-ABCDEF");
        assertThat(mail.body())
                .contains("  - MacBook Air M2 x 1: 24.990.000 VND")
                .contains("  - USB-C cable x 2: 150.000 VND")
                .contains("Tổng thanh toán (đã gồm phí vận chuyển): 25.140.000 VND");
    }

    @Test
    void explainsTheCancellation() {
        RenderedNotification mail = templates.orderCancelled(new OrderCancelledEvent(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "jane@example.com", "Payment failed: Declined"));

        assertThat(mail.type()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(mail.body()).contains("Lý do: Payment failed: Declined");
    }
}
