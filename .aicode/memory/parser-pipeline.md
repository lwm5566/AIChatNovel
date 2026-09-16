---
name: parser-pipeline
description: 本地解析管线的关键规则与踩坑（DTO/校验/映射/稳定 id）；完整契约见 docs/ai/DOMAIN_CONTRACT.md 第 8 节
---
# 解析管线（Phase 3 建立，关键规则与踩坑）

> **完整解析契约见 `docs/ai/DOMAIN_CONTRACT.md` 第 8 节**；不可放宽的规则见 `docs/ai/ARCHITECTURE_RULES.md`。
> 这里只留「容易踩错、必须记住」的操作要点。

## 数据流
`原文 + AI 响应 JSON → ParseResponseDto → ParseValidator → AiParseMapper → StoryContent`
（目录 `data/parser/{dto,validation,mapping,sample}` + `StoryParsePipeline` / `ParseSchema` / `ParseJson`）

## 容易踩错的点
- 事件 DTO 用 `JsonContentPolymorphicSerializer` 按 JSON 的 `type` 字段选子类
  （`dialogue`/`narration`/`action`/`environment`/`sound`/`camera`），未知 type → `UnknownEventDto` → 校验报 ERROR。
  因此 `Json` **必须** `ignoreUnknownKeys = true`（`type` 不是 DTO 属性）。
- **呈现介质安全规则**：`presentationMode` 缺省 → `LiveScene`；非 `LiveScene` 但 `presentationEvidence` 为空 →
  校验层 WARNING(`MISSING_PRESENTATION_EVIDENCE`)，**Mapper 自己也会再降级**为 `LiveScene` + WARNING(`DEGRADED_PRESENTATION_MODE`)。
  **绝不静默接受**，也**不要**为了成功率高而去掉这段逻辑。
- 有 ERROR 时管线不映射（`content = null`）；只有 WARNING 才继续映射并返回 `Partial`。
- **稳定 id**：角色 = `"char-" + 名字小写 slug`（同名跨章节自动去重）；场景 = `"<chapterId>-<id|tempId>"`。
  踩过的坑：样例里两份场景 tempId 都是 `s1`，不按章节限定会让 `beatsByScene` 互相覆盖。
- AI 不产音色 → `voiceOverride = null`，运行时经 `effectiveVoiceProfile` 继承 `Character.voiceProfile`；
  AI 不产最终时长 → `duration` 可空，给了 duration 未给来源时默认 `DurationSource.Estimated`。
- `SourceSpan`：校验层传入 `chapterText` 时会检查越界(ERROR)与 snippet 逐字一致性(WARNING)。

## 样例资源
`app/src/main/resources/parser/`：`sample_live_scene.json`(chapter-1, LiveScene, 6 种事件、并发 Beat)、
`sample_instant_messaging.json`(chapter-2, InstantMessaging + evidence, 4 条聊天) + 两份 `chapter_*.txt` 原文。
AGP 会把 `src/main/resources` 打进 APK，运行时用 classloader 读取。