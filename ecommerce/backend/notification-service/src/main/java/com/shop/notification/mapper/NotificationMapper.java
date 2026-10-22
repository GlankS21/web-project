package com.shop.notification.mapper;

import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper
public interface NotificationMapper {

    @Mapping(target = "body", source = ".", qualifiedByName = "visibleBody")
    NotificationResponse toResponse(Notification notification);

    /**
     * A confidential body (password reset link) is never exposed, neither to the customer history
     * nor to administrators.
     */
    @Named("visibleBody")
    default String visibleBody(Notification notification) {
        return notification.getType() != null && notification.getType().hasConfidentialBody()
                ? null
                : notification.getBody();
    }
}
