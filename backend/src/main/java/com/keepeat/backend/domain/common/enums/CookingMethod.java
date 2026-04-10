package com.keepeat.backend.domain.common.enums;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum CookingMethod {
    볶음,

    @JsonProperty("국/찌개/탕")
    @JsonAlias("국_찌개_탕")
    국_찌개_탕,

    @JsonProperty("찜/조림")
    @JsonAlias("찜_조림")
    찜_조림,

    @JsonProperty("구이/전")
    @JsonAlias("구이_전")
    구이_전,

    @JsonProperty("무침/샐러드")
    @JsonAlias("무침_샐러드")
    무침_샐러드,

    @JsonProperty("면/만두")
    @JsonAlias("면_만두")
    면_만두,

    @JsonProperty("밥/죽/샌드위치")
    @JsonAlias("밥_죽_샌드위치")
    밥_죽_샌드위치,

    @JsonProperty("오븐/베이킹")
    @JsonAlias("오븐_베이킹")
    오븐_베이킹,

    기타
}
