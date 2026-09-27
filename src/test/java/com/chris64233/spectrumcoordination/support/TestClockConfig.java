package com.chris64233.spectrumcoordination.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 用可控时钟替换生产环境的系统时钟，并装配测试数据库清理器。
 */
@TestConfiguration
public class TestClockConfig {

    public static final Instant BASE_TIME = Instant.parse("2026-09-27T00:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return MutableClock.startAt(BASE_TIME);
    }

    @Bean
    public DatabaseCleaner databaseCleaner(JdbcTemplate jdbcTemplate, MutableClock mutableClock) {
        return new DatabaseCleaner(jdbcTemplate, mutableClock);
    }
}
