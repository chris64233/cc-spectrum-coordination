package com.chris64233.spectrumcoordination.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 整体改频请求：给定新的完整频段（必须整体落在同一频率资源内）。
 */
public record RetuneRequest(
        @NotNull Long resourceId,
        @Positive double bandStartMhz,
        @Positive double bandEndMhz) {
}
