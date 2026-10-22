package com.shop.notification.template;

import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.PasswordResetRequestedEvent;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.config.NotificationProperties;
import com.shop.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Plain-text e-mails in Russian, the language of the shop's customers, rendered from events.
 * Amounts are written as {@code 1 234,50 ₽} and times as {@code dd.MM.yyyy HH:mm}, Moscow time.
 */
@Component
@RequiredArgsConstructor
public class NotificationTemplates {

    static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(SHOP_ZONE);

    private final NotificationProperties properties;

    public RenderedNotification welcome(UserRegisteredEvent event) {
        String greeting = StringUtils.hasText(event.firstName())
                ? "Здравствуйте, %s!".formatted(event.firstName().trim())
                : "Здравствуйте!";
        String body = """
                %s

                Спасибо за регистрацию в магазине %s. Ваш аккаунт привязан к адресу %s.
                Теперь вы можете оформлять заказы, следить за их статусом и сохранять адреса доставки.

                %s""".formatted(greeting, shop(), event.email(), signature());
        return new RenderedNotification(NotificationType.WELCOME, event.userId(), event.email(), null,
                "[%s] Добро пожаловать в %s".formatted(shop(), shop()), body);
    }

    /**
     * The link opens the reset page of the storefront; the token is URL-encoded. The validity is the
     * lifetime chosen by auth-service, rounded up to whole minutes.
     */
    public RenderedNotification passwordReset(PasswordResetRequestedEvent event) {
        String link = "%s/reset-password?token=%s".formatted(properties.frontendUrl(),
                URLEncoder.encode(event.token(), StandardCharsets.UTF_8));
        String body = """
                Здравствуйте!

                Мы получили запрос на сброс пароля для аккаунта %s в магазине %s.
                Чтобы задать новый пароль, перейдите по ссылке:

                %s

                Ссылка действительна %d мин. и может быть использована только один раз.
                Если вы не запрашивали сброс пароля, просто проигнорируйте это письмо: ваш пароль останется прежним.

                %s""".formatted(event.email(), shop(), link, validityMinutes(event), signature());
        return new RenderedNotification(NotificationType.PASSWORD_RESET, event.userId(), event.email(), null,
                "[%s] Сброс пароля".formatted(shop()), body);
    }

    public RenderedNotification orderPlaced(OrderCreatedEvent event) {
        String lines = event.items().stream()
                .map(item -> "  - %s, %d шт. — %s".formatted(item.productName(), item.quantity(),
                        MoneyFormatter.format(lineTotal(item), event.currency())))
                .collect(Collectors.joining("\n"));
        long units = event.items().stream().mapToLong(OrderItemPayload::quantity).sum();
        String body = """
                Здравствуйте!

                Мы получили ваш заказ %s от %s. В заказе %s:

                %s

                Итого к оплате с учётом доставки: %s

                Мы резервируем товары и обрабатываем оплату. Когда заказ будет подтверждён, мы пришлём ещё одно письмо.

                %s""".formatted(event.orderNumber(), dateTime(event.occurredAt()),
                RussianPlural.withCount(units, "товар", "товара", "товаров"), lines,
                MoneyFormatter.format(event.totalAmount(), event.currency()), signature());
        return new RenderedNotification(NotificationType.ORDER_PLACED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Заказ %s оформлен".formatted(shop(), event.orderNumber()), body);
    }

    public RenderedNotification orderConfirmed(OrderConfirmedEvent event) {
        String body = """
                Здравствуйте!

                Оплата заказа %s на сумму %s прошла успешно (%s).
                Заказ подтверждён, скоро мы передадим его в доставку.

                %s""".formatted(event.orderNumber(), MoneyFormatter.format(event.totalAmount(), event.currency()),
                dateTime(event.occurredAt()), signature());
        return new RenderedNotification(NotificationType.ORDER_CONFIRMED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Заказ %s оплачен и подтверждён".formatted(shop(), event.orderNumber()),
                body);
    }

    public RenderedNotification orderCancelled(OrderCancelledEvent event) {
        String reason = StringUtils.hasText(event.reason()) ? event.reason().trim() : "не указана";
        String body = """
                Здравствуйте!

                Заказ %s отменён (%s).
                Причина: %s

                Если заказ был оплачен, деньги вернутся тем же способом, которым вы платили.

                %s""".formatted(event.orderNumber(), dateTime(event.occurredAt()), reason, signature());
        return new RenderedNotification(NotificationType.ORDER_CANCELLED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Заказ %s отменён".formatted(shop(), event.orderNumber()), body);
    }

    /**
     * @return {@code dd.MM.yyyy HH:mm} in Moscow time
     */
    static String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    private static long validityMinutes(PasswordResetRequestedEvent event) {
        long seconds = Duration.between(event.occurredAt(), event.expiresAt()).toSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }

    private static BigDecimal lineTotal(OrderItemPayload item) {
        return item.unitPrice() == null ? null : item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private String shop() {
        return properties.shopName();
    }

    private String signature() {
        return "С уважением,\n" + shop();
    }

    /**
     * @param reference order number of order notifications
     */
    public record RenderedNotification(NotificationType type, UUID userId, String recipient, String reference,
                                       String subject, String body) {
    }
}
