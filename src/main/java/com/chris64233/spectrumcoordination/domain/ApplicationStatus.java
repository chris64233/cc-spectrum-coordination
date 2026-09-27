package com.chris64233.spectrumcoordination.domain;

/** 频率申请状态。 */
public enum ApplicationStatus {
    /** 待批准：批准前会按当前生效规则版本重新校验。 */
    PENDING,
    /** 已批准：已原子生成整段频率占用。 */
    APPROVED,
    /** 已驳回：与现有许可冲突，冲突快照可查询。 */
    REJECTED
}
