package com.chris64233.spectrumcoordination.domain;

/**
 * 干扰规则版本的生命周期。
 */
public enum RuleVersionStatus {
    /** 当前生效版本 */
    ACTIVE,
    /** 已被新版本取代 */
    SUPERSEDED
}
