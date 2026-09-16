package com.aichatnovel.app.data.parser

import kotlinx.serialization.json.Json

/**
 * 解析 AI 响应用的 JSON 配置。
 *
 * [Json.ignoreUnknownKeys] 必须开启：事件 DTO 采用按内容选择子类型的策略，
 * 判别用的 `type` 字段本身不是 DTO 属性。
 */
internal object ParseJson {

    val instance: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }
}
