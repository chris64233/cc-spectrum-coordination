package com.chris64233.spectrumcoordination.domain;

/**
 * 许可的存储状态。实际查询时会结合当前时间计算有效状态（见 {@link #effective}）。
 */
public enum LicenseStatus {
    /** 已批准但使用时间窗尚未开始，允许整体改频 */
    NOT_STARTED,
    /** 使用时间窗已开始且正在占用频率 */
    ACTIVE,
    /** 主动暂停，占用仍保留（保障恢复），只能恢复或提前终止 */
    SUSPENDED,
    /** 提前终止，历史保留，不再占用未来时间 */
    TERMINATED,
    /** 时间窗自然到期，历史保留 */
    EXPIRED;

    /**
     * 依据当前时间把存储状态折算为查询时的有效状态：
     * NOT_STARTED 到点自动视为 ACTIVE，ACTIVE 过期视为 EXPIRED。
     */
    public LicenseStatus effective(java.time.Instant now, java.time.Instant startTime, java.time.Instant endTime) {
        return switch (this) {
            case NOT_STARTED -> !now.isBefore(startTime)
                    ? (now.isBefore(endTime) ? ACTIVE : EXPIRED)
                    : NOT_STARTED;
            case ACTIVE -> !now.isBefore(endTime) ? EXPIRED : ACTIVE;
            default -> this;
        };
    }
}
