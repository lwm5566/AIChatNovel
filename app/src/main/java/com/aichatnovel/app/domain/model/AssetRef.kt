package com.aichatnovel.app.domain.model

/** 资源类型。 */
enum class AssetType {
    IMAGE,
    AUDIO,
    VIDEO,
    VOICE,
    SPRITE,
}

/**
 * 统一资源引用。
 *
 * 领域模型只持有对资源的引用，不持有资源本身，也不关心它来自本地文件、
 * 资源库还是远端服务——[source] 允许为空，表示尚未绑定。
 */
data class AssetRef(
    val id: String,
    val type: AssetType,
    val source: String? = null,
)
