package com.chris64233.spectrumcoordination.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SpectrumApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult postJson(String url, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private long createStation(String name, double lonDeg) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("latitudeDeg", 0.0);
        body.put("longitudeDeg", lonDeg);
        body.put("radiusMeters", 1000.0);
        body.put("powerWatts", 10.0);
        body.put("deviceType", "FIXED");
        MvcResult result = postJson("/api/stations", body);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createResource(String name, long low, long high, int maxUsers) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("bandLowHz", low);
        body.put("bandHighHz", high);
        body.put("regionLatitudeDeg", 0.0);
        body.put("regionLongitudeDeg", 0.0);
        body.put("regionRadiusMeters", 50000.0);
        body.put("maxCoChannelUsers", maxUsers);
        MvcResult result = postJson("/api/frequency-resources", body);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Map<String, Object> applicationBody(String no, long stationId, long resourceId,
                                                double lonDeg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("applicationNo", no);
        body.put("stationId", stationId);
        body.put("resourceId", resourceId);
        body.put("bandLowHz", 100_000_000);
        body.put("bandHighHz", 150_000_000);
        body.put("usageLatitudeDeg", 0.0);
        body.put("usageLongitudeDeg", lonDeg);
        body.put("usageRadiusMeters", 1000.0);
        body.put("startTime", Instant.parse("2026-12-01T00:00:00Z").toString());
        body.put("endTime", Instant.parse("2026-12-01T04:00:00Z").toString());
        return body;
    }

    @Test
    void fullWorkflowSubmitApproveQueryReassignPauseTerminate() throws Exception {
        long stationA = createStation("API-A", 0.0);
        long stationB = createStation("API-B", 0.027); // ≈ 3000m 外
        long r1 = createResource("API-R1", 100_000_000, 200_000_000, 1);
        long r2 = createResource("API-R2", 300_000_000, 400_000_000, 1);

        // 提交并批准 A
        postJson("/api/applications", applicationBody("API-APP-A", stationA, r1, 0.0));
        mockMvc.perform(post("/api/applications/API-APP-A/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(true))
                .andExpect(jsonPath("$.licenseNo").value(org.hamcrest.Matchers.startsWith("LIC-")));

        // 幂等提交：同申请号返回同一申请（200，由 POST 返回实体）
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                applicationBody("API-APP-A", stationA, r1, 0.0))))
                .andExpect(jsonPath("$.applicationNo").value("API-APP-A"));

        // B 冲突被驳回
        postJson("/api/applications", applicationBody("API-APP-B", stationB, r1, 0.027));
        mockMvc.perform(post("/api/applications/API-APP-B/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved").value(false))
                .andExpect(jsonPath("$.conflicts[0].licenseNo").exists());

        // 冲突对象可查询
        mockMvc.perform(get("/api/applications/API-APP-B/conflicts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conflictLicenseId").exists());

        // 许可查询 + 规则版本查询
        mockMvc.perform(get("/api/licenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/rule-versions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v1"));

        // 改频到 r2 成功
        String licenseNo = objectMapper.readTree(
                mockMvc.perform(get("/api/licenses")).andReturn().getResponse().getContentAsString()
        ).get(0).get("licenseNo").asText();

        Map<String, Object> reassign = new LinkedHashMap<>();
        reassign.put("resourceId", r2);
        reassign.put("bandLowHz", 300_000_000);
        reassign.put("bandHighHz", 350_000_000);
        mockMvc.perform(post("/api/licenses/" + licenseNo + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reassign)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 变更记录查询
        mockMvc.perform(get("/api/licenses/" + licenseNo + "/changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].changeType").value("REASSIGNED"));

        // 未开始许可不能暂停
        mockMvc.perform(post("/api/licenses/" + licenseNo + "/pause"))
                .andExpect(status().isUnprocessableEntity());

        // 提前终止
        mockMvc.perform(post("/api/licenses/" + licenseNo + "/terminate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"));

        // 历史仍可查询
        mockMvc.perform(get("/api/licenses/" + licenseNo + "/changes"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void invalidRequestReturns400() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "");
        body.put("latitudeDeg", 0.0);
        // 缺少必填字段
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownResourceReturns404() throws Exception {
        mockMvc.perform(get("/api/frequency-resources/999999"))
                .andExpect(status().isNotFound());
    }
}
