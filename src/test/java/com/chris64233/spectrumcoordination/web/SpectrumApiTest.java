package com.chris64233.spectrumcoordination.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.chris64233.spectrumcoordination.support.DatabaseCleaner;
import com.chris64233.spectrumcoordination.support.TestClockConfig;

/**
 * REST 端到端测试：基础数据、申请裁决全链路与错误响应。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class SpectrumApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private DatabaseCleaner cleaner;

    private static final Instant T0 = TestClockConfig.BASE_TIME;
    private static final String START = T0.plus(1, ChronoUnit.HOURS).toString();
    private static final String END = T0.plus(12, ChronoUnit.HOURS).toString();

    @BeforeEach
    void cleanDb() {
        cleaner.clean();
    }

    @Test
    void fullWorkflowOverHttp() throws Exception {
        // 规则版本
        mockMvc.perform(post("/api/rule-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coChannelReuseDistanceKm": 0, "description": "v1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // 两个台站
        mockMvc.perform(post("/api/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stationCode":"W-A","name":"甲","latitude":0,"longitude":0,
                         "coverageRadiusKm":10,"transmitPowerW":100,"deviceType":"A"}
                        """)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stationCode":"W-B","name":"乙","latitude":0,"longitude":0,
                         "coverageRadiusKm":10,"transmitPowerW":100,"deviceType":"A"}
                        """)).andExpect(status().isCreated());

        // 频率资源 100-200，同频上限 1
        mockMvc.perform(post("/api/resources").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bandStartMhz":100,"bandEndMhz":200,"regionLatitude":0,
                         "regionLongitude":0,"regionRadiusKm":1000,"maxCoChannelUsers":1}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());

        String app1 = """
                {"applicationNo":"HTTP-1","stationCode":"W-A","resourceId":1,
                 "bandStartMhz":100,"bandEndMhz":110,"useLatitude":0,"useLongitude":0,
                 "useRadiusKm":10,"startTime":"%s","endTime":"%s"}
                """.formatted(START, END);
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(app1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        // 幂等重放
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(app1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        // 裁决：批准
        mockMvc.perform(post("/api/applications/HTTP-1/decide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.licenseNo").value("LIC-00000001"));

        // 第二申请：同频同地同时段，拒绝并返回冲突对象
        String app2 = app1.replace("HTTP-1", "HTTP-2").replace("W-A", "W-B");
        mockMvc.perform(post("/api/applications").contentType(MediaType.APPLICATION_JSON).content(app2))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/applications/HTTP-2/decide"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.conflicts[0].licenseNo").value("LIC-00000001"));

        // 冲突预演查询
        mockMvc.perform(get("/api/applications/HTTP-2/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationCode").value("W-A"));

        // 许可查询与变更记录
        mockMvc.perform(get("/api/licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licenseNo").value("LIC-00000001"))
                .andExpect(jsonPath("$[0].effectiveStatus").value("NOT_STARTED"));
        mockMvc.perform(get("/api/licenses/LIC-00000001/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeType").value("APPROVED"));
    }

    @Test
    void returns404ForMissingApplicationOnDecide() throws Exception {
        mockMvc.perform(post("/api/rule-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"coChannelReuseDistanceKm": 0, "description": "v1"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/applications/NOPE/decide"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("Not Found"));
    }

    @Test
    void rejectsInvalidStationPayloadWith400() throws Exception {
        mockMvc.perform(post("/api/stations").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stationCode":"","name":"","latitude":0,"longitude":0,
                         "coverageRadiusKm":-1,"transmitPowerW":10,"deviceType":""}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }
}
