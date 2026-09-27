package com.chris64233.spectrumcoordination.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 测试用可控时钟：固定在某个基准时间，可手动推进，便于验证未开始/进行中/到期/终止逻辑。
 */
public class MutableClock extends Clock {

    private Instant current;
    private final ZoneId zone;

    public MutableClock(Instant start, ZoneId zone) {
        this.current = start;
        this.zone = zone;
    }

    public static MutableClock startAt(Instant start) {
        return new MutableClock(start, ZoneOffset.UTC);
    }

    public void setInstant(Instant instant) {
        this.current = instant;
    }

    /** 向前推进给定秒数。 */
    public void advanceSeconds(long seconds) {
        this.current = current.plusSeconds(seconds);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
