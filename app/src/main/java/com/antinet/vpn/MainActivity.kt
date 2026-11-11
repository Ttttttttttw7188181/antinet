package com.antinet.vpn

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antinet.vpn.ui.AntiNetApp
import com.antinet.vpn.ui.theme.AntiNetTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AntiNetViewModel by viewModels()
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val view = LocalView.current
            AntiNetTheme(settings) {
                val dark = when (settings.themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }
                SideEffect {
                    val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                val pending = sharedText.value
                LaunchedEffect(pending) {
                    if (!pending.isNullOrBlank()) {
                        viewModel.importText(pending)
                        sharedText.value = null
                    }
                }
                AntiNetApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.dataString
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        val candidate = when {
            !data.isNullOrBlank() && data.contains("://") -> data
            !text.isNullOrBlank() -> text
            else -> null
        }
        if (!candidate.isNullOrBlank()) sharedText.value = candidate
    }
}
