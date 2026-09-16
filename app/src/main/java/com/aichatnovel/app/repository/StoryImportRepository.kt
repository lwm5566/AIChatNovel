package com.aichatnovel.app.repository

/**
 * 小说导入入口：把「小说原文 + AI 结构化响应」转换成领域模型。
 *
 * 两种实现：
 * - `LocalSampleStoryImportRepository`：读内置假 JSON（离线、回归测试、无 Key 时的开发模式）；
 * - `RemoteStoryImportRepository`：真实调用 DeepSeek（Prompt → HTTP → DTO → 校验 → 映射）。
 *
 * 方法为 suspend：远程实现要发起网络请求，不能靠阻塞伪装异步。
 * 无论哪种实现，都**必须**经过 ParseValidator 与 AiParseMapper。
 */
interface StoryImportRepository {

    suspend fun importStory(request: StoryImportRequest): StoryImportResult
}
