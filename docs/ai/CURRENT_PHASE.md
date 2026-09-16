# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 5B-1 交付后。

## 当前状态

**Phase 5B-1 implementation complete —— 「当前导入」归属隔离已落地；等待审核。**

- **Phase 5A**：**已 COMPLETE / CLOSED**（commit `f30208b`，85 tests 全绿，已 push）
- **Phase 5B-1**：**实现完成**，解决 Phase 5A 遗留 **M2**（Story / Chapter 元信息与导入结果脱节）；**未 commit、未 push**
- **Phase 5B-2**：**未开始**（真实 metadata 由用户显式提供）
- Phase 4 真实 DeepSeek 验证仍为 **blocked / deferred**（无有效 API Key），未因本阶段改变

## 本阶段（Phase 5B-1）目标与结果

目标：**当前导入的 Story / Chapter / Content 必须属于同一次导入**，且不再显示 `SampleStoryData` 的静态 metadata。

### M2 根因（已确认）

Story / Chapter 的展示 metadata 与 `StoryContent` **完全分离**：
`StoryContent` 不含 Story/Chapter，`ParseResponseDto` 也没有 title / author / chapterTitle / chapterIndex。
因此 `InMemoryStoryRepository` 只能用 `SampleStoryData` 静态常量兜底，而 `AppContainer` 又把
`SampleStoryData.STORY_ID` / `CHAPTER_ID_1` 当作**任意**导入的归属 id。

### 本阶段做了什么

| 项 | 结果 |
|---|---|
| 当前导入快照 | 新增 `ImportedStory(story, chapters, content)`，由 `StoryContentStore` 单一槽位持有 |
| Story / Chapter 来源 | `InMemoryStoryRepository` 改为**全部从当前导入快照派生**；未导入时返回空 |
| `SampleStoryData` | 降级为 **sample fixture**，不再参与任何普通导入 |
| Story / Chapter ID | **由导入方决定**：普通导入用本次请求生成的 id（authoritative），模型回显的 `storyId` / `chapterId` **一律不采纳**；本地样例保留 fixture 自己的 id |
| 归属自洽 | `chapter.storyId == story.id`；Remote 下 content 的归属字段**无条件归一**到 authoritative id，因此模型返回错误 id 也不会影响 ownership |
| metadata | **明确的占位值**（见下） |
| 测试 | `./gradlew test --rerun` → **97 tests / 0 failures / 0 errors / 1 skipped** |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL**（无 Kotlin 编译警告） |

**未改动**：Domain 模型 / DTO / Validator / Mapper / DeepSeek remote client / PromptBuilder / `ParseSchema` / UI / Navigation / Gradle。

### ID authority（Review Gate 修正）

ownership **必须由 App / 协调层决定**，不能由 DeepSeek 返回的 `storyId` / `chapterId` 决定：

```
Remote（普通导入，语义 = 一章原文）：
  storyId   = request.storyId      ← 本次导入唯一 authoritative
  chapterId = request.chapterId    ← 本次导入唯一 authoritative
  chapters  = [ Chapter(id = request.chapterId, storyId = request.storyId, index = 1) ]
  content   = content.copy(        ← 无条件归一，不看“是否已经一致”
      characters = characters.map { it.copy(storyId = request.storyId) },
      scenes     = scenes.map { it.copy(chapterId = request.chapterId) },
  )

Local Sample（fixture，忽略请求参数）：
  storyId   = 样例内容自身声明的 storyId
  chapters  = 样例内容里的章节（保留两章，index = 1,2）
  content   = 原样，不做不必要的修改
```

- 模型回显的 id 仅属**解析协议**，不作 ownership 依据；即使模型返回错误值，最终归属不变。
- **不**重建 `Scene.id`、**不**动 `beatsByScene` 的 key、**不**改 `SourceSpan.chapterId`（那是原文溯源信息）。
- 归一化只发生在 `AppContainer` 协调层（`copy` 不可变值对象），未修改任何 `domain/`、Mapper、DTO、Remote 文件。

### ⚠️ 占位 metadata 是临时的

当前 AI 输出契约里**没有** story title / author / chapter title / chapter index，
本阶段**不编造**（不猜小说首行、不让模型生成、不改 Prompt / DTO / Schema），只填明确的占位值：

```
Story.title    = "未命名作品"
Story.author   = "未命名作者"
Story.synopsis = ""
Chapter.title  = "未命名章节"
Chapter.index  = 本轮导入中章节出现的顺序（1..N）
```

**这些占位值不是小说的真实 metadata，将在 Phase 5B-2 由用户显式提供的信息替换。**

## 已完成（Phase 5A，已 CLOSED）

- 导入页：原文输入、示例填充、模式选择（Local Sample / DeepSeek）、五态结果卡片
- 解析结果页：Story → Chapter → Scene → Beat → PerformanceEvent 展示树
- DeepSeek 无 Key 时明确失败（`MISSING_API_KEY`），不伪造成功、不静默回退

## Failure / Partial / Importing 语义（本阶段保持不变）

| 结果 | 行为 |
|---|---|
| `Success` | 写入当前导入快照（story + chapters + content） |
| `Partial` | **同样写入**（视为可应用结果） |
| `Failure` | **不写入**；首次失败 → 仍无当前 Story；后续失败 → 保留上一次成功的快照 |
| `Importing` | 旧快照继续可见，不提前清空 |

## 尚未实现（明确不在本阶段范围）

- **真实 Story / Chapter metadata**（Phase 5B-2：用户显式提供）
- 多作品 / 历史管理（Store 只有「当前导入」一个槽位，第二次导入替换第一次）
- Timeline 播放、TTS、音频、视频、MP4
- DeepSeek 真实成功调用（无有效 Key）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult`（接口层依赖数据层类型）
4. 真实模型 offset 错误时 `INVALID_SOURCE_SPAN_RANGE` 是 ERROR，会导致整章导入失败（**未放宽**）

Phase 5A 遗留：
5. ~~**M2**：任意导入仍显示 `SampleStoryData` 的静态 metadata~~ —— **Phase 5B-1 已解决**
6. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）
7. `AIChatNovelApplication` 启动预载保留（现已走同一套「当前导入」写入路径）
8. `StoryPlayScreen`（Phase 2 已验收）的全局 LazyColumn key 写法未改

Phase 5B-1 新增：
9. **Story / Chapter metadata 仍是占位值** —— 待 Phase 5B-2
10. 多次导入只保留「当前」一份快照，不保留历史（符合本阶段要求）
11. `AppContainer` 在 `LOCAL_SAMPLE` 下也会消耗一对导入 ID（样例实现忽略它，无害但略有浪费）

## 下一步

**不进入 Phase 5B-2。等待审核。**

待决策的开放问题（不要在未确认前动手）：
1. Phase 5B-1 是否验收。
2. Phase 5B-2：metadata 由**用户显式输入**（推荐）还是扩展 DTO / Prompt / Schema 让 AI 产出。
3. 是否补 Compose UI 测试源集（`androidTest`）。
4. Phase 4 遗留：Key 的注入方式。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- 已验收的 Phase 1–5A 结构不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
