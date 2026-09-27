# cc-spectrum-coordination

无线电台站在地理干扰约束下申请频率使用许可的协调服务。

基于 Spring Boot 4.1 / Java 21 / Spring Data JPA / H2，提供台站与频率资源管理、
频率许可申请、干扰冲突裁决、许可改频/暂停/终止以及全程变更审计。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `Station` 台站 | 位置（经纬度）、覆盖半径、发射功率、设备类型 |
| `FrequencyResource` 频率资源 | 可申请频段（MHz，左闭右开）、适用区域圆、允许的最大同频使用数 `maxCoChannelUsers` |
| `InterferenceRuleVersion` 干扰规则版本 | 版本号、同频复用保护距离（km）、生效时间、ACTIVE/SUPERSEDED 状态 |
| `FrequencyApplication` 申请 | 申请号（幂等键）、台站、所需频段、使用区域、时间范围、提交/裁决规则版本、状态 |
| `License` 许可 | 整段频段 × 整段时间窗、使用区域、状态、来源申请、规则版本号；含 JPA 乐观锁 |
| `FrequencyOccupancy` 频率占用 | 冲突判定的唯一数据源：频段 × 时间窗 × 地理圆，每条许可对应一条 |
| `ChangeRecord` 变更记录 | 批准/拒绝/撤销/改频/暂停/恢复/终止/规则重校验的只追加审计历史 |

## 主要业务规则

### 1. 干扰冲突判定（时间 × 频率 × 地理三重交叠）

裁决一个候选占用与现有占用是否冲突，必须**同时**满足：

1. **频段重叠**：左闭右开区间相交，`s1 < e2 && s2 < e1`；首尾相接（如 100–110 与 110–120）不冲突。
2. **时间重叠**：时间窗同为半开区间 `[start, end)`，首尾相接不冲突，相差 1 秒即冲突。
3. **干扰区域重叠**：两个使用圆（圆心经纬度 + 半径，距离采用 Haversine 大圆距离）满足
   `圆心距 ≤ 半径A + 半径B + 同频复用保护距离`。

容量约束：与候选构成干扰的现有占用数量加上候选自身，不得超过频率资源的
`maxCoChannelUsers`；为 1 时表示同一干扰区域内同频完全排他。

### 2. 申请内容

申请包含使用区域（圆心 + 半径，半径不填时回退为台站覆盖半径）、时间范围
（半开区间）和所需频段；申请频段必须完整落在某个频率资源的频段内，否则提交即被拒绝。

### 3. 整段原子批准，不允许部分批准

裁决（`POST /api/applications/{no}/decide`）在单一数据库事务内完成：
冲突检查 → 生成许可 → 写入“整段频段 × 整段时间”占用 → 回写申请 → 追加变更记录。
若申请频段只有一部分可用（例如申请 105–115，而 100–110 已占用），**整体拒绝**，
不会生成部分许可或部分占用。

### 4. 申请号幂等与并发安全

- 申请号全局唯一（数据库唯一索引）。同一申请号重复提交直接返回既有申请
  （响应中 `idempotentReplay=true`）；对已裁决申请重复裁决返回原结果。
  并发重复提交由“先查 + 唯一索引兜底 + 冲突后重读”保证只落一条记录。
- 裁决与改频事务先对目标频率资源行加**悲观写锁**（`PESSIMISTIC_WRITE`），
  同一资源上的并发裁决全局串行化。因此并发申请相邻区域的同一频段时，
  至多 `maxCoChannelUsers` 个获批，其余拒绝，最终状态绝不违反干扰规则
  （见 `SpectrumConcurrencyTest`：8 并发、容量 1，结果恰为 1 批 7 拒）。

### 5. 规则版本升级后待批准申请重新校验

发布新规则版本（`POST /api/rule-versions`）会把旧版本置为 SUPERSEDED，
**已有许可不追溯**；但处于 PENDING 的申请在裁决时若发现当前版本与提交时不同，
一律按当前版本重新校验，并追加 `RULE_REVALIDATED` 变更记录：
新版本下仍无冲突则按新版本批准，否则拒绝（响应中含 `submittedRuleVersion`、
`decisionRuleVersion` 与 `revalidated` 标志）。

### 6. 许可生命周期

- **未开始（NOT_STARTED）**：使用时间窗起点之前。允许**整体改频**
  （`POST /api/licenses/{no}/retune`）。改频在单事务内完成冲突校验，
  只有新频率能**完整批准**时才释放旧频率、写入新占用；任一步失败事务回滚，
  原许可与原占用保持有效。改频后旧频段立即可被其他申请使用。
- **已开始（ACTIVE）**：时间窗开始后，不能改频，只能**暂停**
  （占用保留以保障恢复）或**提前终止**。
- **暂停（SUSPENDED）**：可恢复为 ACTIVE，也可提前终止。
- **提前终止（TERMINATED）**：占用与许可时间窗截断到终止时刻，终止时刻之后
  该频段可重新申请；原始批准时间窗（`originalStartTime/originalEndTime`）与
  全部变更记录永久保留。
- 到期为查询时按当前时间折算的有效状态（`effectiveStatus`），存储状态不变。

### 7. 查询能力

- 许可列表/详情（含存储状态与按当前时间折算的有效状态）
- 冲突对象查询：申请维度预演 `GET /api/applications/{no}/conflicts`；
  许可维度 `GET /api/licenses/{no}/conflicts`（排除自身占用）
- 规则版本列表 `GET /api/rule-versions`
- 变更记录：按许可、按申请、最近记录列表

## HTTP 接口一览

基础数据：

- `POST /api/stations`、`GET /api/stations`、`GET /api/stations/{stationCode}`
- `POST /api/resources`、`GET /api/resources`
- `POST /api/rule-versions`、`GET /api/rule-versions`

申请：

- `POST /api/applications` 提交（申请号幂等）
- `GET /api/applications`、`GET /api/applications/{applicationNo}`
- `POST /api/applications/{applicationNo}/decide` 裁决（原子批准/拒绝）
- `POST /api/applications/{applicationNo}/cancel` 撤销待批准申请
- `GET /api/applications/{applicationNo}/conflicts` 冲突对象预演
- `GET /api/applications/{applicationNo}/changes` 申请变更记录

许可：

- `GET /api/licenses`、`GET /api/licenses/{licenseNo}`
- `POST /api/licenses/{licenseNo}/retune` 未开始许可整体改频
- `POST /api/licenses/{licenseNo}/suspend` / `resume` / `terminate`
- `GET /api/licenses/{licenseNo}/conflicts` 冲突对象
- `GET /api/licenses/{licenseNo}/changes` 许可变更记录
- `GET /api/licenses/changes/recent?limit=50` 最近变更

错误响应统一为 `{ code, message, errors, time }`，状态码使用 400/404/409/422。

### 典型请求体

提交申请：

```json
{
  "applicationNo": "APP-2026-001",
  "stationCode": "STA-A",
  "resourceId": 1,
  "bandStartMhz": 100.0,
  "bandEndMhz": 110.0,
  "useLatitude": 30.0,
  "useLongitude": 120.0,
  "useRadiusKm": 10.0,
  "startTime": "2026-10-01T00:00:00Z",
  "endTime": "2026-10-01T12:00:00Z"
}
```

改频：

```json
{ "resourceId": 1, "bandStartMhz": 130.0, "bandEndMhz": 140.0 }
```

## 自动化测试

`./mvnw clean test` 共 27 个用例：

- `InterferenceEngineTest`：频段/时间半开区间、圆相切、保护距离扩大干扰范围、
  同频容量、多用户复用等纯算法用例。
- `SpectrumServiceIntegrationTest`：三重重叠裁决、部分重叠整体拒绝、
  申请号幂等、规则升级重校验（批准与拒绝两种结果）、改频成功释放旧频、
  改频失败原许可保持有效、已开始禁止改频、暂停占用保留、提前终止释放未来时间、
  历史记录完整性、非法状态流转、越界频段校验。
- `SpectrumConcurrencyTest`：8 线程并发裁决相邻区域同频段（恰好 1 批 7 拒）、
  10 线程相同申请号并发提交（仅落 1 条且全部成功响应）。
- `SpectrumApiTest`：MockMvc 端到端全链路、幂等重放、冲突响应、404/400 错误体。

测试使用可控时钟（`MutableClock`）驱动未开始/进行中/终止等时间相关行为，
每个用例前通过 `DatabaseCleaner` 清空并重置业务表。
