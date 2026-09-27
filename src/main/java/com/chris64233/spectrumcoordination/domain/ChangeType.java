package com.chris64233.spectrumcoordination.domain;

/** 许可生命周期事件类型。 */
public enum ChangeType {
    /** 批准发证。 */
    ISSUED,
    /** 到达开始时间自动生效。 */
    STARTED,
    /** 整体改频成功（旧频率在新频率完整批准后才释放）。 */
    REASSIGNED,
    /** 改频失败：未拿到新频率，原许可保持有效。 */
    REASSIGN_FAILED,
    /** 暂停（仅已开始许可）。 */
    PAUSED,
    /** 提前终止（未开始/已开始/已暂停许可均可）。 */
    TERMINATED
}
