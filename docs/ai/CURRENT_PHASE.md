# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 5A 修复轮（审核后整改）。

## 当前状态

**Phase 5A 尚未验收 COMPLETE —— 审核整改已应用，等待复核。**

实现已完成（Story Import + Parsed Story Explorer UI）；审核发现的 M1 / M3 / m1 / m2 已在本轮修复，**M2 明确保留为下一阶段遗留**。

Phase 4 的**真实 DeepSeek 调用验证仍为 blocked / deferred**（无有效 API Key），该状态**未因本阶段改变**。

## 本阶段目标与结果

目标：把 Phase 4 产出的 `StoryContent` 真正展示到 App UI。

```
小说原文
  → Import（模式选择：Local Sample / DeepSeek）
  → StoryImportRepository
  → StoryContent
  → ViewModel
  → Compose UI
  → Story → Chapter → Scene → Beat → PerformanceEvent
```

| 项 | 结果 |
|---|---|
| 原文输入 | 多行文本输入 + 填充示例原文 + 开始解析 |
| 解析模式 | Local Sample / DeepSeek，复用 `AppConfig` 与既有 `StoryImportRepository`，**未新建解析入口** |
| Import 状态 | Idle / Importing / Success / Partial / Failure 五态 |
| Story Explorer | Story → Chapter → Scene → Beat → PerformanceEvent 全链路展示 |
| 六类事件 | 对白 / 旁白 / 动作 / 环境 / 环境音 / 镜头 |
| SourceSpan | 展示 snippet 与字符区间（只读展示，无编辑器） |
| 测试 | `./gradlew test --rerun` → **85 tests / 0 failures / 0 errors / 1 skipped** |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL** |

**未改动**：Domain 契约 / DTO / Validator / Mapper / DeepSeek remote client / PromptBuilder。

## DeepSeek 无 Key 的行为（本阶段确认）

选择 DeepSeek 且无有效 Key 时：`RemoteStoryImportRepository` 返回 `Failure(MISSING_API_KEY)`，UI 显示「解析失败 + 原因 + 说明」。

- 不伪造成功
- 不硬编码 Key
- 不改认证逻辑
- 不静默 fallback 到 Local Sample 后显示「DeepSeek 成功」

## 已完成（Implemented）

- 导入页：原文输入、示例填充、模式选择、导入按钮、五态结果卡片
- 解析结果页：Story / Chapter / Scene / Beat / Event 展示树
- Scene：title、**PresentationMode（显著展示）**、判定依据、setting、参与角色、节拍数
- Beat：order + 事件列表（按 `Timing.startOffsetMillis` 排序）
- 六类 PerformanceEvent 信息展示 + DialogueEvent 细节（说话者 / 情绪 / 语气风格 / 对谁 / 内心独白 / 语言 / 发声参数 / 音色覆盖）
- SourceSpan 与 timing 只读展示

## 修复轮（审核整改）

| 编号 | 问题 | 处理 |
|---|---|---|
| **M1** | `Importing` 可被输入编辑 / 模式切换重置 → 可触发并发导入 | **已修**：三个编辑入口改为 `idleUnlessImporting()`；`import()` 的 `Importing` 置位前提至 `launch` 之前（不再依赖 `Main.immediate`）。不新增并发框架 |
| **M3** | Explorer 的 LazyColumn key 未限定场景，存在 duplicate key 崩溃风险 | **已修**：仅改 Phase 5A 的 `StoryExplorerScreen`，key 限定到「场景（及节拍）」；`StoryPlayScreen` **未改** |
| **m1** | Scene 总时长未标 duration source | **已修**：总时长追加 `（Estimated / Audio / Manual / 来源未标注）`，与事件 timing 语义一致 |
| **m2** | Failure 丢失 `validation.errors` | **已修**：`ImportStatus.Failure` 新增 `errors`，UI 卡片展示校验错误摘要（Validator 规则未改） |

修复轮**未触碰**：Domain / DTO / Validator / Mapper / DeepSeek / PromptBuilder / Remote Repository / `StoryPlayScreen` / Navigation / 构建配置。

## 尚未实现（明确不在 Phase 5A 范围）

- Timeline 播放、TTS、音频播放、视频 / 画面生成、MP4 导出
- 「按用户原文新建章节」入口（当前统一归到内置第一章）
- DeepSeek 成功调用（无有效 Key，BLOCKED / DEFERRED）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult`（接口层依赖数据层类型）
4. 真实模型 offset 错误时 `INVALID_SOURCE_SPAN_RANGE` 是 ERROR，会导致整章导入失败（**未放宽**）
5. ~~远程模式输入原文仍是内置样例~~ —— Phase 5A 已提供用户原文输入入口
6. ~~导入成功 / 失败只在 logcat 可见~~ —— Phase 5A 已在 UI 呈现五态

Phase 5A 修复轮已解决：
7. ~~M1：`Importing` 可被输入编辑 / 模式切换重置~~ —— 已修（见「修复轮」）
8. ~~M3：Explorer LazyColumn key 未限定场景~~ —— 已修（仅 Phase 5A 的 Screen）
9. ~~m1：Scene 总时长未标 duration source~~ —— 已修
10. ~~m2：Failure 的 validation errors 丢失~~ —— 已修

Phase 5A 保留（下一阶段处理）：
11. **M2：用户导入的原文仍显示 `SampleStoryData` 的静态作品 / 章节元信息**（审核确认保留为下一阶段遗留）
12. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）；UI 展示内容由 ViewModel 展示模型与 UI key 生成函数的单测覆盖
13. `AIChatNovelApplication` 启动预载仍按 Phase 4 行为执行一次内置样例导入（本轮未改动）
14. `StoryPlayScreen`（Phase 2 已验收）存在与 M3 相同的全局 key 写法，本轮**按要求未修改**
15. `ImportViewModel` 以 suspend 函数类型注入导入实现（非接口），属设计取舍；`AppContainer` 保留两个 `importStory` 重载

## 下一步

**Phase 5A 未宣布 COMPLETE。不进入 Phase 5B。等待复核。**

待决策的开放问题（不要在未确认前动手）：
1. Phase 5A 是否验收 COMPLETE。
2. M2（作品 / 章节元信息归属）如何排期。
3. DeepSeek 真实验证：Key 的注入方式。
4. 是否补 Compose UI 测试源集（`androidTest`）。
5. 是否处理 Phase 4 遗留的第 3 条（`StoryImportResult` 层级瑕疵）。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- 已验收的 Phase 1–4 结构不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
