package com.shop.notification.mapper;

import com.shop.notification.dto.response.NotificationResponse;
import com.shop.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
