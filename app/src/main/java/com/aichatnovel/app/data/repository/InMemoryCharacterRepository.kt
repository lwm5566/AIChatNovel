package com.aichatnovel.app.data.repository

import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.repository.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 基于内存的 [CharacterRepository] 实现，数据来自 [StoryContentStore]。
 */
class InMemoryCharacterRepository(
    private val store: StoryContentStore,
) : CharacterRepository {

    override fun observeCharacters(storyId: String): Flow<List<Character>> =
        store.content.map { content -> content.characters.filter { it.storyId == storyId } }

    override fun observeAllCharacters(): Flow<List<Character>> =
        store.content.map { content -> content.characters }
}
