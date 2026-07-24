package com.keepeat.backend.domain.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationBulkDeleteRequest(
        @NotEmpty(message = "삭제할 알림 ID를 1개 이상 입력해 주세요.")
        @Size(max = 100, message = "알림은 한 번에 최대 100개까지 삭제할 수 있습니다.")
        List<@NotNull @Positive Long> ids
) {}
