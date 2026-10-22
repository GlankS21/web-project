package com.shop.notification.template;

import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.PasswordResetRequestedEvent;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.config.NotificationProperties;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.template.NotificationTemplates.RenderedNotification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    private static final String NBSP = " ";
    /** 13:05 in Moscow (UTC+3). */
    private static final Instant ORDERED_AT = Instant.parse("2026-10-21T10:05:00Z");

    private final NotificationTemplates templates =
            new NotificationTemplates(new NotificationProperties(null, "TechShop", null, "https://shop.example.com/"));

    @Test
    void welcomesByFirstName() {
        UUID userId = UUID.randomUUID();
        RenderedNotification mail = templates.welcome(new UserRegisteredEvent(UUID.randomUUID(), Instant.now(), userId,
                "ivan.petrov@example.ru", " Иван ", "Петров"));

        assertThat(mail.type()).isEqualTo(NotificationType.WELCOME);
        assertThat(mail.userId()).isEqualTo(userId);
        assertThat(mail.recipient()).isEqualTo("ivan.petrov@example.ru");
        assertThat(mail.subject()).isEqualTo("[TechShop] Добро пожаловать в TechShop");
        assertThat(mail.body())
                .startsWith("Здравствуйте, Иван!")
                .contains("Спасибо за регистрацию в магазине TechShop. Ваш аккаунт привязан к адресу "
                        + "ivan.petrov@example.ru.")
                .endsWith("С уважением,\nTechShop");
    }

    @Test
    void welcomesWithoutFirstName() {
        RenderedNotification mail = templates.welcome(new UserRegisteredEvent(UUID.randomUUID(), Instant.now(),
                UUID.randomUUID(), "ivan.petrov@example.ru", " ", null));

        assertThat(mail.body()).startsWith("Здравствуйте!\n");
    }

    @Test
    void sendsAnEncodedResetLinkWithItsValidity() {
        UUID userId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-21T10:00:00Z");
        RenderedNotification mail = templates.passwordReset(new PasswordResetRequestedEvent(UUID.randomUUID(),
                occurredAt, userId, "ivan.petrov@example.ru", "abc+/=_-x y", occurredAt.plus(Duration.ofMinutes(30))));

        assertThat(mail.type()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(mail.userId()).isEqualTo(userId);
        assertThat(mail.recipient()).isEqualTo("ivan.petrov@example.ru");
        assertThat(mail.reference()).isNull();
        assertThat(mail.subject()).isEqualTo("[TechShop] Сброс пароля");
        assertThat(mail.body())
                .startsWith("Здравствуйте!")
                // Trailing slash of the configured URL removed, token URL-encoded
                .contains("https://shop.example.com/reset-password?token=abc%2B%2F%3D_-x+y")
                .contains("Ссылка действительна 30 мин.")
                .contains("Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо")
                .endsWith("С уважением,\nTechShop");
    }

    @Test
    void roundsTheResetLinkValidityUpToWholeMinutes() {
        Instant occurredAt = Instant.parse("2026-10-21T10:00:00Z");
        RenderedNotification mail = templates.passwordReset(new PasswordResetRequestedEvent(UUID.randomUUID(),
                occurredAt, UUID.randomUUID(), "ivan.petrov@example.ru", "token", occurredAt.plusSeconds(90)));

        assertThat(mail.body()).contains("Ссылка действительна 2 мин.");
    }

    @Test
    void defaultsTheFrontendUrl() {
        assertThat(new NotificationProperties(null, null, null, null).frontendUrl())
                .isEqualTo("http://localhost:3000");
        assertThat(new NotificationProperties(null, null, null, " http://shop.local// ").frontendUrl())
                .isEqualTo("http://shop.local");
    }

    @Test
    void listsTheOrderedItemsInRoubles() {
        RenderedNotification mail = templates.orderPlaced(new OrderCreatedEvent(UUID.randomUUID(), ORDERED_AT,
                UUID.randomUUID(), "ORD-20261021-ABCDEF", UUID.randomUUID(), "ivan.petrov@example.ru",
                List.of(new OrderItemPayload(7L, "LAP", "MacBook Air M2", 1, new BigDecimal("124990")),
                        new OrderItemPayload(19L, "CAB", "Кабель USB-C", 2, new BigDecimal("750.25"))),
                new BigDecimal("126490.50"), "RUB"));

        assertThat(mail.type()).isEqualTo(NotificationType.ORDER_PLACED);
        assertThat(mail.reference()).isEqualTo("ORD-20261021-ABCDEF");
        assertThat(mail.subject()).isEqualTo("[TechShop] Заказ ORD-20261021-ABCDEF оформлен");
        assertThat(mail.body())
                .contains("Мы получили ваш заказ ORD-20261021-ABCDEF от 21.10.2026 13:05. В заказе 3 товара:")
                .contains("  - MacBook Air M2, 1 шт. — 124" + NBSP + "990,00" + NBSP + "₽")
                .contains("  - Кабель USB-C, 2 шт. — 1" + NBSP + "500,50" + NBSP + "₽")
                .contains("Итого к оплате с учётом доставки: 126" + NBSP + "490,50" + NBSP + "₽")
                .endsWith("С уважением,\nTechShop");
    }

    @Test
    void agreesTheItemCount() {
        assertThat(placedWith(1).body()).contains("В заказе 1 товар:");
        assertThat(placedWith(5).body()).contains("В заказе 5 товаров:");
        assertThat(placedWith(11).body()).contains("В заказе 11 товаров:");
        assertThat(placedWith(22).body()).contains("В заказе 22 товара:");
    }

    @Test
    void confirmsThePayment() {
        RenderedNotification mail = templates.orderConfirmed(new OrderConfirmedEvent(UUID.randomUUID(), ORDERED_AT,
                UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "ivan.petrov@example.ru", new BigDecimal("1234.5"),
                "RUB"));

        assertThat(mail.type()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(mail.subject()).isEqualTo("[TechShop] Заказ ORD-1 оплачен и подтверждён");
        assertThat(mail.body()).contains("Оплата заказа ORD-1 на сумму 1" + NBSP + "234,50" + NBSP
                + "₽ прошла успешно (21.10.2026 13:05).");
    }

    @Test
    void explainsTheCancellation() {
        RenderedNotification mail = templates.orderCancelled(new OrderCancelledEvent(UUID.randomUUID(), ORDERED_AT,
                UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "ivan.petrov@example.ru",
                "Оплата не прошла: Недостаточно средств для оплаты"));

        assertThat(mail.type()).isEqualTo(NotificationType.ORDER_CANCELLED);
        assertThat(mail.subject()).isEqualTo("[TechShop] Заказ ORD-1 отменён");
        assertThat(mail.body())
                .contains("Заказ ORD-1 отменён (21.10.2026 13:05).")
                .contains("Причина: Оплата не прошла: Недостаточно средств для оплаты");
    }

    @Test
    void cancellationWithoutReason() {
        RenderedNotification mail = templates.orderCancelled(new OrderCancelledEvent(UUID.randomUUID(), ORDERED_AT,
                UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "ivan.petrov@example.ru", null));

        assertThat(mail.body()).contains("Причина: не указана");
    }

    @Test
    void writesMoscowTime() {
        // Midnight crossing: 21:30 UTC is 00:30 the next day in Moscow
        assertThat(NotificationTemplates.dateTime(Instant.parse("2026-12-31T21:30:00Z"))).isEqualTo("01.01.2027 00:30");
        assertThat(NotificationTemplates.dateTime(null)).isEqualTo("—");
    }

    private RenderedNotification placedWith(int quantity) {
        return templates.orderPlaced(new OrderCreatedEvent(UUID.randomUUID(), ORDERED_AT, UUID.randomUUID(),
                "ORD-1", UUID.randomUUID(), "ivan.petrov@example.ru",
                List.of(new OrderItemPayload(19L, "CAB", "Кабель USB-C", quantity, new BigDecimal("500"))),
                new BigDecimal("500").multiply(BigDecimal.valueOf(quantity)), "RUB"));
    }
}
