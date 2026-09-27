package com.chris64233.spectrumcoordination.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 测试支撑：每个测试方法前清空全部业务表（重置自增 ID），保证用例间数据隔离。
 * 子表先于父表删除；H2 不允许对仍被外键引用的表执行 TRUNCATE，故使用 DELETE + 重置标识列。
 */
public class DatabaseCleaner {

    private static final String[] TABLES_IN_DELETE_ORDER = {
            "change_record",
            "frequency_occupancy",
            "frequency_application",
            "license",
            "id_sequence",
            "interference_rule_version",
            "station",
            "frequency_resource"
    };

    /** 含 IDENTITY 主键的表（id_sequence 的主键为字符串，不在其中） */
    private static final String[] IDENTITY_TABLES = {
            "change_record",
            "frequency_occupancy",
            "frequency_application",
            "license",
            "interference_rule_version",
            "station",
            "frequency_resource"
    };

    private final JdbcTemplate jdbc;
    private final MutableClock clock;

    public DatabaseCleaner(JdbcTemplate jdbc, MutableClock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void clean() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbc.update("DELETE FROM " + table);
        }
        for (String table : IDENTITY_TABLES) {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH 1");
        }
        clock.setInstant(TestClockConfig.BASE_TIME);
    }
}
