---
name: architecture
description: AIChatNovel 架构红线与文档入口；领域契约详见 docs/ai/DOMAIN_CONTRACT.md，架构规则详见 docs/ai/ARCHITECTURE_RULES.md
---
# AIChatNovel 架构红线与文档入口

> 这是**索引**。完整领域契约见 `docs/ai/DOMAIN_CONTRACT.md`，
> 完整架构规则见 `docs/ai/ARCHITECTURE_RULES.md`，项目基线见 `docs/ai/PROJECT_BASELINE.md`。

## 最重要的语义约束（红线）
本 App **不是**「微信群聊 App」。群聊式 UI 只是多角色轮流说话的**视觉表现形式**。
只有当小说剧情本身明确发生在微信/QQ/短信等 IM 中时，才把剧情表现为线上聊天消息。
领域模型一律**不得**以 ChatMessage / GroupChat 为核心概念。
`PresentationMode` 默认 `LiveScene`；非 `LiveScene` 必须有 `presentationEvidence`。

## 分层（顶层包）
- `domain/model/`：纯 Kotlin 数据模型（层级 `Story → Chapter → Scene → Beat → PerformanceEvent`）
- `repository/`：**只放接口**（StoryRepository / CharacterRepository / PerformanceRepository / SettingsRepository / StoryImportRepository）
- `data/repository/`：实现（InMemory* + LocalSample / Remote StoryImportRepository + `StoryContentStore`）
- `data/parser/`：解析管线（`dto/` `validation/` `mapping/` `sample/` + `StoryParsePipeline` / `ParseSchema`）
- `data/remote/deepseek/`：网络层（**不接触 domain**）
- `viewmodel/`：`StateFlow<XxxUiState>` + `stateIn(WhileSubscribed(5000))`
- `ui/<feature>/`：`XxxRoute`（取 VM）+ `XxxScreen`（无状态）
- `navigation/`、`di/AppContainer`（手工 DI，**未用 Hilt**，靠 `viewModelFactory { initializer { ... } }`）

## 数据来源
一次导入（本地样例或远程 DeepSeek）产出 `StoryContent` → 写入 `StoryContentStore` →
InMemory* Repository 从 store 派生 Flow → UI 自动刷新。
模式由 `AppConfig.storyImportMode` 决定：`LOCAL_SAMPLE`（默认）/ `REMOTE_DEEPSEEK`。
覆盖方式：`local.properties` 写 `story.import.mode=remote`，或 `-Pstory.import.mode=remote`。

## 阶段边界
Phase 1–4 已完成（Phase 4 真实 API 验证待做，见 `docs/ai/CURRENT_PHASE.md`）。
仍未接入：Room、DataStore、TTS、音频播放、视频生成、MP4 导出、登录/账号、云端数据库、图片生成、Timeline 播放器。
**改动时不要自行扩大需求或更换上述架构。**