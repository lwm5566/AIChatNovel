# CURRENT PHASE

> 本文件**只表示「现在」**。阶段切换时整体重写，不要在这里堆积历史（历史放 `PHASE_LOG.md`）。
> 最后更新：Phase 6B 实现完成后（本阶段改动已提交到本地 `master`，尚未 push）。

## 当前状态

**Phase 6B implementation complete —— 可执行时间轴与 StoryPlay 播放基础已建立；等待 Review Gate。**

- **Phase 5A**：**已 COMPLETE / CLOSED**（commit `f30208b`，已 push）
- **Phase 5B-1**：**已 COMPLETE / CLOSED**（commit `9d59177`，已 push）
- **Phase 5B-2**：**已 COMPLETE / CLOSED**（commit `a047904` + docs `b8b9377`，已 push）
- **Phase 5B-3**：**已 COMPLETE / CLOSED**（commit `c85e33c`，已 push）
- **Phase 6A**：**已 COMPLETE / CLOSED**（commit `39e2061`，已 push）
- **Phase 6B**：**实现完成**（时间轴 + 播放状态 + 本地模拟播放）；改动已提交到本地 `master`，**尚未 push**
- Phase 4 真实 DeepSeek 验证仍为 **blocked / deferred**（无有效 API Key），未因本阶段改变

## 本阶段（Phase 6B）目标与结果

目标：把 StoryPlay 从「静态展示」推进到「可按时间位置定位 Beat/Event，并能本地模拟播放」。

```
Story → Chapter → Scene → Beat → PerformanceEvent
                                        ↓
                                   Timeline（可执行）
                                        ↓
                                   PlaybackState
                                        ↓
                                   StoryPlay
```

**本阶段不是 TTS 阶段，也不是真实音频播放阶段。** 时长保留「估算 / 来源」概念，未来可自然接入
`TTS → AudioAsset → actual duration → Timeline duration backfill`。

| 项 | 结果 |
|---|---|
| 测试 | `./gradlew test --rerun` → **221 tests / 0 failures / 0 errors / 1 skipped**（Debug 与 Release 均为 221） |
| 测试数变化 | Phase 6A 的 162 → **221**（**+59**；未删除任何既有用例、未降低任何断言、未新增 skip） |
| 构建 | `./gradlew assembleDebug` → **BUILD SUCCESSFUL**（无 Kotlin 编译警告） |
| 生产缺陷 | **未发现**（新增测试一次通过） |

## 本阶段新增 / 修改的文件

**新增（生产）**

| 文件 | 作用 |
|---|---|
| `domain/model/ExecutableTimeline.kt` | `ExecutableTimeline` + `EventPosition`：把节拍铺开成可按位置查询的场景时间轴 |
| `domain/model/PlaybackState.kt` | `PlaybackStatus` / `PlaybackState` / `PlaybackCursor`：纯函数播放状态机 |
| `domain/mapping/TimelineMapping.kt` | `buildExecutableTimeline()` / `cursorAt()` / `PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS` |

**修改（生产）**

| 文件 | 作用 |
|---|---|
| `viewmodel/StoryPlayViewModel.kt` | 接入时间轴与播放状态，提供 `play` / `pause` / `reset` / `seekTo`，用 coroutine 驱动模拟推进 |
| `ui/storyplay/StoryPlayScreen.kt` | 最小播放器 UI：播放 / 暂停 / 重置、进度条、当前时间与总时长、当前 Beat/Event 高亮 |

**新增（测试）**

| 文件 | 用例数 |
|---|---|
| `test/domain/mapping/TimelineMappingTest.kt` | 23 |
| `test/domain/model/PlaybackStateTest.kt` | 22 |
| `test/viewmodel/StoryPlayViewModelTest.kt`（追加 14 例，原有 16 例全部保留） | 30 总计 |

**未改动**：Domain 既有模型（`Timeline` / `Timing` / `Beat` / `PerformanceEvent` / `Scene`）、DTO、Validator、Mapper、
Parser、PromptBuilder、`ParseSchema`、`AppContainer`、`StoryContentStore`、三个 InMemory Repository、
其他 ViewModel / UI / Navigation、Gradle、Manifest、`strings.xml`、ownership 规则。

## 时间轴契约（本阶段建立）

### 结构

```
Scene.timeline（解析期已知的元信息，totalDurationMillis 可空）
        ↓  buildExecutableTimeline(scene, beats)
ExecutableTimeline（派生、可直接播放）
 ├─ totalDurationMillis: Long              ← 总是有值
 ├─ durationSource: DurationSource
 └─ positions: List<EventPosition>
        └─ EventPosition(beatId, eventId, startOffsetMillis, durationMillis, durationSource)
             └─ endOffsetMillis = startOffsetMillis + durationMillis
```

`ExecutableTimeline` 是**派生数据**，不替代 `Scene.timeline`：后者允许时长未知，前者必须给出确定的播放入口。

### 布局规则

1. 节拍按 `Beat.order` 升序排列；上一个节拍的结束位置就是下一个的起点（Beat 本身没有场景级时间字段，起点由前序节拍累加得出）。
2. 事件位置 = 节拍起点 + `Timing.startOffsetMillis`（**负数一律按 0 处理**）。
3. 事件时长为空（或为负）时，用 `PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS`（1000ms）参与布局，来源标记 `Estimated`。
4. 总时长 = `max(场景已知总时长, 布局末端)`，避免事件被已知时长截断。
5. 空场景 / 空节拍 / 空事件 → 时长为 0 的空时间轴，不崩溃。

### 估算时长的语义边界（重要）

`PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS` **只回答「事件在时间轴上占多长」**，
它是**本地模拟播放**的估算：

- 不修改 AI DTO 的任何事实；
- 绝不把估算结果标成 `DurationSource.Audio`（只有 TTS 合成回填后才是 `Audio`）；
- 事件本身没有时长时，`EventPosition.durationSource` 一律为 `Estimated`。

### 游标规则（`cursorAt`）

- 事件区间是**闭区间** `[startOffsetMillis, endOffsetMillis]` —— 正好落在结束点也算命中；
- 同一时刻有多个事件（并发）时取**最近开始**的那个（起点最大；起点相同则取稳定顺序中靠后的）；
- 没有任何事件在演出（空档期 / 位置超出时间轴 / 空时间轴）→ 返回空游标，**不伪造事件**；
- 负位置按 0 处理。

## 播放状态契约（本阶段建立）

`PlaybackStatus` = `Idle` / `Playing` / `Paused` / `Completed`

| 转换 | 规则 |
|---|---|
| `play()` | 有内容且未播完 → `Playing`；无内容（时长 ≤ 0）→ 保持 `Idle`；已播完 → 保持不变（需先 `reset`） |
| `pause()` | 仅 `Playing` → `Paused`，**位置保留**；其他状态不变 |
| `reset()` | → `Idle`、位置 0、清空当前定位（时长保留） |
| `advanceBy(d)` | 仅 `Playing` 且 `d > 0` 时推进；位置被 clamp 到时长；到达末端 → `Completed` 且**不再前进** |
| `seekTo(p)` | 位置 clamp 到 `[0, durationMillis]`；从 `Completed` 跳回中间 → `Paused`（可继续播） |

- `positionMillis` 恒在 `[0, durationMillis]`；
- `progress`（0f..1f）与 `hasPlayableContent`（时长 > 0）是供 UI 使用的派生值；
- **UI 不复制这些规则**：Compose 只渲染 `PlaybackState` 并把意图回调给 ViewModel。

## StoryPlay 播放链路（本阶段建立）

```
StoryContentStore
    ↓  （content / imported）
PerformanceRepository / CharacterRepository / StoryRepository
    ↓
StoryPlayViewModel
    ├─ scene + beats → buildExecutableTimeline(...) → timeline: StateFlow<ExecutableTimeline>
    ├─ playbackState: StateFlow<PlaybackState>（duration 来自 timeline，位置由 tick coroutine 推进）
    └─ play / pause / reset / seekTo（唯一的状态协调点）
    ↓
StoryPlayScreen（render PlaybackState → emit intent）
```

- 时间推进由 **ViewModel 的 coroutine** 驱动（时间片 100ms，`viewModelScope`），**不使用** MediaPlayer / ExoPlayer；
- UI 层**没有**独立计时器，不自己计算当前事件；
- `viewModelScope` 在 ViewModel 销毁时自动取消；`onCleared()` 另外显式取消推进任务；
- 暂停 / 重置 / 播放完成都会终止推进任务；
- 时间轴变化（场景或节拍变化）时重置播放状态，并按新时长重新初始化。

## 长期硬规则（Phase 6B 未改变）

### snapshot 与内容一致性（Phase 5B-3 起生效）

- `StoryContentStore` 只有 `imported` 一个发布源；`content` 必须是它的派生视图，**不得**再引入第二条发布通道。

### metadata 与 ownership（Phase 5B-2 / 5B-1 起生效）

- metadata **只决定展示字段**，不参与 Story / Chapter ID 的生成、选择、查找或合并。
- Remote 分支继续以 `request.storyId` / `request.chapterId` 为唯一 authoritative 归属。
- 回退规则：`storyTitle` / `author` → `未命名作品` / `未命名作者`；`synopsis` → 空串；`chapterTitle` → `未命名章节`。
- 本地样例路径的元信息仍是占位值（fixture 不读 request）。

### 演出语义（Phase 6A 起生效）

- `orderedEvents` 按 `Timing.startOffsetMillis` 升序，起点相同保持相对顺序。
- `effectivePresentationMode` / `effectiveSpeakerId` / `effectiveVoiceProfile` 的派生优先级不变。
- `voiceLabel` / `Character.voiceProfile` 仍为 `null`（导入链路不产生音色）——**本阶段未绑定任何音色**。

## Failure / Partial / Importing 语义（未变）

| 结果 | 行为 |
|---|---|
| `Success` | 写入当前导入快照（story + chapters + content） |
| `Partial` | **同样写入**（视为可应用结果） |
| `Failure` | **不写入**；首次失败 → 仍无当前 Story；后续失败 → 保留上一次成功的快照 |
| `Importing` | 旧快照继续可见，不提前清空 |

## 尚未实现（明确不在本阶段范围）

- TTS（任何形式的语音合成）、音频生成、音频文件、`MediaPlayer` / `ExoPlayer` / Media3 / `AudioTrack`、音频混音
- `DurationSource.Audio` / `Manual` 的实际回填流程
- `VoiceProfile` 的实际绑定与音色选择 UI
- 图片生成、视频生成、MP4、ffmpeg、图片 / 视频资源系统（`AssetRef` 的实际解析与绑定）
- Timeline Track 系统、Audio/Video Track、复杂 Gap 系统
- 全局 Beat / Event ID 重构、`StoryPlay` 改名
- Room / SQLite / DataStore / 登录 / 云数据库 / 多作品历史 / metadata editor
- `PresentationEvidence.sourceSpan` 的 UI 展示（仍为 backlog）
- DeepSeek 真实成功调用（无有效 Key）

## 遗留问题

Phase 4 遗留（未变）：
1. 真实 DeepSeek 成功调用 **BLOCKED / DEFERRED** —— 无有效 API Key
2. 远程模式在 App 内无 Key 注入方式（无密钥输入 UI）
3. `repository/StoryImportResult` 引用 `data.parser.validation.ValidationResult` —— Phase 5B-3 判断暂不修改
4. 真实模型 offset 错误时 `INVALID_SOURCE_SPAN_RANGE` 是 ERROR，会导致整章导入失败（**未放宽**）

Phase 5A 遗留：
5. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）
6. `AIChatNovelApplication` 启动预载保留
7. `StoryPlayScreen`（Phase 2 已验收）的全局 LazyColumn key 写法未改

Phase 5B-1 遗留（审计结论：保持不动）：
8. Remote `Scene.id` 保留 Mapper 生成的前缀
9. Remote 多章收敛为单章
10. `LOCAL_SAMPLE` 也消耗一对导入 ID
11. 仅保留单个当前快照、没有历史

Phase 6A 记录（非缺陷，保持不动）：
12. DTO 的 `confidence` 单向丢弃（需先有低置信度复审需求）
13. `Timeline` 类名暗示时间轴结构，实际只有两个标量 —— **Phase 6B 新增 `ExecutableTimeline` 作为派生层，`Timeline` 本身仍保持原样**
14. 呈现介质覆盖的展示判断在 Explorer 与 StoryPlay 各有一处表达
15. `Beat.id` / `PerformanceEvent.id` 未按场景限定（UI 用 `beatUiKey` / `eventUiKey` 规避）
16. `InMemoryStoryRepository` 前两个方法读 `imported`、后两个读 `content`（同源，靠约定维持）

Phase 6B 新增：
17. `Beat` 没有场景级起始时间字段 —— 本阶段用「`order` 升序 + 前序节拍末端累加」推导；若将来需要显式节拍起点（例如节拍之间要有独立停顿），属于 **Domain 契约变更**，需先说明原因
18. `PLAYBACK_ESTIMATED_EVENT_DURATION_MILLIS`（1000ms）是**固定**的播放模拟估算，不是按文本长度估算 —— 待 TTS 接入后由真实音频时长取代
19. 播放推进基于墙上时间片的 coroutine（100ms），只用于本地模拟；接入真实播放器后应由播放器驱动
20. `ExecutableTimeline` 目前每次 `scene` / `beats` 发射都会重建（数据量为单场景级别，无需缓存）

已解决：
21. ~~演出侧语义规则无回归保护~~ —— Phase 6A 解决
22. ~~StoryPlay 只能静态展示、无法按时间位置定位事件、无播放状态~~ —— Phase 6B 解决

## 下一步

**不进入下一 Phase。Phase 6C / Phase 7 均未获实施授权；等待 Review Gate 与下一阶段规划。**

待决策的开放问题（不要在未确认前动手）：
1. 下一阶段的范围与排期。
2. 是否接入 TTS / 真实音频（会决定 `DurationSource.Audio` 的回填时机）。
3. 是否补 Compose UI 测试源集（`androidTest`）。
4. Phase 4 遗留：API Key 的注入方式。

## 当前项目红线（每次开工前自查）

- 本项目**不是**即时通讯软件；普通面对面剧情必须 `LiveScene`。
- AI 输出**不得**直接进入 Domain，必须走 DTO → Validator → Mapper。
- 不得为了让 AI 或测试更容易通过而放宽 Validator。
- **估算时长不得伪装成真实音频时长**（`Estimated` ≠ `Audio`）。
- 已验收的 Phase 1–6A 结构与语义（ownership / metadata / 单源快照 / 演出规则）不要无理由重写。

详见 `ARCHITECTURE_RULES.md` 与 `DOMAIN_CONTRACT.md`。
