package com.aichatnovel.app.data.parser.sample

/**
 * 一份内置的假 AI 响应，及其对应的章节原文。
 * 原文用于校验 sourceSpan 的偏移是否真实、snippet 是否与原文一致。
 */
data class SampleParseSource(
    val name: String,
    val responseJson: String,
    val chapterText: String,
)

/**
 * 从 `src/main/resources/parser/` 读取内置样例。
 * 本阶段用它模拟 DeepSeek 返回，不涉及任何网络。
 */
object SampleParseSources {

    private const val DIR = "parser"

    val liveScene: SampleParseSource = load("sample_live_scene", "chapter_live_scene.txt")

    val instantMessaging: SampleParseSource = load("sample_instant_messaging", "chapter_instant_messaging.txt")

    val all: List<SampleParseSource> = listOf(liveScene, instantMessaging)

    private fun load(jsonName: String, textName: String): SampleParseSource = SampleParseSource(
        name = jsonName,
        responseJson = readResource("$jsonName.json"),
        chapterText = readResource(textName),
    )

    private fun readResource(fileName: String): String {
        val stream = SampleParseSources::class.java.classLoader?.getResourceAsStream("$DIR/$fileName")
            ?: error("缺少内置样例资源：$DIR/$fileName")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
