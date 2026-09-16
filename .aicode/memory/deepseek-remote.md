---
name: deepseek-remote
description: DeepSeek 远程接入层的边界与 Key 处理；架构规则见 docs/ai/ARCHITECTURE_RULES.md，当前状态见 docs/ai/CURRENT_PHASE.md
---
# DeepSeek 远程接入层（Phase 4）

> 架构规则见 `docs/ai/ARCHITECTURE_RULES.md`；当前进度与未完成项见 `docs/ai/CURRENT_PHASE.md`。

## 边界（不可破）
```
DeepSeek API → Wire DTO(DeepSeekChatResponse) → 提取 content JSON 文本 → ParseJson → ParseResponseDto
             → ParseValidator → AiParseMapper → StoryContent
```
- 网络层（`data/remote/deepseek/`）**永不构造** Story / Scene / Beat / PerformanceEvent。
- 校验 + 映射**只有一条路径**：`StoryParsePipeline`。`RemoteStoryImportRepository` 只做流程协调
  （调用第三阶段新增的 DTO 重载 `pipeline.parse(dto, novelText)`），不绕过 Validator / Mapper。
- 不允许为了让模型「容易成功」而放宽 Validator；模型 offset 不准时**记录问题**，不改校验。

## 文件
`data/remote/deepseek/`：`DeepSeekConfig`（toString 脱敏）、`DeepSeekWireDto`、
`DeepSeekApiClient` + `DeepSeekApiResult`（Success / MissingApiKey / HttpError / Timeout / NetworkError / MalformedEnvelope）、
`OkHttpDeepSeekApiClient`、`PromptBuilder`（输出契约）、`DeepSeekStoryParser`（**拒 Markdown**、要求 JSON 对象）、`DeepSeekLogger`。
`data/repository/RemoteStoryImportRepository`、`repository/StoryImportResult`（Success / Partial / Failure）、`repository/StoryImportRequest`。

## API Key（重要）
- **不写进源码 / BuildConfig / APK / 资源**。已用 `unzip -p ... classes.dex | strings | grep 'sk-'` 验证 APK 无 Key。
- 来源：环境变量 `DEEPSEEK_API_KEY` 或 JVM 系统属性 `deepseek.api.key`（`DeepSeekConfig.fromEnvironment()`）。
- 日志只出现 `****(len=NN)`，不出现 Key 的任何字符；有测试断言请求体与日志不含 Key。
- Android 客户端直连持有 Key 存在泄露风险 → **仅作开发验证**，生产应改为服务端代理。

## 真实调用测试（无 Key 时自动跳过，不影响 ./gradlew test）
```bash
DEEPSEEK_API_KEY=sk-xxx ./gradlew testDebugUnitTest --tests '*DeepSeekLiveIntegrationTest*' -i
```
报告写到 `app/build/deepseek-live-report.txt`（PresentationMode、SourceSpan 正确率、事件类型分布等）。

## 现状
`./gradlew test` → 59 用例 0 失败（1 skip = 真实调用）；`assembleDebug` 成功。
容器内**没有**可用 Key，**真实成功调用尚未完成**（只验证到请求到达服务端并返回 HTTP 401）。