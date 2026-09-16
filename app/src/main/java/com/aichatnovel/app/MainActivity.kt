package com.aichatnovel.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aichatnovel.app.navigation.AppNavHost
import com.aichatnovel.app.ui.theme.AIChatNovelTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 界面固定使用深色主题，系统栏同样按「深色背景 + 浅色图标」配置，
        // 否则系统为浅色时状态栏图标会与深色页面背景撞在一起。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            AIChatNovelTheme {
                AppNavHost()
            }
        }
    }
}
