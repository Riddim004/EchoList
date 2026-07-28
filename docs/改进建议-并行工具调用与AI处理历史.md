# 修改建议：并行工具调用 & AI 处理历史

> 来源：对开源项目 [LiteTask](https://github.com/cdz-hy/LiteTask) 的调研对比，经评估采纳其中两项改进。
> 原则：不改变现有 Agent 架构，仅做增量增强。
>
> **状态：两项均已于 v0.4.0 实施完成**。本文档初稿写于"草稿-确认模式"时期，以下内容已按实施时的实际代码现状（免确认直接落库 + 多轮上下文 + delete_task）修订。

---

## 一、并行工具调用（✅ 已实施）

### 1.1 原现状与问题

`TaskAgent.runLlmLoop()` 中对模型一次返回的多个 `tool_calls` 采用 for 循环**串行**执行：

- 文件：`app/src/main/java/com/msphone/agent/agent/TaskAgent.kt`
- 位置：`runLlmLoop()` 内 `for (call in toolCalls) { registry.dispatch(call) }`

当用户发出复合指令（如"查一下明天的任务，再把买菜标记完成"），模型可能同轮返回 `query_tasks` + `complete_task` 两个调用，串行执行使总耗时为各工具耗时之和。

### 1.2 实施方案

用 `coroutineScope + async` 并发执行所有 `tool_calls`，结果**按原顺序**回填消息列表（GLM API 要求每个 `tool_call_id` 都有对应的 role=tool 消息，顺序与 id 对应即可）：

```kotlin
// TaskAgent.kt — runLlmLoop() 内（已落地，注意字段已从 result.draft 改为 result.created）
val results = coroutineScope {
    toolCalls.map { call -> async { call to registry.dispatch(call) } }.awaitAll()
}
for ((call, result) in results) {
    if (result is ToolResult.Success && result.created != null) {
        created += result.created
    }
    messages += ChatMessage.tool(call.id, result.modelPayload)
}
```

### 1.3 注意事项（已按实施时现状修订）

- **异常隔离已具备**：`ToolRegistry.dispatch()` 内部已将所有异常转为 `ToolResult.Failure`，`async` 不会因单个工具失败取消兄弟协程，无需额外处理。
- **写-写并发**：与初稿假设不同，现在 `create_task`、`delete_task` 都**直接写库**。依赖 Room 事务串行化保证单写原子性；同轮内各工具操作不同 task_id，无交叉修改风险。若未来出现同轮内多工具写同一任务的场景，需重新评估顺序语义。
- **多任务创建**：同轮多个 `create_task` 的结果按原顺序收集进 `created` 列表，行为与串行版一致。

### 1.4 验收标准

- 单工具调用场景行为无任何变化（现有单测全绿）。
- 复合指令场景：两个工具的 `ToolRegistry` 日志时间戳重叠（并发执行），总耗时 ≈ max(各工具耗时) 而非 sum。

---

## 二、AI 处理历史表（✅ 已实施）

### 2.1 原现状与问题

每次 AI 解析的输入/输出仅有 Logcat 日志（`ToolRegistry` 的 `Log.d`），App 退出即丢失，无法：

- 回溯"某条任务是从哪句话解析出来的"；
- 统计解析成功率、离线降级触发率，用于评估与调优 System Prompt；
- 为后续用户习惯分析预留数据。

### 2.2 数据模型（已落地）

参考 LiteTask 的 `AIHistory` 表设计，结合本项目的离线降级通道扩展 `sourceType`。实施时按现有 Repository 分层落地：

- `data/local/AiHistoryDao.kt`：`AiHistoryEntity`（表 `ai_history`）+ `AiHistoryDao`（insert / observeRecent / trim 保留 500 条）
- `domain/model/AiHistoryRecord.kt`：领域模型 + `AiSourceType`（LLM / OFFLINE_FALLBACK）+ `AiResultType`（TASKS_CREATED / TEXT_REPLY / ERROR，初稿的 DRAFT_CREATED 随免确认落库改名）
- `domain/repository/AiHistoryRepository.kt` + `data/repository/AiHistoryRepositoryImpl.kt`：`record()` 内部 `runCatching` 吞异常，写入失败不影响主流程

### 2.3 接入点（已落地，注意与初稿的差异）

1. **AppDatabase**：`entities` 追加 `AiHistoryEntity::class`，版本 **v2 → v3**（初稿写的 1→2 已被聊天记录表占用）。用户手机上已有真实数据，**必须走正规 `Migration(2, 3)` 建表**，不能卸载重装。
2. **AppModule**：`provideAiHistoryDao` + `MIGRATION_2_3` + `BindsModule` 绑定 `AiHistoryRepository`。
3. **记录时机 —— 统一在 `TaskAgent.process()`**（签名已是 `process(userText, history)`）：在线/离线/错误三条路径汇聚于此，一处埋点全覆盖；另外防幻觉纠错路径内部也可能触发离线降级，故 `sourceType` 以草稿的 `fromOfflineFallback` 标记为准修正。

### 2.4 UI 展示（可选，二期）

首期只落库不做界面；后续可在设置页加"AI 解析历史"入口，列表展示 `rawInput → resultSummary`，用 `sourceType` 角标区分在线/离线通道。

### 2.5 验收标准

- 在线解析、离线降级、错误三种路径各触发一次后，`ai_history` 表分别出现 `sourceType/resultType` 正确的记录；
- 主动断库（如 DAO 抛异常）时，聊天主流程不受影响；
- 表记录数不超过 500 条上限。

---

## 三、实施记录

| 事项 | 状态 | 版本 |
|---|---|---|
| 并行工具调用 | ✅ 完成（TaskAgent.kt） | v0.4.0 |
| AI 历史：Entity + DAO + DB v2→v3 + DI | ✅ 完成 | v0.4.0 |
| AI 历史：TaskAgent 埋点 | ✅ 完成 | v0.4.0 |

## 四、明确不采纳项（备忘）

调研中 LiteTask 另有两项能力，经评估**暂不采纳**：

- **提醒独立成表**（一任务多提醒、相对提醒）—— 当前单提醒模型满足需求，避免过度设计；
- **全屏强提醒**（fullScreenIntent 锁屏弹窗）—— 涉及 `USE_FULL_SCREEN_INTENT` 权限与厂商 ROM 兼容性成本，后续视用户反馈再议。
