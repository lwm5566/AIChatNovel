package com.aichatnovel.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichatnovel.app.AIChatNovelApplication
import com.aichatnovel.app.domain.model.Character
import com.aichatnovel.app.repository.CharacterRepository
import com.aichatnovel.app.repository.StoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CharacterUiState(
    val isLoading: Boolean = true,
    val characters: List<Character> = emptyList(),
)

/**
 * 角色页 ViewModel：展示当前作品下的角色列表。
 *
 * 作品尚未选定，暂时取第一部作品；选定作品后，这里替换为按 storyId 查询即可。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterViewModel(
    characterRepository: CharacterRepository,
    storyRepository: StoryRepository,
) : ViewModel() {

    val uiState: StateFlow<CharacterUiState> = storyRepository.observeStories()
        .map { it.firstOrNull()?.id }
        .flatMapLatest { storyId ->
            if (storyId == null) {
                flowOf(CharacterUiState(isLoading = false))
            } else {
                characterRepository.observeCharacters(storyId)
                    .map { CharacterUiState(isLoading = false, characters = it) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CharacterUiState(),
        )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AIChatNovelApplication
                CharacterViewModel(
                    characterRepository = app.container.characterRepository,
                    storyRepository = app.container.storyRepository,
                )
            }
        }
    }
}
