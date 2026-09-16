# AIChatNovel — 项目规则

本文件每轮自动注入，**必须保持简短**。详细内容一律放 `docs/ai/`。

## 项目定位

把小说原文变成「多角色沉浸式剧情演出」：解析剧情 → 角色表演 → 语音 → 视觉 → 时间轴 → MP4。
核心层级：`Story → Chapter → Scene → Beat → PerformanceEvent`。

## 绝对红线

1. **本项目不是即时通讯软件。** 聊天式 UI 只是某些小说剧情的表现形式。
2. `PresentationMode` 默认 `LiveScene`；只有原文**明确写出**即时通讯媒介才允许 `InstantMessaging`，且必须带 `presentationEvidence`。
   **不得**因为「多人轮流讲话 / 对话很多 / 界面像聊天」判定为聊天。
3. AI 输出**不得**直接进入 Domain，必须走 `DTO → Validator → Mapper → Domain`。
4. 不得为了让测试通过而降低验证标准；不得为了让 AI 输出更容易通过而放宽 Validator。
5. API Key 不得写进源码 / BuildConfig / APK / 资源 / 日志。
6. 已验收的 Phase 1–4 结构不要无理由重写；确需修改必须先说明原因。

## 开工前：按顺序读文档（不要扫描整个项目来重建上下文）

1. `docs/ai/PROJECT_BASELINE.md`
2. `docs/ai/ARCHITECTURE_RULES.md`
3. `docs/ai/DOMAIN_CONTRACT.md`
4. `docs/ai/CURRENT_PHASE.md`
5. 按任务需要**按需**读源码
6. 需要历史时再读 `docs/ai/PHASE_LOG.md`

## 构建与测试（容器内已验证）

```bash
cd ~/workspace
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew test
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 ./gradlew assembleDebug
```

## 阶段开发规则

- **只做当前阶段要求的事**，不顺手实现后续阶段功能；需要时先记录，等排期。
- 阶段结束后**不要自动进入下一阶段**，按固定流程收尾：跑测试 → 跑构建 → 把**真实结果**写入 `docs/ai/PHASE_LOG.md` → 更新 `docs/ai/CURRENT_PHASE.md` → 契约/规则有变再更新对应文档 → 更新相关 Project Memory → 最后才考虑 `/compress`。
- `/compress` 是**会话上下文压缩工具**，不是项目总结工具；只在阶段边界用，不要因为「累计 token 大」就频繁压缩。

## 上下文卫生

- 源码不是长期记忆：需要时再 `readFile`，不要为了「记住整个项目」一次性读全部源码。
- 长输出（Gradle 日志、JSON）交给工具输出存储；文档里只记命令、结论与关键错误。
- 分工：`docs/ai/` = 结构化长期知识；`.aicode/memory/` = 少量事实索引；`AGENTS.md` = 每轮注入的短规则；源码 = 真实实现；Git / Checkpoint = 代码历史。
