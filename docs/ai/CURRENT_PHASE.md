# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 5B-3 实现完成后（本阶段改动已提交到本地 `master`，尚未 push）。

## 当前状态

**Phase 5B-3 implementation complete —— 导入后数据链路已复核并加固；等待 Review Gate。**

- **Phase 5A**：**已 COMPLETE / CLOSED**（commit `f30208b`，已 push）
- **Phase 5B-1**：**已 COMPLETE / CLOSED**（commit `9d59177`，已 push）
- **Phase 5B-2**：**已 COMPLETE / CLOSED**（commit `a047904`，已 push）
- **Phase 5B-3**：**实现完成**（导入后数据链路与架构稳固）；改动已提交到本地 `master`，**尚未 push**
- Phase 4 真实 DeepSeek 验证仍为 **blocked / deferred**（无有效 API Key），未因本阶段改变

## 本阶段（Phase 5B-3）目标与结果

目标：在进入后续剧情加工阶段之前，确认「一次导入 → 当前 Story 快照 → Story / Chapter / Scene / Beat / PerformanceEvent」这条链路足够稳定、职责清晰、数据可安全继续流转。

**复核结论：链路未发现断裂或违规。**

| 审查项 | 结论 |
|---|---|
| Story → Chapter → Scene → Beat → PerformanceEvent 归属 | 连通，无断裂 |
| Phase 5B-1 ownership 规则 | 未被绕过；Remote 仍以 `request.storyId` / `request.chapterId` 为唯一 authority |
| Phase 5B-2 metadata | 仍只是展示字段；不参与 id 生成 / lookup / validation / mapper |
| DTO → Validator → Mapper → Domain | 仍只有 `StoryParsePipeline` 一条路径；无 AI 原始字段直入 Domain |
| `StoryContentStore` 一致性 | **已修**（见下） |
| `repository` → `data.parser.validation` 依赖 | **判断为暂不修改**（见「遗留问题」） |

### 本阶段实际修改：`StoryContentStore` 改为单一真源

原先 `replace()` 顺序写两个 `MutableStateFlow`（先 `importedState` 再 `contentState`），
存在一个「快照已换新、内容仍是上一次」的中间窗口：只要订阅者在该窗口内读取，就会看到新 Story / Chapter 配旧 content。

- 现在只有 `importedState` 一个发布源，`content` 是它的**派生视图**：
  `val content: Flow<StoryContent> = importedState.map { it?.content ?: EMPTY_CONTENT }`
- 「快照与内容来自同一次导入」由**结构**保证，不再依赖调用顺序
- 对外行为不变：3 个 InMemory Repository 都是 `store.content.map { … }`，**零改动**；未导入时仍得到空 `StoryContent()`
- 附带收益：`observeStories` / `observeChapters`（读 `imported`）与 `observeScenes` / `observeBeats` / `observeCharacters`（读 `content`）现在来自同一条源，跨 Repository 的 `combine` 不再可能跨快照

| 项 | 结果 |
|---|---|
| 测试 | `./gradlew test --rerun` → **113 tests / 0 failures / 0 errors / 1 skipped** |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL**（无 Kotlin 编译警告） |

**未改动**：Domain 模型 / DTO / Validator / Mapper / parser / remote client / PromptBuilder / `ParseSchema` / `AppContainer` / 三个 InMemory Repository / ViewModel / UI / Navigation / Gradle。

### snapshot 与内容一致性（硬规则，本阶段新增）

- `StoryContentStore` 只有 `imported` 一个发布源；`content` 必须是它的派生视图，**不得**再引入第二条发布通道。
- 新代码若需要「当前快照的内容」，从 `imported` 派生，不要另建 `StateFlow`。
- `StoryContentStoreTest` 以「观察到 `imported` 变化时立即读 `content`」锁定该不变量；已验证**改回双通道实现即失败**。

### metadata 与 ownership 的关系（硬规则，Phase 5B-2 起生效）

- metadata **只决定展示字段**，**不参与** Story / Chapter ID 的生成、选择、查找或合并。
- metadata **不触碰** `SourceSpan.chapterId`、`Scene.id`、`beatsByScene` 的 key。
- Remote 分支继续以 `request.storyId` / `request.chapterId` 为唯一 authoritative 归属。
- 回退规则：`storyTitle` / `author` → `未命名作品` / `未命名作者`；`synopsis` → 空串；`chapterTitle` → `未命名章节`。**从不**把空白字符串写进 Domain。
- metadata **不改变** Success / Partial / Failure / Importing 的任何行为。
- 本地样例（Local Sample）路径的元信息**仍是占位值** —— 它是 fixture，不读 request（Phase 5B-2 裁决 α-2，不是缺陷）。

## 已完成（Phase 5A，已 CLOSED）

- 导入页：原文输入、示例填充、模式选择（Local Sample / DeepSeek）、五态结果卡片
- 解析结果页：Story → Chapter → Scene → Beat → PerformanceEvent 展示树
- DeepSeek 无 Key 时明确失败（`MISSING_API_KEY`），不伪造成功、不静默回退

## Failure / Partial / Importing 语义（未变）

| 结果 | 行为 |
|---|---|
| `Success` | 写入当前导入快照（story + chapters + content） |
| `Partial` | **同样写入**（视为可应用结果） |
| `Failure` | **不写入**；首次失败 → 仍无当前 Story；后续失败 → 保留上一次成功的快照 |
| `Importing` | 旧快照继续可见，不提前清空 |

## 尚未实现（明确不在本阶段范围）

- 导入后编辑 metadata、metadata 编辑页、metadata 历史版本
- 多作品 / 历史管理（Store 只有「当前导入」一个槽位）
- AI 自动生成 metadata（Phase 5B-2 明确采用用户输入方案）
- Timeline 播放、TTS、音频、视频、MP4
- DeepSeek 真实成功调用（无有效 Key）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult` —— **Phase 5B-3 复核后判断暂不修改**，理由见下
4. 真实模型 offset 错误时 `INVALID_SOURCE_SPAN_RANGE` 是 ERROR，会导致整章导入失败（**未放宽**）

Phase 5A 遗留：
5. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）
6. `AIChatNovelApplication` 启动预载保留（走同一套「当前导入」写入路径）
7. `StoryPlayScreen`（Phase 2 已验收）的全局 LazyColumn key 写法未改

Phase 5B-1 遗留（非 blocker，审计结论：保持不动）：
8. Remote `Scene.id` 保留 Mapper 生成的前缀（可能与归一化后的 `chapterId` 前缀不同；功能无影响）
9. Remote 多章收敛为单章（符合「一次导入 = 一章原文」语义）
10. `LOCAL_SAMPLE` 也消耗一对导入 ID（样例忽略之，无害）
11. 仅保留单个当前快照、没有历史（本阶段要求）

Phase 5B-3 判断（**明确不修改**，已记录为架构债务）：
12. `repository/StoryImportResult` → `data.parser.validation.ValidationResult`
    - **不是实现耦合**：`ValidationResult` 是纯数据，不引用 `ParseValidator` / `ApiClient` / Prompt 等任何解析实现；`repository` 契约没有泄漏解析实现。
    - **移出去反而错位**：`ValidationCode` 的词汇表（`UNPARSEABLE_RESPONSE` / `UNSUPPORTED_SCHEMA_VERSION` / `DUPLICATE_*_TEMP_ID` / `SNIPPET_MISMATCH` / `MISSING_PRESENTATION_EVIDENCE` …）本质是**解析契约**的概念，归属 `data.parser.validation` 是正确的。搬进 `repository` 会让接口层拥有「AI 响应解析」的词汇。
    - **真正的解耦是设计变更**：正确做法是由 `repository` 定义面向消费者的中性「导入问题」，data 层负责映射 —— 需要新契约 + 映射层，属于设计变更，应由真实需求（第二个解析来源或第二个消费者）驱动，不做预见性设计。
    - 现状无行为风险、无扩展阻碍；当前只有 8 个引用点，未来若确需解耦，成本仍然可控。

已解决：
13. ~~M2：任意导入显示 `SampleStoryData` 静态元信息~~ —— Phase 5B-1 解决
14. ~~Story / Chapter 元信息为硬编码占位值（Remote 路径）~~ —— Phase 5B-2 解决（本地样例按裁决保留占位）
15. ~~`StoryContentStore` 双 `StateFlow` 顺序发布导致的中间窗口~~ —— Phase 5B-3 解决（单一真源）

## 下一步

**不进入下一 Phase。Phase 5B-4 / Phase 6 均未获实施授权；等待 Review Gate 与下一阶段规划。**

待决策的开放问题（不要在未确认前动手）：
1. 是否补 Compose UI 测试源集（`androidTest`）。
2. Phase 4 遗留：Key 的注入方式。
3. 下一阶段的范围与排期。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- 已验收的 Phase 1–5B-2 结构不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
