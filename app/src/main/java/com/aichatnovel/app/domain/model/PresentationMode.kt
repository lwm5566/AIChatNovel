package com.aichatnovel.app.domain.model

/**
 * 剧情的呈现介质，即「这段剧情是以什么方式发生的」。
 *
 * 这是本项目的核心语义约束：**它由剧情解析结果决定，绝不能根据 UI 或对白形式猜测**。
 * 「两个人轮流说话」「对白很多」都不等于 [InstantMessaging]。
 * 只有当原文明确写明角色通过即时通讯工具交流（例如「他拿出手机，打开微信，给她发消息」），
 * 才使用 [InstantMessaging]。
 *
 * 默认值为 [LiveScene]，即绝大多数小说剧情：角色在真实空间中面对面演出。
 */
enum class PresentationMode {

    /** 现场场景：角色处于同一真实空间，面对面说话、行动。默认值。 */
    LiveScene,

    /** 即时通讯：剧情明确发生在微信 / QQ / 短信等聊天软件中。 */
    InstantMessaging,

    /** 语音通话：角色通过电话 / 语音通话交流。 */
    PhoneCall,

    /** 书信 / 字条 / 公告等书面媒介。 */
    Letter,

    /** 回忆 / 闪回 / 梦境。 */
    RecallOrFlashback,
}

/**
 * 呈现介质的判定依据。
 *
 * 非 [PresentationMode.LiveScene] 的场景必须提供：它说明「AI 为什么认为这段剧情是这种媒介」，
 * 并指回原文。它既是可追溯性的一部分，也是防止错误判定污染领域模型的凭据。
 */
data class PresentationEvidence(
    val text: String,
    val sourceSpan: SourceSpan? = null,
)
