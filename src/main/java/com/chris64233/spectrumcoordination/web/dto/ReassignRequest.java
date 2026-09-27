package com.chris64233.spectrumcoordination.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 未开始许可整体改频请求：指定新频率资源与完整新频段。 */
public record ReassignRequest(
        @NotNull Long resourceId,
        @PositiveOrZero @NotNull Long bandLowHz,
        @PositiveOrZero @NotNull Long bandHighHz) {
}
