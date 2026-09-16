# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 6A 实现完成后（本阶段改动已提交到本地 `master`，尚未 push）。

## 当前状态

**Phase 6A implementation complete —— 演出语义契约与 StoryPlay 只读链路已固化为可回归契约；等待 Review Gate。**

- **Phase 5A**：**已 COMPLETE / CLOSED**（commit `f30208b`，已 push）
- **Phase 5B-1**：**已 COMPLETE / CLOSED**（commit `9d59177`，已 push）
- **Phase 5B-2**：**已 COMPLETE / CLOSED**（commit `a047904`，已 push）
- **Phase 5B-3**：**已 COMPLETE / CLOSED**（commit `c85e33c`，已 push）
- **Phase 6A**：**实现完成**（演出语义契约固化）；改动已提交到本地 `master`，**尚未 push**
- Phase 4 真实 DeepSeek 验证仍为 **blocked / deferred**（无有效 API Key），未因本阶段改变

## 本阶段（Phase 6A）目标与结果

目标：把「导入完成后、播放开始前」这一层**已被使用但未被测试**的既有演出语义锁定为可回归契约。**不新增业务功能、不修改生产代码、不重写 Domain。**

| 工作包 | 内容 | 新增用例 |
|---|---|---|
| 1 | `domain/mapping` 演出规则契约测试 | 11 |
| 2 | `InMemory*Repository` 读取契约测试（乱序 + 外键过滤 + 按 id 查找） | 11 |
| 3 | `StoryPlayViewModel` 只读链路契约测试（真实 Store + 真实 Repository） | 16 |
| 4 | `AiParseMapper` 边界规则补测 | 11 |
| — | **合计** | **+49** |

| 项 | 结果 |
|---|---|
| 测试 | `./gradlew test --rerun` → **162 tests / 0 failures / 0 errors / 1 skipped** |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL**（无 Kotlin 编译警告） |
| 生产代码 | **0 改动**（本阶段只新增测试文件） |

**未改动**：Domain / DTO / Validator / Mapper / parser / remote client / PromptBuilder / `ParseSchema` / `AppContainer` / `StoryContentStore` / 三个 InMemory Repository / ViewModel / UI / Navigation / Gradle / Manifest / 持久化层。

### 本阶段新锁定的既有语义（此前无回归保护）

- `orderedEvents`：同一 Beat 内按 `Timing.startOffsetMillis` 升序；起点相同的事件保持相对顺序
- `effectivePresentationMode`：`override ?: sceneMode`（`override == sceneMode` 时结果仍是该 mode）
- `effectiveSpeakerId`：`utterance.speakerId ?: dialogue.characterId`
- `effectiveVoiceProfile`：`voiceOverride ?: 角色档案`；两者皆无 → `null`
- Repository 读取：按 `storyId` / `chapterId` 过滤；按 `index` / `order` 排序；`observeScene` 命中与未命中
- StoryPlay 场景选择：显式 `sceneId` 精确选择（不混入其他场景）；无 `sceneId` 时按 `story → 首个 chapter → 首个 scene` 回退
- StoryPlay 空数据：无 Story / 无 Chapter / 无 Scene / 无 Beat 四种情形均不崩溃、`beats` 为空、`isLoading` 最终为 false、不伪造数据
- `BeatUi.label` 形如 `"节拍 N"`；`PerformanceLine.kind` 的六种取值（对白 / 旁白 / 动作 / 环境 / 环境音 / 镜头）
- Mapper 边界：有 `duration` 而无 `durationSource` → `DurationSource.Estimated`；缺 `duration` → `duration` 与 `durationSource` 均为 null；缺 `timing` → 起点 0 且无 duration
- Mapper 资源与角色：`setting.backgroundRef` → `AssetRef(IMAGE)`；`participants` → `Scene.characters` 的 `CharacterSceneState(characterId)` 且**去重**、未声明角色被丢弃

### 被明确锁定为「当前为无」的行为（设计预留，不是缺陷）

以下行为已由测试显式锁定，避免将来被误判为 bug：

- `PerformanceLine.voiceLabel == null` —— 当前导入链路不产生任何 `VoiceProfile`（AI 不提供音色）
- `Character.voiceProfile == null`、`Character.defaultAvatarRef == null` —— 同上
- `CharacterSceneState` 除 `characterId` 外全部为 null —— AI 的 `participants` 只带角色引用

### snapshot 与内容一致性（硬规则，Phase 5B-3 起生效）

- `StoryContentStore` 只有 `imported` 一个发布源；`content` 必须是它的派生视图，**不得**再引入第二条发布通道。
- 新代码若需要「当前快照的内容」，从 `imported` 派生，不要另建 `StateFlow`。

### metadata 与 ownership（硬规则，Phase 5B-2 起生效）

- metadata **只决定展示字段**，**不参与** Story / Chapter ID 的生成、选择、查找或合并。
- metadata **不触碰** `SourceSpan.chapterId`、`Scene.id`、`beatsByScene` 的 key。
- Remote 分支继续以 `request.storyId` / `request.chapterId` 为唯一 authoritative 归属。
- 回退规则：`storyTitle` / `author` → `未命名作品` / `未命名作者`；`synopsis` → 空串；`chapterTitle` → `未命名章节`。**从不**把空白字符串写进 Domain。
- 本地样例（Local Sample）路径的元信息仍是占位值 —— 它是 fixture，不读 request（裁决 α-2）。

## Failure / Partial / Importing 语义（未变）

| 结果 | 行为 |
|---|---|
| `Success` | 写入当前导入快照（story + chapters + content） |
| `Partial` | **同样写入**（视为可应用结果） |
| `Failure` | **不写入**；首次失败 → 仍无当前 Story；后续失败 → 保留上一次成功的快照 |
| `Importing` | 旧快照继续可见，不提前清空 |

## 尚未实现（明确不在本阶段范围）

- 播放器 / 时间推进 / Timeline 重构（gap、track）
- TTS、音频生成与播放、`DurationSource.Audio` / `Manual` 的实际回填
- `VoiceProfile` 的实际绑定、`CharacterSceneState` 的视觉字段填充
- 资源系统（`AssetRef` 的实际解析与绑定）、图片 / 视频生成、MP4
- 多作品 / 历史管理、数据库、登录、云端历史的任何形式
- 导入后编辑 metadata
- DeepSeek 真实成功调用（无有效 Key）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult` —— Phase 5B-3 判断暂不修改（非实现耦合；`ValidationCode` 词汇表本质属解析契约；真正的解耦需真实需求驱动）
4. 真实模型 offset 错误时 `INVALID_SOURCE_SPAN_RANGE` 是 ERROR，会导致整章导入失败（**未放宽**）

Phase 5A 遗留：
5. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）
6. `AIChatNovelApplication` 启动预载保留（走同一套「当前导入」写入路径）
7. `StoryPlayScreen`（Phase 2 已验收）的全局 LazyColumn key 写法未改

Phase 5B-1 遗留（非 blocker，审计结论：保持不动）：
8. Remote `Scene.id` 保留 Mapper 生成的前缀（可能与归一化后的 `chapterId` 前缀不同；功能无影响）
9. Remote 多章收敛为单章（符合「一次导入 = 一章原文」语义）
10. `LOCAL_SAMPLE` 也消耗一对导入 ID（样例忽略之，无害）
11. 仅保留单个当前快照、没有历史（设计要求）

Phase 6A 新增的「已发现但明确延期」项（**均非缺陷**，按勘察结论保持不动）：
12. DTO 的 `confidence` 有值但 Domain 不承载 → 单向丢弃且无提示；需先有「低置信度复审」的产品需求
13. `Timeline` 类名暗示时间轴结构，实际只有总时长 + 来源两个标量（命名预期落差）
14. `StoryPlay` 命名承诺了播放职责，当前只做只读展示；改名属无关重构
15. 呈现介质覆盖的展示判断在 Explorer（raw `presentationOverride`）与 StoryPlay（`effectivePresentationMode`）各有一处表达
16. `PresentationEvidence.sourceSpan` 在 UI 未展示（**保留为 backlog，本阶段明确不做**）
17. `Beat.id` / `PerformanceEvent.id` 未按场景限定（UI 已用 `beatUiKey` / `eventUiKey` 规避）
18. `InMemoryStoryRepository` 前两个方法读 `imported`、后两个与其余 Repository 读 `content`（Phase 5B-3 后同源，安全但靠约定维持）

已解决：
19. ~~M2：任意导入显示 `SampleStoryData` 静态元信息~~ —— Phase 5B-1 解决
20. ~~Story / Chapter 元信息为硬编码占位值（Remote 路径）~~ —— Phase 5B-2 解决（本地样例按裁决保留占位）
21. ~~`StoryContentStore` 双 `StateFlow` 顺序发布导致的中间窗口~~ —— Phase 5B-3 解决（单一真源）
22. ~~演出侧语义规则（`orderedEvents` / `effective*` / Repository 排序 / StoryPlay 链路）无回归保护~~ —— Phase 6A 解决（+49 用例）

## 下一步

**不进入下一 Phase。Phase 6B / Phase 7 均未获实施授权；等待 Review Gate 与下一阶段规划。**

待决策的开放问题（不要在未确认前动手）：
1. 下一阶段的范围与排期。
2. 是否补 Compose UI 测试源集（`androidTest`）。
3. Phase 4 遗留：Key 的注入方式。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- 已验收的 Phase 1–5B-3 结构与语义（ownership / metadata / 单源快照 / 演出规则）不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
