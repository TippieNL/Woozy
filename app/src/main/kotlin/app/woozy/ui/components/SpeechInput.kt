package app.woozy.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Launches the system speech recogniser and returns the best transcript.
 * Calls [onUnavailable] when the device has no recogniser (the caller should fall back to typing).
 */
@Composable
fun rememberSpeechInput(prompt: String, onResult: (String) -> Unit, onUnavailable: () -> Unit): () -> Unit {
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull { it.isNotBlank() }
                ?.let(currentOnResult)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            currentOnUnavailable()
        }
    }
}
