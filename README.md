# 语忆清单 EchoList

> 说一句话，任务和提醒就安排好了。
> Speak once — your task is captured, categorized, and scheduled.

**EchoList** 是一款 Android 原生的 AI 任务管理应用：用自然语言（如"明天下午三点开产品评审会"）描述待办，AI 自动完成**语义理解、时间提取、工作/生活分类**，创建任务并在指定时间**精确提醒**。

An AI-powered task manager for Android. Describe your to-dos in natural Chinese, and the app extracts the time, classifies the task (Work / Life), stores it, and fires an exact-time reminder — all through a tool-calling LLM agent.

## ✨ 功能 Features

- 💬 **对话式创建** — 聊天输入即建任务，免确认直接落库，卡片一键撤销
- 🧠 **AI 语义解析** — GLM-4-Flash（免费模型）提取时间、事件、分类；一句话多个任务自动拆分
- 🏷 **智能分类** — 自动归入「工作 / 生活」，支持改名、改分类、批量删除
- ⏰ **精确提醒** — AlarmManager 精确闹钟，Doze 可触发，重启后自动恢复
- 🗂 **任务清单** — 筛选计数、今日/明日角标、星期几显示、完成/删除/撤销
- 🔁 **多轮上下文** — 聊天记录持久化，"把刚才那个改到四点"这类指代能听懂
- 📡 **离线降级** — 断网时本地规则引擎解析常见中文时间表达，功能不中断
- 🛡 **防幻觉三层加固** — 工具成功返回才算数；模型"嘴上说做了"会被检测并强制纠正

## 🏗 架构 Architecture

采用 Agent 架构：LLM 负责理解，预定义工具负责执行，Harness 负责调度与校验。

```
用户输入 (自然语言)
   │
   ▼
TaskAgent (编排器)
   │  System Prompt (当前时间/规则) + 滑动窗口历史(20条)
   ▼
GLM-4-Flash ──── tool_calls ────► ToolCallingHarness (ToolRegistry)
   ▲                                  │ 参数校验 / 并行执行 / 失败回传自纠错
   │◄──────── role=tool 结果 ─────────┤
   │                                  ▼
   │                    ┌─────────────────────────────┐
 收尾确认               │ create_task   query_tasks   │
   │                    │ update_task   complete_task │
   ▼                    │ set_reminder  delete_task   │
聊天卡片 + 任务清单      └──────────┬──────────────────┘
                                   ▼
                        Room DB + AlarmManager 提醒
```

| 层 | 技术 |
| --- | --- |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Hilt DI + Kotlin Coroutines/Flow |
| Agent | 自研 Tool Calling Harness（注册制工具、JSON Schema 校验、防幻觉检测） |
| 模型 | 智谱 GLM-4-Flash（免费），Retrofit + kotlinx.serialization |
| 存储 | Room（任务 / 聊天记录 / AI 解析历史三张表） |
| 提醒 | AlarmManager 精确闹钟 + 前台通知 + 开机恢复对账 |

## 🚀 构建 Build

**环境要求**：JDK 17、Android SDK (compileSdk 34)、Gradle 8.9+（或使用 Android Studio 直接打开）

1. 克隆仓库后，在项目根目录创建 `local.properties`（参考 `local.properties.sample`）：

   ```properties
   sdk.dir=你的AndroidSDK路径
   # 智谱开放平台 API Key，https://open.bigmodel.cn 免费注册获取
   GLM_API_KEY=你的Key
   ```

2. 构建 Debug APK：

   ```bash
   gradle :app:assembleDebug
   ```

   产物：`app/build/outputs/apk/debug/语忆清单-v{版本号}-debug.apk`

> 🔑 `GLM_API_KEY` 留空也能运行——应用会自动切换到离线规则解析（支持常见中文时间表达，但无法处理复杂语义）。
> `local.properties` 已被 `.gitignore` 排除，密钥不会进入版本库。

## 📱 使用 Usage

| 你说 | 它做 |
| --- | --- |
| "明天下午三点开产品评审会" | 创建工作任务，明天 15:00 提醒 |
| "周六记得买牛奶，再提醒我给妈妈打电话" | 拆成两个生活任务，分别处理 |
| "把刚才那个会议改到四点" | 从上下文定位任务，更新提醒时间 |
| "今天的任务做完了，全部删掉" | 查询后批量删除 |

## 📄 License

个人学习项目，暂未附加开源许可证。All rights reserved.
