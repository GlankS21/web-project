package com.shop.notification.service.impl;

import com.shop.common.dto.PageResponse;
import com.shop.common.exception.ResourceNotFoundException;
import com.shop.notification.config.NotificationProperties;
import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.Notification;
import com.shop.notification.entity.NotificationStatus;
import com.shop.notification.entity.NotificationType;
import com.shop.notification.mapper.NotificationMapper;
import com.shop.notification.repository.NotificationRepository;
import com.shop.notification.sender.EmailSender;
import com.shop.notification.service.NotificationService;
import com.shop.notification.template.NotificationTemplates.RenderedNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    static final int RETRY_BATCH_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final NotificationMapper notificationMapper;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public void notify(UUID eventId, RenderedNotification notification) {
        if (!StringUtils.hasText(notification.recipient())) {
            log.warn("No recipient for {} notification of event {}, skipped", notification.type(), eventId);
            return;
        }
        UUID notificationId = transactionTemplate.execute(status -> {
            if (notificationRepository.existsByEventId(eventId)) {
                return null;
            }
            Notification entity = new Notification();
            entity.setEventId(eventId);
            entity.setType(notification.type());
            entity.setUserId(notification.userId());
            entity.setRecipient(notification.recipient());
            entity.setReference(notification.reference());
            entity.setSubject(notification.subject());
            entity.setBody(notification.body());
            entity.setStatus(NotificationStatus.PENDING);
            return notificationRepository.save(entity).getId();
        });
        if (notificationId == null) {
            log.info("Event {} already notified, ignored", eventId);
            return;
        }
        deliver(notificationId);
    }

    @Override
    public int retryUndelivered() {
        List<UUID> ids = notificationRepository.findIdsToRetry(properties.retry().maxAttempts(),
                clock.instant().minus(properties.retry().interval()), PageRequest.of(0, RETRY_BATCH_SIZE));
        int delivered = 0;
        for (UUID id : ids) {
            if (deliver(id)) {
                delivered++;
            }
        }
        return delivered;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listOf(UUID userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findAllByUserId(userId, pageable),
                notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> search(NotificationStatus status, NotificationType type,
                                                     Pageable pageable) {
        Specification<Notification> specification = Specification.allOf(
                (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status),
                (root, query, cb) -> type == null ? null : cb.equal(root.get("type"), type));
        return PageResponse.from(notificationRepository.findAll(specification, pageable),
                notificationMapper::toResponse);
    }

    @Override
    public NotificationResponse resend(UUID notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("Notification", "id", notificationId);
        }
        deliver(notificationId);
        return notificationRepository.findById(notificationId)
                .map(notificationMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));
    }

    /**
     * Sends outside any transaction (an SMTP server may be slow), then records the outcome.
     *
     * @return whether the e-mail was sent
     */
    private boolean deliver(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) {
            return false;
        }
        try {
            emailSender.send(notification.getRecipient(), notification.getSubject(), notification.getBody());
        } catch (RuntimeException ex) {
            String error = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            updateStatus(notificationId, entity -> entity.markFailed(error));
            log.warn("Could not send {} e-mail {} to {} (attempt {}): {}", notification.getType(), notificationId,
                    notification.getRecipient(), notification.getAttempts() + 1, ex.getMessage());
            return false;
        }
        updateStatus(notificationId, entity -> entity.markSent(clock.instant()));
        log.info("Sent {} e-mail {} to {}", notification.getType(), notificationId, notification.getRecipient());
        return true;
    }

    private void updateStatus(UUID notificationId, Consumer<Notification> change) {
        transactionTemplate.executeWithoutResult(status ->
                notificationRepository.findById(notificationId).ifPresent(change));
    }
}
