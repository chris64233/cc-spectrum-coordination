package com.chris64233.spectrumcoordination.domain;

/**
 * 频率申请的处理状态。
 */
public enum ApplicationStatus {
    /** 待批准（可重复提交幂等） */
    PENDING,
    /** 已批准，许可已原子生成 */
    APPROVED,
    /** 经冲突判定后拒绝（含规则版本升级后重新校验不通过） */
    REJECTED,
    /** 申请人主动撤销 */
    CANCELLED
}
