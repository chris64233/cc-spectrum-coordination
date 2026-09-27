package com.chris64233.spectrumcoordination.service;

/**
 * 业务编号格式。
 */
public final class BusinessNumbers {

    private BusinessNumbers() {
    }

    /** 许可编号由序列值生成，例如 1 -> LIC-00000001。 */
    public static String licenseNo(long sequenceValue) {
        return "LIC-" + String.format("%08d", sequenceValue);
    }
}
