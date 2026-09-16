package com.aichatnovel.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichatnovel.app.ui.character.CharacterRoute
import com.aichatnovel.app.ui.explorer.StoryExplorerRoute
import com.aichatnovel.app.ui.home.HomeRoute
import com.aichatnovel.app.ui.novel.NovelRoute
import com.aichatnovel.app.ui.scene.SceneRoute
import com.aichatnovel.app.ui.settings.SettingsRoute
import com.aichatnovel.app.ui.storyimport.ImportRoute
import com.aichatnovel.app.ui.storyplay.StoryPlayRoute

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Destinations.HOME,
    ) {
        composable(Destinations.HOME) {
            HomeRoute(
                onOpenNovel = { navController.navigate(Destinations.NOVEL) },
                onOpenCharacters = { navController.navigate(Destinations.CHARACTERS) },
                onOpenScenes = { navController.navigate(Destinations.scenes()) },
                onOpenStoryPlay = { navController.navigate(Destinations.storyPlay()) },
                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }

        composable(Destinations.NOVEL) {
            NovelRoute(
                onBack = { navController.popBackStack() },
                onChapterClick = { chapterId -> navController.navigate(Destinations.scenes(chapterId)) },
                onOpenImport = { navController.navigate(Destinations.IMPORT) },
            )
        }

        composable(Destinations.IMPORT) {
            ImportRoute(
                onBack = { navController.popBackStack() },
                onOpenExplorer = { navController.navigate(Destinations.EXPLORER) },
            )
        }

        composable(Destinations.EXPLORER) {
            StoryExplorerRoute(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.CHARACTERS) {
            CharacterRoute(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Destinations.SCENES_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_CHAPTER_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments
                ?.getString(Destinations.ARG_CHAPTER_ID)
                ?.ifBlank { null }
            SceneRoute(
                chapterId = chapterId,
                onBack = { navController.popBackStack() },
                onSceneClick = { sceneId -> navController.navigate(Destinations.storyPlay(sceneId)) },
            )
        }

        composable(
            route = Destinations.STORY_PLAY_ROUTE,
            arguments = listOf(
                navArgument(Destinations.ARG_SCENE_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val sceneId = backStackEntry.arguments
                ?.getString(Destinations.ARG_SCENE_ID)
                ?.ifBlank { null }
            StoryPlayRoute(
                sceneId = sceneId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
