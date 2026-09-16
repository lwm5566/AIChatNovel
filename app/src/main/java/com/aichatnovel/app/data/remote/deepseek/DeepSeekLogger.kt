package com.aichatnovel.app.data.remote.deepseek

/**
 * 各阶段日志。刻意做成很薄的接口：
 * - Android 侧接到 android.util.Log；
 * - 测试侧换成记录器，用来断言「没有打印敏感信息」。
 *
 * 约定：**任何实现都不能打印 Authorization / API Key**。
 */
fun interface DeepSeekLogger {

    fun log(stage: String, message: String)

    companion object {
        const val STAGE_REQUEST = "[DeepSeek] request"
        const val STAGE_RAW_RESPONSE = "[DeepSeek] raw response"
        const val STAGE_JSON_PARSE = "[Parser] JSON parse"
        const val STAGE_VALIDATION = "[Validator] validation"
        const val STAGE_MAPPING = "[Mapper] mapping"

        val NoOp = DeepSeekLogger { _, _ -> }
    }
}
