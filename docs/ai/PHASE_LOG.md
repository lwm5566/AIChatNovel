# PHASE LOG

> 阶段历史摘要。**只记结论，不复制聊天记录，不复制源码与测试代码。**
> 完整原始输出请查 AiCode 的工具输出存储（`~/.aicode/tool-output/`）或重新运行命令。
> 最后更新：Phase 4 完成时。

---

## Phase 1 — 工程骨架与架构

- **目标**：建立一个能在 Android Studio 打开、结构清晰的 Kotlin + Compose 基础工程，为后续接入 AI / TTS / 数据库 / 视频预留位置。
- **主要产出**
  - Gradle 工程配置（`settings.gradle.kts`、根/模块 `build.gradle.kts`、`gradle/libs.versions.toml`、wrapper）
  - 分层骨架：`domain/model`、`repository`（只放接口）、`data/repository`、`viewmodel`、`ui/<feature>`、`navigation`、`di`
  - 6 个页面：Home / Novel / Character / Scene / StoryPlay / Settings（Route + 无状态 Screen 成对）
  - 共约 33 个 Kotlin 文件
- **关键架构决定**
  - MVVM + `ViewModel` 暴露 `StateFlow<XxxUiState>`，`stateIn(WhileSubscribed(5000))`
  - Repository 一律先定义**接口**，实现放 `data/`
  - **不引入 Hilt**，用手工 `AppContainer` + `viewModelFactory { initializer { ... } }`
  - 领域模型**不以** `ChatMessage` / `GroupChat` 为核心（项目的核心语义约束从第一天就确立）
- **测试结果**：无（Phase 1 未要求）
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，APK ≈ 9.5 MB
- **遗留问题**：无

---

## Phase 2 — Domain Model 升级

- **目标**：让领域模型足以支撑「解析 → 演出 → TTS → 视觉」的长期演进。
- **主要产出**
  - 新增 `PresentationMode`、`PresentationEvidence`、`Timeline`/`Timing`/`DurationSource`、`Beat`、`SceneSetting`/`CharacterSceneState`、`Utterance`/`SpeechParams`/`VoiceProfile`、`AssetRef`/`AssetType`、`SourceSpan`
  - 改造 `PerformanceEvent`（引入 `timing` / `presentationOverride` / `sourceSpan`；`DialogueEvent` 分层为 dialogue + utterance + speech + voiceOverride）
  - 改造 `Scene`（`presentationMode` / `setting` / `timeline` / `characters`）、`Character`
  - 新增 `domain/mapping/PerformanceMapping.kt`（呈现介质继承、发声者回落、音色继承、节拍内排序）
  - 适配 repository / data / ViewModel / UI（最小改动）
- **关键架构决定**
  - **时间分三层**：`Scene.timeline` 总时长（可空） → `Beat.order` → `PerformanceEvent.timing`（Beat 内可并发）
  - `PresentationMode` 默认 `LiveScene`，且是**领域数据**，UI 只读不判
  - **Dialogue / Utterance / SpeechParams 三层职责分离**（剧情事实 / 演出指令 / 数值参数）
  - 资源引用统一为 `AssetRef`，不用裸字符串
- **测试结果**：无（Phase 2 未要求新增测试）
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL
- **遗留问题**：无

---

## Phase 3 — 本地解析闭环

- **目标**：建立「小说原文 → AI 结构化 DTO → 校验 → 归一化 → Domain Model」的**本地闭环**（不连真实网络）。
- **主要产出**
  - `data/parser/dto/`：`ParseResponseDto`、`CharacterDto`、`SceneDto`+`SceneSettingDto`、`BeatDto`、`PerformanceEventDto`（sealed + 6 子类 + `UnknownEventDto`）、`CommonDto`（SourceSpan/Timing/SpeechParams/PresentationEvidence）
  - `data/parser/validation/`：`ParseValidator`、`ValidationResult` / `ValidationIssue` / `ValidationCode` / `ValidationSeverity`
  - `data/parser/mapping/`：`AiParseMapper`
  - `data/parser/sample/`：`SampleParseSources`（从 classpath 读样例）
  - `StoryParsePipeline`、`ParseSchema`（`CURRENT = "1.0"`）、`ParseJson`
  - `repository/StoryImportRepository`、`data/repository/LocalSampleStoryImportRepository`、`domain/model/StoryContent`
  - 两份假 AI 响应 JSON + 两份章节原文（`app/src/main/resources/parser/`）
- **关键架构决定**
  - **DTO 完全独立于 domain**，网络/解析结果不得直接构造领域对象
  - Validator **只报告不修改**；有 ERROR 时管线**不映射**，保证错误输出不污染领域
  - Mapper **自己再检查**非 LiveScene 的 `presentationEvidence`，缺失则降级 + 告警
  - `tempId` → 稳定 id；场景 id 必须按章节限定（否则跨章节 tempId 冲突导致 `beatsByScene` 互相覆盖）
- **测试结果**：`./gradlew test` → **26 用例，0 失败**（管线 10 + 校验 13 + 导入仓库 3）
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL
- **遗留问题**：无（结构验收通过）

---

## Phase 4 — 接入真实 DeepSeek

- **目标**：把 Phase 3 的「本地样例」换成「真实 DeepSeek 返回」，证明 `原文 → Prompt → DeepSeek → JSON → DTO → 校验 → 映射 → StoryContent → UI` 可跑通。
- **主要产出**
  - `data/remote/deepseek/`：`DeepSeekConfig`（脱敏 toString）、`DeepSeekWireDto`、`DeepSeekApiClient` + `DeepSeekApiResult`、`OkHttpDeepSeekApiClient`、`PromptBuilder`、`DeepSeekStoryParser`、`DeepSeekLogger`
  - `data/repository/RemoteStoryImportRepository`、`StoryContentStore`
  - `repository/StoryImportRequest`、`StoryImportResult`（Success / Partial / Failure）
  - `di/AppConfig`（`LOCAL_SAMPLE` / `REMOTE_DEEPSEEK` 两种模式）
  - `AndroidManifest` 增加 `INTERNET` 权限
- **关键架构决定**
  - 网络层**只做 HTTP**，不解析业务 JSON、不接触 domain；校验 + 映射仍走**唯一一条** `StoryParsePipeline`（新增 DTO 重载）
  - `importStory()` 改为 `suspend`，不再用阻塞伪装异步
  - **API Key 不进源码 / BuildConfig / APK / 资源**，只从环境变量或系统属性读取；日志只出现 `****(len=NN)`
  - **不接受 Markdown 包裹的响应**（明确失败，不偷偷剥离代码围栏）
  - 导入结果三态：仅 warning → `Partial`（有内容 + 告警），有 error → `Failure`（不产出领域模型）
  - 启动时在 Application 触发一次导入，失败只记日志、不崩溃
- **测试结果**：`./gradlew test` → **59 用例，0 失败，1 跳过**（真实调用测试因无 API Key 被 `Assume` 跳过）
  - 其中 Phase 3 的 26 个用例**一个没少、全部通过**
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，无 Kotlin 编译警告
- **遗留问题**
  1. **真实 DeepSeek 成功调用 BLOCKED** —— 后续专门做了一次「最小真实 API 验收」（只读链路检查 + 环境确认），结论：**链路各层齐备**（`/chat/completions` → `DeepSeekChatResponse` → `choices[0].message.content` → `ParseJson` → `ParseResponseDto` → `ParseValidator` → `AiParseMapper` → `StoryContent`），但**环境无有效 API Key**（环境变量 / 系统属性 / `local.properties` / `~/.aicode` 均无），因此**未发出携带有效凭据的请求、未得到 HTTP 200、未产生真实 `StoryContent`**。失败层级 = **认证层（缺凭据）**。现有 HTTP 401 验证只证明请求可达服务端。
  2. 远程模式在 App 内无法完成鉴权（无 Key 注入方式，也没有密钥输入 UI）
  3. 层级小瑕疵：`repository/StoryImportResult` 引用了 `data.parser.validation.ValidationResult`（接口层依赖了数据层类型）
  4. 真实模型算错 offset 时，`INVALID_SOURCE_SPAN_RANGE` 是 **ERROR**，会导致整章导入失败（严格，**未放宽**）
  5. 远程模式输入原文仍是内置样例，没有「用户导入原文」入口
  6. 导入成功 / 失败只在 logcat 可见，UI 未呈现状态
- **Phase 4 封存清单（finalization）**
  - Implementation: **COMPLETE**
  - Unit tests: **PASS** —— 59 tests / 0 failures / 0 errors / 1 skipped；skipped = `DeepSeekLiveIntegrationTest`
  - Build: **PASS** —— `assembleDebug` BUILD SUCCESSFUL
  - Real API verification: **BLOCKED / DEFERRED**
  - Reason: **no valid DeepSeek API credential available**
  - Code defect discovered during this verification: **NONE** —— 未验证项属于「真实 API 验证延期」，不是已发现的代码缺陷
  - HTTP 401 endpoint connectivity verification: 仍然有效，但它**仅证明**「端点可达 + 请求构造正确」，**不代表 API 调用成功**
- **封存判定**：**实现层面 CLOSED**；真实 API 验证状态 = **blocked / deferred**，待拿到合法 Key 后执行 `DeepSeekLiveIntegrationTest` 补齐。

---

## Phase 5A — Story Import + Parsed Story Explorer UI

- **成果**：把 Phase 4 产出的 `StoryContent` 真正展示到 App UI。
  链路：`小说原文 → Import → StoryImportRepository → StoryContent → ViewModel → Compose UI → Story → Chapter → Scene → Beat → PerformanceEvent`
- **改动文件**
  - 新增 `ui/storyimport/ImportScreen.kt`（导入页）、`ui/explorer/StoryExplorerScreen.kt`（解析结果页）
  - 新增 `viewmodel/ImportViewModel.kt`（五态导入状态机）、`viewmodel/StoryExplorerViewModel.kt`（展示树）
  - 修改 `navigation/Destinations.kt`、`navigation/AppNavHost.kt`（新增 `import` / `explorer` 路由）
  - 修改 `ui/novel/NovelScreen.kt`（新增「导入原文并解析」入口）
  - 修改 `di/AppContainer.kt`（两种模式各持一个既有 `StoryImportRepository`；`importStory(mode, novelText)` 统一协调并写入内容仓库；新增 `sampleNovelText`）
  - 修改 `res/values/strings.xml`
  - 新增测试 `viewmodel/ImportViewModelTest.kt`、`viewmodel/StoryExplorerViewModelTest.kt`
- **架构保持**：UI → ViewModel → Repository → Parser / Remote。**未修改** Domain / DTO / Validator / Mapper / DeepSeek remote client / PromptBuilder。**未新增架构层**（未新增第二套 Repository / Provider / 网络层）。
- **UI 完成范围**
  - 原文多行输入、填充示例原文、开始解析
  - 解析模式：Local Sample / DeepSeek（复用 `AppConfig` 与既有 `StoryImportRepository`，未建第二套解析入口）
  - 状态展示：Idle / Importing / Success / Partial（含告警）/ Failure（含 reason + message）
  - 解析结果：Story → Chapter → Scene（title、PresentationMode、判定依据、setting、参与角色、节拍数）→ Beat（order）→ PerformanceEvent（六类）
  - DialogueEvent 额外展示说话者 / 情绪 / 语气风格 / 对谁 / 内心独白 / 语言 / 发声参数 / 音色覆盖
  - SourceSpan 展示 snippet 与字符区间；事件 timing 展示起止
- **DeepSeek 无 Key 行为**：选择 DeepSeek 且无 Key 时，由 `RemoteStoryImportRepository` 返回 `Failure(MISSING_API_KEY)`，UI 显示明确失败；**未**硬编码 Key、**未**修改认证逻辑、**未**回退冒充成功。
- **测试结果**：`./gradlew test` → **73 用例，0 失败，0 错误，1 跳过**（跳过 = `DeepSeekLiveIntegrationTest`，无 Key）
  - Phase 3 / Phase 4 既有 59 个用例一个没少、全部通过；新增 14 个（ImportViewModel 8 + StoryExplorerViewModel 6）
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，无 Kotlin 编译警告
- **越界检查**：Room / DataStore / TTS / `MediaPlayer` / `ExoPlayer` / `media3` / `ffmpeg` / 登录 / 账号 / 数据库 —— 均**未引入**（`DataStore` 唯一命中是 Phase 1 遗留的一句注释）
- **遗留问题**
  1. 用户粘贴的原文暂时归到内置第一章（`SampleStoryData.CHAPTER_ID_1`），未实现「按用户原文新建章节」
  2. 无 Compose / instrumentation UI 测试（项目尚无 `androidTest` 源集）；UI 展示内容由 ViewModel 展示模型单测覆盖
  3. Phase 4 的真实 DeepSeek 验证仍 BLOCKED / DEFERRED（无有效 Key），本阶段未改变该状态
  4. 启动预载（`AIChatNovelApplication`）仍按 Phase 4 行为执行一次内置样例导入，本次**未改动**

---

## 未开始

Phase 5B 及以后：**尚未开始，等待项目负责人确认。**
