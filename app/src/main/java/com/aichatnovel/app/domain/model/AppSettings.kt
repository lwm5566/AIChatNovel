package com.aichatnovel.app.domain.model

/**
 * 应用级设置。当前阶段为占位项，用于打通「设置界面 -> Repository -> 持久化」这条链路。
 */
data class AppSettings(
    val showNarration: Boolean = true,
    val autoAdvanceScenes: Boolean = false,
)
