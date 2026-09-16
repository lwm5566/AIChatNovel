package com.aichatnovel.app.ui.components

import com.aichatnovel.app.domain.model.PresentationMode

/** 呈现介质的中文展示名。仅用于 UI，不参与任何判定。 */
fun presentationModeLabel(mode: PresentationMode): String = when (mode) {
    PresentationMode.LiveScene -> "现场场景"
    PresentationMode.InstantMessaging -> "即时通讯"
    PresentationMode.PhoneCall -> "语音通话"
    PresentationMode.Letter -> "书信 / 书面"
    PresentationMode.RecallOrFlashback -> "回忆 / 闪回"
}
