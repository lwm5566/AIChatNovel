# ARCHITECTURE RULES

> **不可随意违反**的架构规则。违反前必须先说明理由并取得项目负责人同意。
> 最后更新：Phase 4 完成时。

## 一、边界规则（1–10）

1. **DeepSeek 不属于 Domain。** 网络层、请求/响应对象、Provider 概念都不得出现在 `domain/` 中。
2. **Remote API 不得直接构造 Domain。** 任何网络结果都必须先落成 DTO，再走校验与映射。
3. **DTO 与 Domain 必须保持隔离。** DTO 不复用任何 domain 类型；domain 不依赖 DTO。
4. **Validator 不修改 DTO。** 它只产出 `ValidationResult{errors, warnings}`，不改数据、不做降级。
5. **Mapper 负责 DTO → Domain。** 这是**唯一**的映射点；消歧、补默认值、降级都只在这里做。
6. **AI 不负责最终 TTS duration。** `duration` 允许为空；合成完成后才把 `DurationSource` 从 `Estimated` 改为 `Audio`。
7. **AI 不负责最终音频资源。** 不得让模型产出音频 URL / 文件路径。
8. **AI 不负责最终视频资源。** 同理，不得产出视频 URL / 资源路径。
9. **Repository 不应该重复实现 Mapper。** 远程实现只做流程协调，校验 + 映射一律复用同一条管线。
10. **UI 不应该直接调用 DeepSeek API。** 网络调用只能经 Repository / 数据层，UI 只消费 `StateFlow`。

## 二、语义规则（11–14）

11. **PresentationMode 必须遵守剧情证据。** 默认 `LiveScene`，只有原文明确写出媒介才可使用其它值。
12. **InstantMessaging 必须有 presentationEvidence。** 缺失时 Validator 告警 + Mapper 降级，**不得静默接受**。
13. **不得因为「多人轮流讲话」而判断为聊天。** 对白多、轮流说话、界面上像聊天，都**不构成** `InstantMessaging` 的依据。
14. **SourceSpan 必须尽量能够回到原文。** `startOffset/endOffset` 是真实字符位置，`snippet` 必须与区间逐字一致；不确定就留空，不要编造。

## 三、变更纪律（15–18）

15. **已经验收通过的阶段不能无理由重写。** Phase 1–4 的 DTO / Validator / Mapper / Domain 结构已验收，保持稳定。
16. **如果下一阶段确实需要修改已验收结构，必须先说明原因。** 写清「为什么必须改 / 不改的后果 / 影响范围」，得到确认后再动。
17. **不允许为了让测试通过而降低验证标准。** 测试是契约的守卫，不是障碍。
18. **不允许为了让 AI 输出更容易通过而放宽 Validator。** 模型输出不符合契约时，**记录问题**（例如「模型 offset 不准」），而不是改校验去迁就。

## 四、未来大方向

```
小说原文
  → Scene          （场景划分）
  → Beat           （演出节拍）
  → PerformanceEvent（对白 / 旁白 / 动作 / 环境 / 音效 / 镜头）
  → Timeline       （统一时间轴）
  → TTS / Visual   （语音合成 / 画面演出）
  → MP4            （成片导出）
```

这条链路是**分层推进**的：每一层只依赖它上面的层，AI 只负责最上层的「小说理解」。

## 五、当前明确禁止（Phase 4 结束时）

Room、DataStore、TTS、音频播放、视频生成、MP4 导出与编码、登录 / 账号、云端数据库、图片生成、角色声音绑定 UI、Timeline 播放器。

超出「当前阶段目标」的功能不要顺手实现；需要时先记录，等排期。
