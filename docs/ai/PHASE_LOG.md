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

## Phase 5A 修复轮（审核整改）

- **背景**：Phase 5A 审核发现 M1 / M3 / m1 / m2，本轮只修这四项；Phase 5A **未宣布 COMPLETE**。
- **改动文件**（仅授权的 8 个）
  - `viewmodel/ImportViewModel.kt`（M1 + m2）、`ui/storyimport/ImportScreen.kt`（m2）
  - `ui/explorer/StoryExplorerScreen.kt`（M3）、`viewmodel/StoryExplorerViewModel.kt`（m1）
  - `test/viewmodel/ImportViewModelTest.kt`、`test/viewmodel/StoryExplorerViewModelTest.kt`、`test/ui/explorer/StoryExplorerKeysTest.kt`（新增）、`test/di/AppContainerTest.kt`
- **M1（已修）**：`onNovelTextChange` / `onModeChange` / `useSampleText` 改为 `idleUnlessImporting()` —— `Importing` 期间保持 `Importing`；`import()` 的 `Importing` 置位提到 `launch` 之前，不再依赖 `Main.immediate` 的隐含语义。因此：导入期间重复点击被拦下、不可能并发第二个 `importStory`、不存在旧请求覆盖新请求。**未新增并发框架**，Repository / ViewModel 架构未变。
- **M3（已修）**：Explorer 的 Beat / Event UI key 限定到场景（及节拍）范围（`beatUiKey` / `eventUiKey`）。仅改 Phase 5A 的 `StoryExplorerScreen`；`StoryPlayScreen`（Phase 2 已验收）**未修改**，也未做全项目 ID 重构。
- **m1（已修）**：Scene 总时长在 UI 上追加 duration source（`5.0s（Estimated）`），与事件 timing 写法一致，不再把估计时长当作最终 TTS 时长。Domain / Mapper **未改**。
- **m2（已修）**：`ImportStatus.Failure` 新增 `errors: List<String>`，由 `validation.errors` 映射；ImportScreen 的失败卡片展示「校验错误」摘要（如 `INVALID_SOURCE_SPAN_RANGE@路径：消息`）。**Validator 规则与 ERROR/WARNING 标准未改**。
- **M2（保留）**：用户导入的原文仍显示 `SampleStoryData` 的静态作品 / 章节元信息 —— 审核确认保留为下一阶段遗留，本轮**未修**。
- **测试结果**：`./gradlew test --rerun` → **85 用例，0 失败，0 错误，1 跳过**（跳过 = `DeepSeekLiveIntegrationTest`，无 Key）
  - 上一轮 73 → 85（+12）：ImportViewModelTest 8→14、StoryExplorerViewModelTest 6→7、StoryExplorerKeysTest 0→3（新增）、AppContainerTest 2→4
  - 新增覆盖：导入期间编辑文本 / 切模式 / 填充示例文本不解除 Importing；导入期间重复点击不产生第二次导入；Failure 保留 validation errors；总时长标注来源；跨 Scene 复用相同 beat/event id 时 UI key 仍唯一；`AppContainer.importStory(mode, novelText)` 真实调用与失败不覆盖已有内容
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，无 Kotlin 编译警告
- **越界检查**：`git status` 仅列出上述 8 个文件；Domain / DTO / Parser / Remote / `StoryPlayScreen` / Navigation / 构建配置 / `AIChatNovelApplication` 均**未改动**
- **Git**：未 commit、未 push，保持 HEAD = `a4a9348`

---

## Phase 5B-1 — 当前导入归属隔离（修复 M2）

- **背景**：Phase 5A 审核确认的 **M2** —— 任意导入后 Story / Chapter 元信息仍来自 `SampleStoryData`。本阶段只做归属隔离，**不做真实 metadata**。
- **根因（审计结论）**：Story / Chapter 的展示 metadata 与 `StoryContent` **完全分离**：
  `StoryContent` 不含 Story/Chapter；`ParseResponseDto` 也没有 title / author / chapterTitle / chapterIndex；
  `InMemoryStoryRepository` 只能用静态常量兜底，`AppContainer` 又把 `SampleStoryData.STORY_ID` / `CHAPTER_ID_1` 当作任意导入的归属 id。
- **改动文件**
  - 新增 `di/ImportIdGenerator.kt`（`ImportIdGenerator` + `SequentialImportIdGenerator`）
  - `data/repository/StoryContentStore.kt`：新增 `ImportedStory(story, chapters, content)`；store 持有当前导入快照；`content` 与 `imported` 同一写入点保持同步
  - `data/repository/InMemoryStoryRepository.kt`：`observeStories` / `observeChapters` 改为从快照派生；构造函数去掉静态 Story/Chapter 列表
  - `data/repository/SampleStoryData.kt`：KDoc 明确它只是 **sample fixture**
  - `di/AppContainer.kt`：每次导入取全新 storyId/chapterId；成功后组装并写入 `ImportedStory`；不再引用 `SampleStoryData`
  - `test/di/AppContainerTest.kt`、`test/viewmodel/StoryExplorerViewModelTest.kt`：新增/调整用例
- **ImportedStory 结构**：`ImportedStory(story: Story, chapters: List<Chapter>, content: StoryContent)`；Store 只有一个槽位（当前导入），**无历史、无多作品管理**。
- **Story / Chapter ID 策略（Review Gate 修正后的最终版）**：**ownership 由导入方决定，不由模型回显决定**。
  - Remote（普通导入，语义 = 一章原文）：`request.storyId` / `request.chapterId` 是唯一 authoritative ID；`chapters` 固定为一个 `Chapter(id = request.chapterId, storyId = request.storyId, index = 1)`；content **无条件归一**（`characters.map { it.copy(storyId = request.storyId) }`、`scenes.map { it.copy(chapterId = request.chapterId) }`）——不是“不一致才修”，而是明确以 request 为准。
  - Local Sample：fixture 忽略请求参数，storyId / chapterIds 取自样例内容自身声明，保留两章、content 不做不必要修改。
  - 模型回显的 id 仅属解析协议，不作 ownership 依据。不重建 `Scene.id`、不动 `beatsByScene` key、不改 `SourceSpan.chapterId`。
  - 归一化只在 `AppContainer` 协调层完成（copy 不可变值对象），**未修改** `domain/` / DTO / Mapper / Remote / Prompt / Schema。
- **归属权威性（Review Gate 最终版）**：归属**由导入方决定** —— Remote 使用 `request.storyId` / `request.chapterId`；Local Sample 使用样例自身声明的归属。模型回显的 id 只属解析协议，**不作** ownership 依据。（此 bullet 曾有一段被 Review Gate 推翻的旧表述，已修正。）
- **Sample fixture 处理**：`SampleStoryData` 降级为 fixture（仅测试与样例说明使用）；本地样例仍保留自己的 `story-1` / `chapter-1` / `chapter-2`，但这些 id **不再**成为普通导入的默认 id。
- **placeholder metadata**：`Story.title = "未命名作品"`、`author = "未命名作者"`、`synopsis = ""`、`Chapter.title = "未命名章节"`、`Chapter.index` = 章节出现顺序。**不是小说真实 metadata**，将在 Phase 5B-2 由用户显式提供的信息替换（代码注释与 `CURRENT_PHASE.md` 均已标明）。
- **Failure / Partial / Importing（未变）**：Success / Partial → 写入快照；Failure → 不写入（首次失败仍无 Story，后续失败保留上一次成功快照）；Importing → 旧快照继续可见。
- **启动预载**：`AIChatNovelApplication` **未改动**，它调用的 `importStory()` 走的仍是同一套「当前导入」写入路径，不再出现「Story metadata 一套、Content 另一套」的混合状态。
- **Explorer**：**未修改**。`StoryExplorerViewModel` 仍只读 Repository；Repository 修好后 Story / Chapter 自动来自当前导入快照。
- **测试结果**：`./gradlew test --rerun` → **97 用例，0 失败，0 错误，1 跳过**（跳过 = `DeepSeekLiveIntegrationTest`，无 Key）
  - 上一轮 85 → 97（+12）：`AppContainerTest` 4→14、`StoryExplorerViewModelTest` 7→9
  - 新增覆盖：模型返回**错误 storyId / chapterId** 时 ownership 仍为 request 的 authoritative 值；所有 character 归到权威 storyId；所有 scene 归到权威 chapterId 且属于本次导入的 chapters；连续两次 Remote 导入 id 不同且 content 不串；本地样例保留 `story-1` + `chapter-1`/`chapter-2` 两章与正确 index；未导入时 Story/Chapter 为空；Partial 替换快照；Failure 不动快照；**导入进行中旧快照继续可见**（MockWebServer 延迟 + 并发）；Explorer 能把 Story / Chapter / Scene / Beat 正确关联
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，无 Kotlin 编译警告
- **越界检查**：`git status` 仅 7 个文件（6 改 + 1 新增）；Domain / DTO / Validator / Mapper / Remote / PromptBuilder / ParseSchema / UI / Navigation / Gradle **均未改动**
- **Git**：已 commit `9d5917752dd7600fe1fc9da6f4008d513f5397af`（父 `f30208b`）；**尚未 push**
- **遗留**：真实 Story / Chapter metadata（Phase 5B-2）；多次导入只保留当前快照（符合本阶段要求）

---

## Phase 5B-2 — 用户显式提供的作品 / 章节元信息

- **背景**：Phase 5B-1 把 Story / Chapter 的归属做对了，但它们的**内容**仍是硬编码占位值（Remote 路径显示「未命名作品 / 未命名作者 / 未命名章节」），直接暴露在 `NovelScreen` / `StoryExplorerScreen` 上。
- **最终设计裁决（用户批准）**
  - **D1** 采用**方案 α：用户显式输入**。禁止 AI 生成 metadata；**不修改** parser / DTO / Prompt / Schema。
  - **D2** 直接扩展 `StoryImportRequest`（新增 `storyTitle` / `author` / `synopsis` / `chapterTitle`）；**不新建** `di/ImportMetadata`。
  - **D3** 采用 **α-2**：Local Sample **保留 fixture 语义、继续忽略 request**，不得因本阶段开始读取 request 中的 metadata。
  - **D4** trim 后为空视为未填写 → 回退到现有占位值（`未命名作品` / `未命名作者` / `""` / `未命名章节`）；**不**根据 `chapter.index` 生成「第 N 章」；**不**把空白字符串写入 Domain。
  - **D5** 一次支持四项（作品名 / 作者 / 作品简介 / 章节名），作者与简介可为空。
  - **D6** 明确排除：导入后编辑 metadata、metadata 编辑页、多作品历史、metadata 历史版本、独立作品管理、AI 自动生成 metadata。
- **改动文件**（严格限定 5 个生产 + 2 个测试）
  - `repository/StoryImportRequest.kt`：+4 个可空元信息字段
  - `viewmodel/ImportViewModel.kt`：`ImportUiState` +4 字段与对应事件；导入时随请求下发（编辑元信息同样不解除 `Importing`）
  - `ui/storyimport/ImportScreen.kt`：新增「作品与章节信息」输入区 + 留空说明 + 本地样例模式提示
  - `res/values/strings.xml`：新增 7 条文案
  - `di/AppContainer.kt`：`importStory(...)` 接受元信息；Remote 分支使用用户输入（空则占位），Local 分支**忽略** request 元信息；新增 `String?.orPlaceholder()`
  - `test/di/AppContainerTest.kt`、`test/viewmodel/ImportViewModelTest.kt`：新增/调整用例
- **metadata 与 ownership 的分离**：metadata 只决定展示字段，不参与 id 的生成 / 选择 / 查找 / 合并；不触碰 `SourceSpan.chapterId`、`Scene.id`、`beatsByScene` key；Remote 继续以 `request.storyId` / `request.chapterId` 为唯一 authoritative 归属。
- **测试结果**：`./gradlew test --rerun` → **109 用例，0 失败，0 错误，1 跳过**（跳过 = `DeepSeekLiveIntegrationTest`，无 Key）
  - 上一轮 97 → 109（+12）：`AppContainerTest` 14→24、`ImportViewModelTest` 14→16
  - 新增覆盖：Remote 完整 metadata 落库；缺失 / 空白 metadata 回退占位值；metadata trim 且保留特殊字符；metadata 不影响 ownership；metadata 与归属归一**都不碰 SourceSpan**；归一化不重建 `Scene.id` / `beatsByScene` key；两次导入 metadata 与 ownership 不串；**Local Sample 明确忽略 request metadata**；Repository 层暴露当前导入的 metadata；ImportViewModel 元信息透传与「导入中编辑不解除 Importing」
- **构建结果**：`assembleDebug` BUILD SUCCESSFUL，无 Kotlin 编译警告
- **越界检查**：`git status` 仅 7 个文件（5 生产 + 2 测试）；Domain / DTO / Validator / Mapper / Remote / PromptBuilder / ParseSchema / StoryContentStore / 三个 InMemory Repository / `ui/explorer` / `ui/novel` / Navigation / Gradle **均未改动**
- **Git**：**未 commit、未 push**，HEAD 仍为 `9d5917752dd7600fe1fc9da6f4008d513f5397af`（Phase 5B-2 改动尚未提交）
- **文档修正（D7）**：修正了本文件中被 5B-1 Review Gate 推翻的旧 ownership bullet，以及过时的 Git 状态。

---

## 未开始

下一阶段：**尚未开始，等待项目负责人确认。**
