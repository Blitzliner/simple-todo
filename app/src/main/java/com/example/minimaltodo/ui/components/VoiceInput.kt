package com.example.minimaltodo.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.minimaltodo.R
import com.example.minimaltodo.ui.theme.AppIcons
import com.example.minimaltodo.util.SpeechText
import java.util.Locale

/**
 * Speech-to-Text über die Spracherkennung des Systems (RecognizerIntent).
 * Die App braucht dafür weder RECORD_AUDIO noch INTERNET – Mikrofon und Erkennung
 * laufen in der System-App. Offline-Erkennung wird bevorzugt, sofern das Gerät sie anbietet.
 */
class RecognizeSpeech : ActivityResultContract<String, String?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, input)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null
        return SpeechText.firstResult(intent?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
    }
}

/** Gibt es auf dem Gerät eine Spracherkennung? (benötigt den <queries>-Eintrag im Manifest) */
fun isSpeechInputAvailable(context: Context): Boolean = try {
    @Suppress("DEPRECATION")
    context.packageManager
        .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        .isNotEmpty()
} catch (_: Exception) {
    false
}

/**
 * Liefert eine Funktion, die die Spracheingabe startet – oder null, wenn das Gerät
 * keine Spracherkennung hat (dann wird auch kein Mikrofon-Button angezeigt).
 */
@Composable
fun rememberVoiceInput(onResult: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val available = remember(context) { isSpeechInputAvailable(context) }
    val currentOnResult by rememberUpdatedState(onResult)
    val prompt = stringResource(R.string.voice_prompt)
    val launcher = rememberLauncherForActivityResult(RecognizeSpeech()) { text ->
        if (!text.isNullOrBlank()) currentOnResult(text)
    }
    if (!available) return null
    return {
        try {
            launcher.launch(prompt)
        } catch (_: ActivityNotFoundException) {
            // Spracherkennung wurde zwischenzeitlich deaktiviert – still ignorieren.
        }
    }
}

/** Mikrofon-Button für Textfelder. Erkannter Text wird an [current] angehängt. */
@Composable
fun VoiceInputButton(current: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(current)
    val start = rememberVoiceInput { spoken -> onTextChange(SpeechText.merge(latest, spoken)) } ?: return
    IconButton(onClick = start, modifier = modifier) {
        Icon(AppIcons.Mic, contentDescription = stringResource(R.string.voice_input))
    }
}
