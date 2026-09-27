package com.chris64233.spectrumcoordination.config;

import com.chris64233.spectrumcoordination.service.RuleVersionService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动后初始化协调锁单行与默认规则版本。 */
@Component
public class DataInitializer implements ApplicationRunner {

    private final RuleVersionService ruleVersionService;

    public DataInitializer(RuleVersionService ruleVersionService) {
        this.ruleVersionService = ruleVersionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ruleVersionService.ensureInitialized();
    }
}
