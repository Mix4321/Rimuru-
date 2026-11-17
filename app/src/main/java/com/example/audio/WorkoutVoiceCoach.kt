package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Live Thai TTS Audio Coach & Chime Feedback.
 * Speaks rep milestones, HIIT interval transitions, 1km pace splits, and form alerts cleanly without spamming.
 */
class WorkoutVoiceCoach(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75) }.getOrNull()
    private var lastSpokenMillis: Long = 0L

    init {
        runCatching {
            tts = TextToSpeech(appContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val thaiLocale = Locale("th", "TH")
            val result = tts?.setLanguage(thaiLocale) ?: TextToSpeech.LANG_MISSING_DATA
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
            }
            tts?.setSpeechRate(1.02f)
            isReady = true
        }
    }

    fun speak(message: String, priority: Boolean = false, minGapMillis: Long = 2600L) {
        val now = System.currentTimeMillis()
        if (!priority && (now - lastSpokenMillis) < minGapMillis) return
        lastSpokenMillis = now

        if (isReady) {
            runCatching {
                val queueMode = if (priority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(message, queueMode, null, "move_coach_$now")
            }
        }
    }

    fun playTone(isHighChime: Boolean = true) {
        runCatching {
            toneGen?.startTone(
                if (isHighChime) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP,
                160
            )
        }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        runCatching {
            toneGen?.release()
        }
    }
}
