package com.chris64233.spectrumcoordination.domain;

/** 许可状态（历史行保留，不做物理删除）。 */
public enum LicenseStatus {
    /** 未开始：允许整体改频。 */
    NOT_STARTED,
    /** 已开始（生效中）。 */
    ACTIVE,
    /** 已暂停：占用即时释放，但许可行保留。 */
    PAUSED,
    /** 提前终止：占用释放，许可行保留。 */
    TERMINATED
}
