package com.example.minimaltodo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.minimaltodo.ui.MainViewModel
import com.example.minimaltodo.ui.navigation.MinimalTodoRoot

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as TodoApplication).container
                MainViewModel(container.todoRepository, container.settingsRepository)
            }
        }
    }

    /** true, wenn diese Activity nur zum Empfangen geteilter Inhalte gestartet wurde. */
    private var launchedForShare = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        launchedForShare = intent?.action == Intent.ACTION_SEND
        if (savedInstanceState == null) handleShareIntent(intent)

        setContent {
            MinimalTodoRoot(
                mainViewModel = mainViewModel,
                onDarkThemeResolved = ::updateSystemBars,
                onShareHandled = ::onShareHandled,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("text/") != true) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val combined = listOfNotNull(
            subject?.takeIf { it.isNotBlank() && text?.contains(it) != true },
            text?.takeIf { it.isNotBlank() },
        ).joinToString("\n")
        mainViewModel.onTextShared(combined)
    }

    /** Nach dem Speichern eines geteilten Inhalts zurück zur teilenden App. */
    private fun onShareHandled(saved: Boolean) {
        if (saved) Toast.makeText(this, R.string.share_in_saved, Toast.LENGTH_SHORT).show()
        if (launchedForShare) {
            launchedForShare = false
            finish()
        }
    }

    private fun updateSystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
