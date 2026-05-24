package com.keepeat.backend.domain.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record NotificationBulkDeleteRequest(
        @NotEmpty(message = "삭제할 알림 ID를 1개 이상 입력해 주세요.")
        List<Long> ids
) {}
