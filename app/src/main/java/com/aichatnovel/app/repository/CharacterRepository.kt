package com.aichatnovel.app.repository

import com.aichatnovel.app.domain.model.Character
import kotlinx.coroutines.flow.Flow

/**
 * 角色数据入口。仅定义接口，具体实现位于 data 层。
 */
interface CharacterRepository {

    fun observeCharacters(storyId: String): Flow<List<Character>>

    /** 供剧情演出等场景按 id 解析说话角色使用。 */
    fun observeAllCharacters(): Flow<List<Character>>
}
