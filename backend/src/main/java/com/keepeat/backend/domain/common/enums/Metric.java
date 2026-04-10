package com.keepeat.backend.domain.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Metric {
    Days(1),
    Weeks(7),
    Months(30),
    Years(365);

    private final int days;

    public int toDays() {
        return days;
    }
}
