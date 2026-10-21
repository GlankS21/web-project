package com.shop.notification.template;

import com.shop.common.event.OrderCancelledEvent;
import com.shop.common.event.OrderConfirmedEvent;
import com.shop.common.event.OrderCreatedEvent;
import com.shop.common.event.OrderItemPayload;
import com.shop.common.event.UserRegisteredEvent;
import com.shop.notification.config.NotificationProperties;
import com.shop.notification.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Plain-text e-mails (in Vietnamese, the language of the shop's customers) rendered from events.
 */
@Component
@RequiredArgsConstructor
public class NotificationTemplates {

    private final NotificationProperties properties;

    public RenderedNotification welcome(UserRegisteredEvent event) {
        String name = StringUtils.hasText(event.firstName()) ? event.firstName().trim() : "bạn";
        String body = """
                Chào %s,

                Cảm ơn bạn đã đăng ký tài khoản tại %s với địa chỉ e-mail %s.
                Từ bây giờ bạn có thể đặt hàng, theo dõi đơn hàng và lưu sổ địa chỉ giao hàng.

                %s""".formatted(name, shop(), event.email(), signature());
        return new RenderedNotification(NotificationType.WELCOME, event.userId(), event.email(), null,
                "[%s] Chào mừng bạn đến với %s".formatted(shop(), shop()), body);
    }

    public RenderedNotification orderPlaced(OrderCreatedEvent event) {
        String lines = event.items().stream()
                .map(item -> "  - %s x %d: %s".formatted(item.productName(), item.quantity(),
                        MoneyFormatter.format(lineTotal(item), event.currency())))
                .collect(Collectors.joining("\n"));
        String body = """
                Chào bạn,

                %s đã nhận được đơn hàng %s của bạn:

                %s

                Tổng thanh toán (đã gồm phí vận chuyển): %s

                Chúng tôi đang giữ hàng và xử lý thanh toán. Bạn sẽ nhận được e-mail khi đơn hàng được xác nhận.

                %s""".formatted(shop(), event.orderNumber(), lines,
                MoneyFormatter.format(event.totalAmount(), event.currency()), signature());
        return new RenderedNotification(NotificationType.ORDER_PLACED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Đã nhận đơn hàng %s".formatted(shop(), event.orderNumber()), body);
    }

    public RenderedNotification orderConfirmed(OrderConfirmedEvent event) {
        String body = """
                Chào bạn,

                Thanh toán %s cho đơn hàng %s đã thành công. Đơn hàng đã được xác nhận và sẽ sớm được giao.

                %s""".formatted(MoneyFormatter.format(event.totalAmount(), event.currency()), event.orderNumber(),
                signature());
        return new RenderedNotification(NotificationType.ORDER_CONFIRMED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Đơn hàng %s đã được xác nhận".formatted(shop(), event.orderNumber()), body);
    }

    public RenderedNotification orderCancelled(OrderCancelledEvent event) {
        String reason = StringUtils.hasText(event.reason()) ? event.reason() : "không rõ";
        String body = """
                Chào bạn,

                Đơn hàng %s đã bị huỷ.
                Lý do: %s

                Nếu bạn đã thanh toán, số tiền sẽ được hoàn lại theo phương thức thanh toán ban đầu.

                %s""".formatted(event.orderNumber(), reason, signature());
        return new RenderedNotification(NotificationType.ORDER_CANCELLED, event.userId(), event.userEmail(),
                event.orderNumber(), "[%s] Đơn hàng %s đã bị huỷ".formatted(shop(), event.orderNumber()), body);
    }

    private static BigDecimal lineTotal(OrderItemPayload item) {
        return item.unitPrice() == null ? null : item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
    }

    private String shop() {
        return properties.shopName();
    }

    private String signature() {
        return "Trân trọng,\n" + shop();
    }

    /**
     * @param reference order number of order notifications
     */
    public record RenderedNotification(NotificationType type, UUID userId, String recipient, String reference,
                                       String subject, String body) {
    }
}
