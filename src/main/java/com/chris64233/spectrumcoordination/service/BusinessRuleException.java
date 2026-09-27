package com.chris64233.spectrumcoordination.service;

/** 请求违反业务规则（如对已开始许可改频、频段非法等）。 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
