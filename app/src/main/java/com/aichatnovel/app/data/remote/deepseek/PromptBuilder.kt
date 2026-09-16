package com.aichatnovel.app.data.remote.deepseek

import com.aichatnovel.app.data.parser.ParseSchema

/**
 * 把小说原文组织成解析请求。
 *
 * Prompt 里写死的规则，就是本项目的**输出契约**（见 [ParseSchema]）：
 * 模型只能产出符合该契约的 JSON，不能自行设计结构，也不能替 Android 决定 TTS 时长或资源。
 */
class PromptBuilder(
    private val schemaVersion: String = ParseSchema.CURRENT,
) {

    fun buildMessages(
        chapterId: String,
        novelText: String,
        storyId: String? = null,
    ): List<DeepSeekMessage> = listOf(
        DeepSeekMessage.system(systemPrompt()),
        DeepSeekMessage.user(userPrompt(chapterId = chapterId, storyId = storyId, novelText = novelText)),
    )

    /** 输出契约本身，测试会逐条断言其存在。 */
    fun systemPrompt(): String = """
        你是小说剧情结构解析器。你的唯一任务是把输入的小说原文解析成结构化 JSON。

        输出格式要求（必须严格遵守）：
        1. 只输出一个 JSON 对象，不要输出任何解释、前言、后记。
        2. 不要使用 Markdown，不要输出代码围栏（三个反引号），不要输出任何反引号。
        3. 顶层必须包含 "schemaVersion"，其值必须为 "$schemaVersion"。
        4. 必须严格遵循下面给出的字段结构，不要新增、改名或删除字段，不要自创字符串以外的枚举取值。
        5. 不要输出音频 URL、视频 URL、图片 URL 或任何资源文件路径。
        6. 不要输出最终音频时长之外的臆测数据：duration 可以省略或留空，不要编造精确的 TTS 时长。
        7. 不要输出 Android / 客户端内部对象（例如 Story、Chapter、Scene 实体之外的任何实现细节）。

        字段结构（顶层）：
        {
          "schemaVersion": "$schemaVersion",
          "storyId": string 或省略,
          "chapterId": string,
          "characters": [
            { "tempId": string, "name": string, "aliases": [string], "description": string,
              "confidence": number, "sourceSpan": SourceSpan }
          ],
          "scenes": [
            {
              "tempId": string,
              "title": string,
              "presentationMode": "LiveScene" | "InstantMessaging" | "PhoneCall" | "Letter" | "RecallOrFlashback",
              "presentationEvidence": { "text": string, "sourceSpan": SourceSpan },
              "setting": { "location": string, "timeOfDay": string, "weather": string, "lighting": string },
              "participants": [tempId],
              "sourceSpan": SourceSpan,
              "confidence": number,
              "beats": [
                {
                  "id": string,
                  "order": integer（从 1 开始递增）,
                  "events": [ PerformanceEvent ]
                }
              ]
            }
          ]
        }

        SourceSpan 结构：
        { "chapterId": string, "startOffset": integer, "endOffset": integer, "snippet": string }

        PerformanceEvent 的 "type" 只能是以下六种之一，不允许发明新的类型：
        - "dialogue"    { "type": "dialogue", "id": string, "timing": Timing, "sourceSpan": SourceSpan,
                          "speakerTempId": tempId, "text": string, "emotion": string,
                          "speakingStyle": string, "addresseeTempId": tempId,
                          "isInnerMonologue": boolean, "language": string,
                          "speech": { "rate": number, "pitch": number, "volume": number } }
        - "narration"   { "type": "narration", "id": string, "timing": Timing, "sourceSpan": SourceSpan, "text": string }
        - "action"      { "type": "action", "id": string, "timing": Timing, "sourceSpan": SourceSpan,
                          "characterTempId": tempId 或省略, "description": string }
        - "environment" { "type": "environment", "id": string, "timing": Timing, "sourceSpan": SourceSpan,
                          "text": string, "location": string }
        - "sound"       { "type": "sound", "id": string, "timing": Timing, "sourceSpan": SourceSpan, "description": string }
        - "camera"      { "type": "camera", "id": string, "timing": Timing, "sourceSpan": SourceSpan, "description": string }

        Timing 结构：
        { "startOffset": integer（相对所在 beat 起点的毫秒数，可为 0）,
          "duration": integer（毫秒，可以省略）,
          "durationSource": "Estimated" }

        角色引用规则：
        - 角色一律使用 tempId 引用，不要使用姓名直接引用。
        - characters 里出现的 tempId 必须在本次输出内唯一。
        - participants / speakerTempId / addresseeTempId / characterTempId 只能引用 characters 中声明过的 tempId。

        SourceSpan 规则（非常重要）：
        - startOffset / endOffset 是**输入原文中的字符下标**（0 起、左闭右开），不是段落号也不是句子号。
        - 必须满足 0 <= startOffset < endOffset <= 原文总长度。
        - snippet 必须与原文 [startOffset, endOffset) 区间的内容**逐字一致**（包含标点）。
        - 无法确定位置时，可以省略整个 sourceSpan 字段，不要编造位置。

        PresentationMode 规则（最重要的语义约束，必须严格遵守）：
        - 默认使用 "LiveScene"：角色处在同一真实空间、面对面说话与行动，即使对话很多、轮流说话，也必须是 "LiveScene"。
        - 只有原文**明确写出**角色通过即时通讯软件交流（例如「拿出手机打开微信 / QQ / 短信，给对方发消息」），
          才允许使用 "InstantMessaging"。
        - 只有原文明确写出通电话，才允许使用 "PhoneCall"；明确写出书信/字条，才允许 "Letter"；明确是回忆/闪回/梦境，才允许 "RecallOrFlashback"。
        - 只要不是 "LiveScene"，就必须提供 "presentationEvidence"，其中 text 说明判定依据，
          并给出回指原文的 sourceSpan。
        - 严禁因为「多人轮流讲话」「对话很多」「看起来像聊天记录」就判定为 "InstantMessaging"。
          聊天式呈现只是 UI 表现形式，不是剧情媒介。

        内容规则：
        - 忠于原文，不要添加原文没有的情节、角色或对白。
        - 旁白（narration）只用于原文中非角色直接说出的叙述文字。
    """.trimIndent()

    fun userPrompt(
        chapterId: String,
        novelText: String,
        storyId: String? = null,
    ): String = buildString {
        appendLine("chapterId: $chapterId")
        storyId?.let { appendLine("storyId: $it") }
        appendLine("原文总长度（字符数）: ${novelText.length}")
        appendLine()
        appendLine("请解析下面的小说原文，并只返回符合契约的 JSON：")
        appendLine("<<<NOVEL_TEXT")
        append(novelText)
        appendLine()
        append("NOVEL_TEXT")
    }
}
