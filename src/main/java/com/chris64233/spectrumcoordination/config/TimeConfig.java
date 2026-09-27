package com.chris64233.spectrumcoordination.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** 统一时钟，便于时间相关状态（未开始/进行中/到期、终止截断）的可测注入。 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
