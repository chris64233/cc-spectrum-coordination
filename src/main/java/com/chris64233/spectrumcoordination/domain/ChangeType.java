package com.chris64233.spectrumcoordination.domain;

/**
 * 许可变更记录类型，覆盖申请批准、改频、暂停、恢复、提前终止及规则重新校验等事件。
 */
public enum ChangeType {
    /** 申请被原子批准，整段频率占用写入 */
    APPROVED,
    /** 申请经冲突判定被拒绝 */
    REJECTED,
    /** 申请被主动撤销 */
    CANCELLED,
    /** 未开始许可整体改频：新频完整批准、旧频随后释放 */
    RETUNED,
    /** 已开始许可暂停 */
    SUSPENDED,
    /** 暂停许可恢复 */
    RESUMED,
    /** 许可提前终止，历史保留 */
    TERMINATED,
    /** 规则版本升级后对待批准申请重新校验（批准时触发） */
    RULE_REVALIDATED
}
