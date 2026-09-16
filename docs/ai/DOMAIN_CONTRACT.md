# DOMAIN CONTRACT

> 领域契约的**权威来源**。记录字段、语义与默认值，**不复制源码**——真实实现以 `app/src/main/java/com/aichatnovel/app/domain/` 为准。
> 状态：**Phase 2 建立、Phase 3 补充、已验收通过**。后续阶段若要修改，必须先说明原因（见 ARCHITECTURE_RULES 第 15、16 条）。
> 最后更新：Phase 4 完成时。

## 1. 层级结构

```
Story 1──* Chapter 1──* Scene 1──* Beat 1──* PerformanceEvent
```

### Story
`id`、`title`、`author`、`synopsis`。内容组织的根节点。

### Chapter
`id`、`storyId`、`index`、`title`。`Scene` 的容器。

### Scene
`id`、`chapterId`、`index`、`title`、`presentationMode`（默认 `LiveScene`）、`presentationEvidence`（可空）、`setting: SceneSetting`、`timeline: Timeline`、`characters: List<CharacterSceneState>`。

- **Scene 不携带 beats**：演出节拍按需单独加载（`PerformanceRepository.observeBeats(sceneId)`）。
- 场景是「剧情发生的时空单位」，**不是聊天会话**。

### Beat
`id`、`order: Int`、`events: List<PerformanceEvent>`。

- 同一 Beat 内的多个事件**允许并发 / 时间重叠**（例如对白 + 动作 + 环境音 + 镜头同时发生）。
- 事件之间的先后由各自的 `Timing.startOffsetMillis` 决定，**不是列表顺序**。
- 节拍之间按 `order` 先后播放。

### PerformanceEvent（sealed interface）
公共字段：`id`、`timing: Timing`、`presentationOverride: PresentationMode?`、`sourceSpan: SourceSpan?`。

| 变体 | 专有字段 |
|---|---|
| `DialogueEvent` | `dialogue: Dialogue`、`utterance: Utterance`、`speech: SpeechParams?`、`voiceOverride: VoiceProfile?` |
| `NarrationEvent` | `narration: Narration` |
| `ActionEvent` | `action: Action` |
| `EnvironmentEvent` | `environment: Environment` |
| `SoundEvent` | `description`、`soundRef: AssetRef?` |
| `CameraEvent` | `description`、`shotRef: AssetRef?` |

- `presentationOverride` 为空 = **继承所属 Scene 的 presentationMode**；只有明确的媒介切换才允许覆盖。
- `sourceSpan` 允许为空（例如由演出编排额外补充的环境音、镜头）。

## 2. 内容级值对象

| 类型 | 字段 | 语义 |
|---|---|---|
| `Dialogue` | `characterId`、`text`、`emotion?` | **剧情层事实**：某个角色在某个场景说出的话。**不是 IM 消息**，不承载 TTS 参数 |
| `Narration` | `text` | 旁白：非角色直接说出的叙述文字 |
| `Action` | `characterId?`、`description` | 角色动作；`characterId` 为空表示群体 / 未指名动作 |
| `Environment` | `description`、`location?` | 环境描述：氛围、光线、天气等 |

## 3. 时间与时间轴

| 类型 | 字段 | 语义 |
|---|---|---|
| `Timeline` | `totalDurationMillis: Long?`、`durationSource: DurationSource?` | Scene 的整体时间轴；**总时长允许为空**（解析阶段尚不知道） |
| `Timing` | `startOffsetMillis: Long`、`durationMillis: Long?`、`durationSource: DurationSource?` | 事件在所属 Beat 内的位置；**duration 允许为空** |
| `DurationSource` | `Estimated` / `Audio` / `Manual` | 时长来源。**不绑定任何 TTS 服务** |

**时间分三层**（这是 Phase 2 的核心结构变化）：
```
Scene.timeline（总时长，可空）
  → Beat.order（节拍顺序）
    → PerformanceEvent.timing（相对所属 Beat 起点，可并发）
```

## 4. 呈现介质

| 类型 | 字段 | 语义 |
|---|---|---|
| `PresentationMode` | `LiveScene` / `InstantMessaging` / `PhoneCall` / `Letter` / `RecallOrFlashback` | 见 PROJECT_BASELINE 的语义红线 |
| `PresentationEvidence` | `text`、`sourceSpan: SourceSpan?` | 呈现介质的**判定依据**，必须能回指原文 |

- 默认值必须是 `LiveScene`。
- 非 `LiveScene` 的场景**必须**提供 `presentationEvidence`，否则由 Validator 告警 + Mapper 降级（见第 6 节）。

## 5. 发声与音色（TTS 预留）

| 类型 | 字段 | 语义 |
|---|---|---|
| `Utterance` | `speakerId?`、`text`、`emotion?`、`speakingStyle?`、`addressee?`、`isInnerMonologue`、`language?` | **演出层**：这一次由谁、以什么情绪/风格、念给谁听 |
| `SpeechParams` | `rate?`、`pitch?`、`volume?`（全部可空 = 用默认值） | 逐次发声的参数，不绑定 TTS 服务 |
| `VoiceProfile` | `voiceRef?`、`defaultSpeech: SpeechParams` | 角色**默认**音色档案 |

**Dialogue / Utterance / SpeechParams 的职责区分**：
- `Dialogue` = 剧情层事实（作者写的台词）
- `Utterance` = 演出层指令（这次怎么演）
- `SpeechParams` = 这次发声的数值参数

**继承规则**：`DialogueEvent.voiceOverride` 为空 → 运行时继承该角色的 `Character.voiceProfile`（由 `domain/mapping/PerformanceMapping.kt` 的 `effectiveVoiceProfile` 表达）。

## 6. 资源与溯源

| 类型 | 字段 | 语义 |
|---|---|---|
| `AssetRef` | `id`、`type: AssetType`、`source?` | **统一资源引用**；`AssetType` = `IMAGE` / `AUDIO` / `VIDEO` / `VOICE` / `SPRITE` |
| `SourceSpan` | `chapterId`、`startOffset: Int`、`endOffset: Int`、`snippet` | **原文溯源**：指回原小说字符位置（0 起、左闭右开） |

`AssetRef` 的既有使用点：`SoundEvent.soundRef`、`CameraEvent.shotRef`、`Character.defaultAvatarRef`、`CharacterSceneState.avatarRef`、`SceneSetting.backgroundRef`。

## 7. 其他模型

| 类型 | 字段 | 语义 |
|---|---|---|
| `Character` | `id`、`storyId`、`name`、`description`、`aliases: List<String>`、`defaultAvatarRef: AssetRef?`、`voiceProfile: VoiceProfile?` | `aliases` 用于 AI 消歧 |
| `SceneSetting` | `location?`、`timeOfDay?`、`weather?`、`lighting?`、`backgroundRef: AssetRef?` | 给视觉模块用的舞台信息 |
| `CharacterSceneState` | `characterId`、`position?`、`facing?`、`expression?`、`costume?`、`avatarRef: AssetRef?` | 「角色 × 场景」的状态 |
| `StoryContent` | `characters`、`scenes`、`beatsByScene` | 一次导入的产出，是 Repository ↔ UI 的交换单位 |
| `AppSettings` | `showNarration`、`autoAdvanceScenes` | 占位设置 |

## 8. 解析契约：AI 输出**不能**直接进 Domain

**唯一允许的链路**（Phase 3 已验收）：

```
原文 + AI 响应 JSON
  → ParseResponseDto        （DTO 层，独立于 domain）
  → ParseValidator          （只报告，不修改 DTO）
  → AiParseMapper           （DTO → Domain，唯一映射点）
  → StoryContent
  → Repository → ViewModel → UI
```

关键约束：
- **DTO 与 Domain 必须隔离**，DTO 不复用任何 domain 类型。
- Validator 产出 `ValidationResult{issues}`，分 `errors` / `warnings`；**有 ERROR 时管线不映射**（`content = null`）。
- Mapper 负责：`schemaVersion`、`tempId` → 稳定 id、别名、`presentationMode` 默认值与证据校验、`SourceSpan`、`Beat` 顺序、事件类型映射、`Timing`、`Utterance`、`SpeechParams`、`VoiceProfile` 继承。
- `schemaVersion` 只由 `ParseSchema` 定义（当前 `"1.0"`），Mapper 不持有版本常量。

### 解析契约的关键规则（不可放宽）

1. `presentationMode` 缺省 → `LiveScene`。
2. 取值为非 `LiveScene` 但**缺少 `presentationEvidence`** → Validator 发 WARNING，**Mapper 自己再检查并降级为 `LiveScene`** + WARNING。**绝不静默接受**。
3. `tempId` 只是本次响应的临时 ID，**不得作为最终业务 ID**。角色稳定 id 由名字派生；场景 id 需按章节限定（跨章节 tempId 会冲突）。
4. AI 不产出音色 → `voiceOverride = null`，运行时继承 `Character.voiceProfile`。
5. AI **不负责**最终音频时长 → `duration` 允许为空；给了 duration 未给来源时默认 `Estimated`。TTS 完成后才回填 `Audio`。
6. `SourceSpan` 的 `startOffset`/`endOffset` 必须是**原文真实字符位置**，`snippet` 必须与区间逐字一致。

## 9. 稳定 id 规则

| 对象 | 规则 |
|---|---|
| Character | `"char-" + 名字小写 slug`（同名跨章节自动去重） |
| Scene | `"<chapterId>-<id 或 tempId>"` |

> 历史坑：两份样例的场景 tempId 都叫 `s1`，不按章节限定会导致 `beatsByScene` 互相覆盖。
