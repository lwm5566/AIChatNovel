package com.aichatnovel.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aichatnovel.app.navigation.AppNavHost
import com.aichatnovel.app.ui.theme.AIChatNovelTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIChatNovelTheme {
                AppNavHost()
            }
        }
    }
}
