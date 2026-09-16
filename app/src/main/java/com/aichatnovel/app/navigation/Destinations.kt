package com.aichatnovel.app.navigation

/**
 * 全部导航目的地与路由构造。
 *
 * 场景页与剧情演出页的参数可选：从首页进入时携带空参数，由 ViewModel 回退到默认数据；
 * 从小说页 / 场景页进入时携带具体 id。
 */
object Destinations {

    const val HOME = "home"
    const val NOVEL = "novel"
    const val CHARACTERS = "characters"
    const val SCENES = "scenes"
    const val STORY_PLAY = "story_play"
    const val SETTINGS = "settings"
    const val IMPORT = "import"
    const val EXPLORER = "explorer"

    const val ARG_CHAPTER_ID = "chapterId"
    const val ARG_SCENE_ID = "sceneId"

    const val SCENES_ROUTE = "$SCENES?$ARG_CHAPTER_ID={$ARG_CHAPTER_ID}"
    const val STORY_PLAY_ROUTE = "$STORY_PLAY?$ARG_SCENE_ID={$ARG_SCENE_ID}"

    fun scenes(chapterId: String? = null): String =
        if (chapterId.isNullOrBlank()) SCENES else "$SCENES?$ARG_CHAPTER_ID=$chapterId"

    fun storyPlay(sceneId: String? = null): String =
        if (sceneId.isNullOrBlank()) STORY_PLAY else "$STORY_PLAY?$ARG_SCENE_ID=$sceneId"
}
