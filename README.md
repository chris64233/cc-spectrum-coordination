# cc-spectrum-coordination

无线电台站在地理干扰约束下申请频率使用许可的协调服务：受理频率许可申请，按
**时间 × 频率 × 干扰区域** 三重条件校验，整段频段原子批准，并支持未开始许可整体改频、
已开始许可暂停/提前终止，规则版本演进后对存量待批申请重新校验。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1
- Spring Data JPA + H2（可替换为其他关系型数据库）

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Station` 台站 | 位置（经纬度）、覆盖半径（米）、发射功率（瓦）、设备类型（FIXED/MOBILE/PORTABLE/BASE_STATION） |
| `FrequencyResource` 频率资源 | 频段 `[bandLowHz, bandHighHz)`、所属地理区域（圆心+半径）、允许的最大同频使用数 `maxCoChannelUsers` |
| `RuleVersion` 规则版本 | 干扰余量参数；任意时刻最多一个 `active` 版本。服务启动自动生成默认版本 `v1`（固定余量 1000 米，功率系数 10 米/瓦） |
| `FrequencyApplication` 申请 | 申请号、台站、频率资源、**整段**申请频段、使用区域、起止时间、提交时依据的规则版本 |
| `License` 许可 | 批准后生成：NOT_STARTED → ACTIVE → PAUSED / TERMINATED，行永久保留 |
| `FrequencyOccupancy` 频率占用 | 干扰校验的事实来源，许可与占用一一对应；暂停/终止删除占用、保留许可 |
| `ApplicationConflict` 冲突快照 | 申请最近一次校验命中的冲突许可、台站、规则版本、重叠量 |
| `LicenseChangeRecord` 变更记录 | 发证、生效、改频成功/失败、暂停、终止的完整历史（仅追加） |
| `CoordinationLock` 协调锁 | 单行悲观写锁，串行化所有改变占用的判定 |

## 主要业务规则

### 1. 干扰判定：时间 × 频率 × 区域三重重叠

候选申请只有在以下三个条件**同时**成立时，才算与一个现有占用同频干扰：

1. **时间重叠**：`[startTime, endTime)` 与占用时间窗相交（端点相接不算）；
2. **频率重叠**：申请频段 `[low, high)` 与占用频段相交；
3. **干扰区域重叠**：双方在覆盖半径之外各获得一段规则版本定义的同频干扰余量：

   ```
   扩展半径 = 使用区域半径 + 固定余量(marginMeters) + 功率系数(distanceFactor) × 发射功率
   干扰区域重叠 ⇔ 圆心距 < 扩展半径A + 扩展半径B
   ```

   地理距离采用 WGS-84 经纬度的 Haversine 大圆距离（米）。

命中的占用总数加候选自身超过频率资源的 `maxCoChannelUsers`（1 即同频排他）时，
申请被**驳回**，冲突对象落库可查；否则批准。

### 2. 整段频段原子批准（全有或全无）

批准是一个数据库事务：获取协调锁 → 读取现有占用 → 三重判定 →
**一次性写入整段申请频段的占用**并生成许可。即使申请频段只有一小段与现有占用冲突，
也是整段驳回，绝不只批准无冲突的一部分；事务中途失败则整体回滚。

### 3. 申请号幂等

`applicationNo` 有数据库唯一约束。相同申请号重复提交直接返回首次申请，不重复创建。
并发提交同一申请号时，"先查后插 + 唯一约束兜底"保证最终恰好一条申请。

### 4. 并发安全：相邻区域同频段

所有批准/改频/暂停/终止在事务内先获取**全局干扰协调锁**（单行 `SELECT … FOR UPDATE`），
使"读占用 → 判定 → 写占用"成为串行临界区。因此并发申请相邻区域的同一频段时，
最多有符合 `maxCoChannelUsers` 数量的申请成功，最终占用集合不违反任何干扰规则
（`SpectrumCoordinationServiceTest.concurrentAdjacentApplicationsNeverViolateInterferenceRule`
多轮并发验证该不变式）。

### 5. 规则版本变更 → 待批申请重新校验

申请记录提交时的规则版本；**批准时一律按当前生效版本重新校验**（而不是沿用旧版本）。
新版本可扩大或缩小干扰余量；激活新版本在同一事务内停用旧版本。基于旧版本提交、
尚未批准的申请，因此自动按新规则重新判定，冲突快照也按新版本重建。

### 6. 未开始许可整体改频

仅 `NOT_STARTED` 许可允许改频（指定新频率资源与完整新频段）。同一事务内：

- 新频段通过完整干扰校验后，才写入新占用、更新许可、删除旧占用；
- 新频段校验失败时**不触碰旧占用**，原许可保持有效，并记录 `REASSIGN_FAILED` 历史。

即"只有新频率完整批准后才释放旧频率；失败时原许可保持有效"。

### 7. 已开始许可只能暂停或提前终止

- `ACTIVE` 许可可暂停：占用立即释放，许可保留为 `PAUSED`（并记录历史）；
- 未开始/已开始/已暂停许可均可提前终止：占用释放，许可保留为 `TERMINATED`；
- 许可行与全部 `LicenseChangeRecord` 永久保留作为历史；
- 到达开始时间的未开始许可由定时任务自动置为 `ACTIVE`。

## REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/stations` | 登记台站 |
| GET | `/api/stations`、`/api/stations/{id}` | 台站查询 |
| POST | `/api/frequency-resources` | 登记频率资源（频段、区域、最大同频使用数） |
| GET | `/api/frequency-resources`、`/api/frequency-resources/{id}` | 频率资源查询 |
| GET | `/api/rule-versions`、`/api/rule-versions/current` | 规则版本查询 |
| POST | `/api/rule-versions` | 创建规则版本（不自动激活） |
| POST | `/api/rule-versions/{id}/activate` | 激活新版本（旧版本同事务停用） |
| POST | `/api/applications` | 提交申请（申请号幂等） |
| POST | `/api/applications/{applicationNo}/approve` | 按当前规则重新校验并整段批准/驳回 |
| GET | `/api/applications/{applicationNo}` | 申请查询 |
| GET | `/api/applications/{applicationNo}/conflicts` | **冲突对象查询**（许可、台站、版本、重叠量） |
| GET | `/api/licenses?stationId=&status=` | **许可查询**（可按台站/状态过滤） |
| GET | `/api/licenses/{licenseNo}` | 许可详情 |
| POST | `/api/licenses/{licenseNo}/reassign` | 未开始许可整体改频 |
| POST | `/api/licenses/{licenseNo}/pause` | 已开始许可暂停 |
| POST | `/api/licenses/{licenseNo}/terminate` | 提前终止 |
| GET | `/api/licenses/{licenseNo}/changes` | **变更记录查询**（完整历史） |

批准返回体示例：

```json
{
  "approved": false,
  "applicationNo": "APP-B",
  "licenseNo": null,
  "ruleVersion": "v2-strict",
  "conflicts": [
    { "licenseId": 1, "licenseNo": "LIC-1", "stationId": 2,
      "ruleVersion": "v2-strict", "overlapMeters": 1234.5 }
  ]
}
```

时间字段使用 ISO-8601 UTC（如 `2026-12-01T00:00:00Z`）。业务规则违反返回 422，
资源不存在返回 404，参数校验失败返回 400。

## 自动化测试

`./mvnw test` 共 27 个测试，覆盖：

- 地理距离/覆盖圆相交计算（`GeoCalculatorTest`）；
- 时间、频率、区域任一维度不重叠即放行，三者同时重叠且超出 `maxCoChannelUsers` 才驳回
  （`InterferenceEvaluatorTest`）；
- 整段占用原子写入、部分频段重叠整段驳回、申请号幂等（含并发同号提交）；
- 6 个相邻区域台站 × 5 轮并发批准，断言最终占用两两不构成干扰、批准数符合排他上限；
- 规则版本升级后旧版本待批申请按新版本重新校验；
- 未开始许可改频成功（旧频随后释放）、改频失败（原许可与占用保持有效）；
- 已开始许可禁止改频、暂停/终止后占用释放且历史完整；
- REST 全流程（提交→批准→冲突查询→改频→终止→历史）与 400/404 错误映射。
