package app.woozy.services

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import app.woozy.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-speech for alerts and confirmations.
 *
 * - Uses the navigation-guidance audio stream and asks other audio (music, podcasts) to duck.
 * - Respects the user's mute / volume / rate settings; [force] bypasses mute (e.g. "Test voice").
 * - Drops an identical phrase repeated within [repeatSuppressMillis] so alerts never loop.
 * - High-priority phrases flush the queue; normal ones are appended.
 */
@Singleton
class VoiceAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    enum class Priority { NORMAL, HIGH }

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Pair<String, Priority>>()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .build()

    private var lastPhrase: String? = null
    private var lastPhraseAt = 0L
    private val repeatSuppressMillis = 15_000L

    /** Last phrase actually spoken, for the "repeat warning" voice command. */
    var lastAnnouncement: String? = null
        private set

    @Synchronized
    fun speak(text: String, priority: Priority = Priority.NORMAL, force: Boolean = false) {
        if (!force && !settingsRepository.settings.value.voiceAlertsEnabled) return
        val now = System.currentTimeMillis()
        if (!force && text == lastPhrase && now - lastPhraseAt < repeatSuppressMillis) return
        lastPhrase = text
        lastPhraseAt = now

        val engine = tts ?: createEngine()
        if (!ready) {
            if (priority == Priority.HIGH) pending.clear()
            pending.addLast(text to priority)
            return
        }
        speakNow(engine, text, priority)
    }

    fun repeatLast(): Boolean {
        val text = lastAnnouncement ?: return false
        speak(text, Priority.HIGH, force = true)
        return true
    }

    private fun createEngine(): TextToSpeech {
        val engine = TextToSpeech(context.applicationContext) { status -> onInit(status) }
        tts = engine
        return engine
    }

    @Synchronized
    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech init failed: $status")
            pending.clear()
            tts = null
            return
        }
        // UI strings are English in this build; match the voice so phrases are pronounced correctly.
        val result = engine.setLanguage(Locale.UK)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
        }
        engine.setAudioAttributes(audioAttributes)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = releaseFocusIfIdle()
            @Deprecated("Deprecated in API") override fun onError(utteranceId: String?) = releaseFocusIfIdle()
            override fun onError(utteranceId: String?, errorCode: Int) = releaseFocusIfIdle()
        })
        ready = true
        while (pending.isNotEmpty()) {
            val (text, priority) = pending.removeFirst()
            speakNow(engine, text, priority)
        }
    }

    private fun speakNow(engine: TextToSpeech, text: String, priority: Priority) {
        val settings = settingsRepository.settings.value
        engine.setSpeechRate(settings.speechRate.ttsRate)
        audioManager?.requestAudioFocus(focusRequest)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.voiceVolume) }
        val mode = if (priority == Priority.HIGH) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        engine.speak(text, mode, params, UUID.randomUUID().toString())
        lastAnnouncement = text
    }

    private fun releaseFocusIfIdle() {
        if (tts?.isSpeaking != true) audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    @Synchronized
    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
        pending.clear()
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }

    private companion object {
        const val TAG = "VoiceAlertManager"
    }
}
