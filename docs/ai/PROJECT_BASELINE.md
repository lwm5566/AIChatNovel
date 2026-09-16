# PROJECT BASELINE

> 项目长期基线。只记录**已经确认的事实**。详细契约见 `DOMAIN_CONTRACT.md`，规则见 `ARCHITECTURE_RULES.md`。
> 最后更新：Phase 4 完成时。

## 项目名称

AIChatNovel（包名 `com.aichatnovel.app`）

## 一句话定位

把小说原文变成**多角色沉浸式剧情演出**：解析剧情 → 角色轮流表演 → 语音 → 视觉 → 时间轴 → MP4。

## 目标流水线

```
小说原文
  → 剧情 / 场景解析
  → 多角色表演
  → 语音（TTS）
  → 视觉（画面 / 视频）
  → 时间轴
  → MP4
```

当前只完成前两步中的「解析」部分，其余为后续阶段目标，**尚未实现**。

## 核心模型层级

```
Story → Chapter → Scene → Beat → PerformanceEvent
```

## PerformanceEvent 的六个变体

| 变体 | 含义 |
|---|---|
| `DialogueEvent` | 角色说话 |
| `NarrationEvent` | 旁白 |
| `ActionEvent` | 角色动作 |
| `EnvironmentEvent` | 环境描述 |
| `SoundEvent` | 环境音 |
| `CameraEvent` | 镜头 / 画面变化 |

## PresentationMode 的五个取值

| 取值 | 含义 |
|---|---|
| `LiveScene` | 现场场景（**默认**）：角色在同一真实空间面对面演出 |
| `InstantMessaging` | 即时通讯：剧情明确发生在微信 / QQ / 短信等聊天软件中 |
| `PhoneCall` | 语音通话 |
| `Letter` | 书信 / 纸条 / 公告等书面媒介 |
| `RecallOrFlashback` | 回忆 / 闪回 / 梦境 |

## 语义红线（最重要）

- **本项目不是即时通讯软件。**
- 聊天式 UI 只是**某些小说剧情的表现形式**，不代表故事发生在微信 / QQ 里。
- 只有当小说剧情**明确写出**角色通过即时通讯媒介交流（例如「拿出手机打开微信给对方发消息」），才允许 `InstantMessaging`。
- **普通面对面对话必须保持 `LiveScene`**，不能因为「多人轮流讲话」「对话很多」「界面像聊天」而判定为聊天。
- `PresentationMode` 是**领域数据**，由解析结果给出，**UI 不负责判定**。

## 当前阶段

见 `CURRENT_PHASE.md`（Phase 4 代码完成，真实 API 验证待做）。
