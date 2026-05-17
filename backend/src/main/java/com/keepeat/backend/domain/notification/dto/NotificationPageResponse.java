package com.keepeat.backend.domain.notification.dto;

import java.util.List;

public record NotificationPageResponse(
        List<NotificationResponse> notifications,
        boolean hasNext,
        Long nextCursor
) {}