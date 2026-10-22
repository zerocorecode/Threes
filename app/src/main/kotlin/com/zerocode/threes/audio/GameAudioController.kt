package com.zerocode.threes.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.zerocode.threes.R

class GameAudioController(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loadedSounds = mutableSetOf<Int>()
    private val recentStreams = ArrayDeque<Int>()
    private val slideSound: Int
    private val clickSound: Int
    private val deepClickSound: Int
    private val mergeSounds: List<Int>
    private val invalidMoveSounds: List<Int>
    private var lastFeedbackSound = 0
    private var feedbackStream = 0
    private var buttonStream = 0

    private var enabled = true
    private var foreground = false
    private var released = false

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { loadedSounds += sampleId }
        }
        slideSound = soundPool.load(context, R.raw.sfx_slide, 1)
        clickSound = soundPool.load(context, R.raw.sfx_click, 1)
        deepClickSound = soundPool.load(context, R.raw.sfx_deep_click, 1)
        mergeSounds = loadSounds(context, R.array.merge_sounds)
        invalidMoveSounds = loadSounds(context, R.array.invalid_move_sounds)
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) stopRecentStreams()
    }

    @Synchronized
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            stopRecentStreams()
            stopButtonStream()
        }
    }

    fun playMove(merged: Boolean) {
        play(slideSound, SLIDE_VOLUME)
        if (merged) playRandomFeedback(mergeSounds, FEEDBACK_VOLUME)
    }

    fun playInvalidMove() {
        playRandomFeedback(invalidMoveSounds, FEEDBACK_VOLUME)
    }

    fun playClick() {
        playButton(clickSound)
    }

    fun playDeepClick(allowWhenDisabled: Boolean = false) {
        playButton(deepClickSound, allowWhenDisabled)
    }

    @Synchronized
    fun release() {
        if (released) return
        released = true
        stopRecentStreams()
        stopButtonStream()
        loadedSounds.clear()
        soundPool.release()
    }

    private fun loadSounds(context: Context, arrayId: Int): List<Int> {
        val resources = context.resources.obtainTypedArray(arrayId)
        return try {
            List(resources.length()) { index ->
                soundPool.load(context, resources.getResourceId(index, 0), 1)
            }
        } finally {
            resources.recycle()
        }
    }

    @Synchronized
    private fun playRandomFeedback(sounds: List<Int>, volume: Float) {
        if (!enabled || !foreground || released) return
        val available = sounds.filter { it in loadedSounds }
        val choices = available.filter { it != lastFeedbackSound }.ifEmpty { available }
        val soundId = choices.randomOrNull() ?: return
        // Keep spoken feedback intelligible during fast swipes and automatic play.
        if (feedbackStream != 0) soundPool.stop(feedbackStream)
        feedbackStream = play(soundId, volume)
        if (feedbackStream != 0) lastFeedbackSound = soundId
    }

    @Synchronized
    private fun play(soundId: Int, volume: Float): Int {
        if (!enabled || !foreground || released || soundId !in loadedSounds) return 0
        val streamId = soundPool.play(soundId, volume, volume, 1, 0, 1f)
        if (streamId != 0) {
            recentStreams.addLast(streamId)
            while (recentStreams.size > MAX_TRACKED_STREAMS) soundPool.stop(recentStreams.removeFirst())
        }
        return streamId
    }

    private fun stopRecentStreams() {
        recentStreams.forEach(soundPool::stop)
        recentStreams.clear()
        if (feedbackStream != 0) soundPool.stop(feedbackStream)
        feedbackStream = 0
    }

    @Synchronized
    private fun playButton(soundId: Int, allowWhenDisabled: Boolean = false) {
        if ((!enabled && !allowWhenDisabled) || !foreground || released || soundId !in loadedSounds) return
        stopButtonStream()
        buttonStream = soundPool.play(soundId, BUTTON_VOLUME, BUTTON_VOLUME, 1, 0, 1f)
    }

    private fun stopButtonStream() {
        if (buttonStream != 0) soundPool.stop(buttonStream)
        buttonStream = 0
    }

    private companion object {
        const val MAX_STREAMS = 4
        const val MAX_TRACKED_STREAMS = 8
        const val SLIDE_VOLUME = 0.40f
        const val FEEDBACK_VOLUME = 0.65f
        const val BUTTON_VOLUME = 0.55f
    }
}
