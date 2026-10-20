package com.simon.youtube

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.simon.youtube.ui.BrowserScreen
import com.simon.youtube.ui.theme.YoutubeTheme

/**
 * Unique activité de l'application : elle installe le thème puis le navigateur
 * kiosque. Toute la navigation se fait ensuite à l'intérieur du WebView et de
 * Compose (panneau de réglages, écran de défi).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YoutubeTheme {
                BrowserScreen()
            }
        }
    }
}
