# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 5B-2 实现完成后（未 commit）。

## 当前状态

**Phase 5B-2 implementation complete —— 用户显式提供的作品 / 章节元信息已接入导入流程；等待 Review Gate。**

- **Phase 5A**：**已 COMPLETE / CLOSED**（commit `f30208b`，已 push）
- **Phase 5B-1**：**已 COMPLETE / CLOSED**（commit `9d5917752dd7600fe1fc9da6f4008d513f5397af`，父 `f30208b`；**已 commit，尚未 push**）
- **Phase 5B-2**：**实现完成**（元信息由用户显式提供）；**未 commit、未 push**
- Phase 4 真实 DeepSeek 验证仍为 **blocked / deferred**（无有效 API Key），未因本阶段改变

## 本阶段（Phase 5B-2）目标与结果

目标：让**真实的** Story / Chapter 元信息通过「用户显式输入」进入导入流程，替换硬编码占位值。

| 项 | 结果 |
|---|---|
| 输入通道 | `StoryImportRequest` 新增 `storyTitle` / `author` / `synopsis` / `chapterTitle`（均可空） |
| 输入 UI | `ImportScreen` 新增「作品与章节信息」区（4 个输入框 + 留空说明 + 本地样例模式提示） |
| 状态管理 | `ImportViewModel.ImportUiState` 新增 4 个字段与对应事件；编辑元信息同样不解除 `Importing` |
| Remote 映射 | 使用用户输入；null / 空白（trim 后）回退到占位值 |
| Local Sample | **忽略** request 携带的元信息（裁决 α-2），继续使用 fixture 归属与占位元信息 |
| 测试 | `./gradlew test --rerun` → **109 tests / 0 failures / 0 errors / 1 skipped** |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL**（无 Kotlin 编译警告） |

**未改动**：Domain 模型 / DTO / Validator / Mapper / DeepSeek remote client / PromptBuilder / `ParseSchema` / `StoryContentStore` / 三个 InMemory Repository / `ui/explorer` / `ui/novel` / Navigation / Gradle。

### metadata 与 ownership 的关系（硬规则）

- metadata **只决定展示字段**，**不参与** Story / Chapter ID 的生成、选择、查找或合并。
- metadata **不触碰** `SourceSpan.chapterId`、`Scene.id`、`beatsByScene` 的 key。
- Remote 分支继续以 `request.storyId` / `request.chapterId` 为唯一 authoritative 归属。
- 回退规则：`storyTitle` / `author` → `未命名作品` / `未命名作者`；`synopsis` → 空串；`chapterTitle` → `未命名章节`。
  **从不**把空白字符串写进 Domain。
- metadata **不改变** Success / Partial / Failure / Importing 的任何行为。

### 仍需占位值的地方

本地样例（Local Sample）路径的元信息**仍是占位值** —— 它是 fixture，不读 request。这是 Phase 5B-2 的明确裁决（α-2），**不是遗留缺陷**。

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
- AI 自动生成 metadata（本阶段明确采用用户输入方案）
- Timeline 播放、TTS、音频、视频、MP4
- DeepSeek 真实成功调用（无有效 Key）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult`（接口层依赖数据层类型）
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

已解决：
12. ~~M2：任意导入显示 `SampleStoryData` 静态元信息~~ —— Phase 5B-1 解决
13. ~~Story / Chapter 元信息为硬编码占位值（Remote 路径）~~ —— Phase 5B-2 解决（本地样例按裁决保留占位）

## 下一步

**不进入下一 Phase。等待 Phase 5B-2 Review Gate。**

待决策的开放问题（不要在未确认前动手）：
1. Phase 5B-2 是否验收 / 提交。
2. 是否补 Compose UI 测试源集（`androidTest`）。
3. Phase 4 遗留：Key 的注入方式。
4. `9d59177` 何时 push（当前本地领先 1 个 commit，且本轮还有未提交改动）。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- 已验收的 Phase 1–5B-1 结构不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
