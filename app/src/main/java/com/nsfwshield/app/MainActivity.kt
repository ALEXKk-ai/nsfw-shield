package com.nsfwshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nsfwshield.app.ui.navigation.AppNavigation
import com.nsfwshield.app.ui.theme.NSFWShieldTheme
import com.nsfwshield.app.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

/**
 * Single-activity host for NSFW Shield.
 * All UI is rendered via Jetpack Compose with Navigation.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the splash screen before super.onCreate
        val splashScreen = installSplashScreen()
        var keepSplashScreen = true

        super.onCreate(savedInstanceState)
        
        // Keep the splash screen on-screen until our data is ready
        splashScreen.setKeepOnScreenCondition { keepSplashScreen }
        
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val isDarkMode by viewModel.darkMode.collectAsState(initial = true)
            val onboardingCompleted by viewModel.onboardingCompleted.collectAsState(initial = null)

            // Once onboardingCompleted is loaded from DataStore (not null),
            // wait a tiny bit extra to let the icon shine, then drop the slash screen.
            androidx.compose.runtime.LaunchedEffect(onboardingCompleted) {
                if (onboardingCompleted != null) {
                    delay(500) // Artificial delay for aesthetic "slow" load as requested
                    keepSplashScreen = false
                }
            }

            NSFWShieldTheme(darkTheme = isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }
}
