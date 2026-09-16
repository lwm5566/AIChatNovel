package com.aichatnovel.app.data.parser

/**
 * AI 解析结果的 schema 版本约定。
 *
 * 版本号只在这里定义一次，校验层通过它判断是否支持，Mapper 不持有版本常量。
 * 未来 schema 升级时，在这里加入新版本，并在 Mapper 中补上对应的归一化规则。
 */
object ParseSchema {

    /** 当前实现的版本。 */
    const val CURRENT = "1.0"

    /** 支持的版本集合。 */
    val SUPPORTED: Set<String> = setOf(CURRENT)
}
