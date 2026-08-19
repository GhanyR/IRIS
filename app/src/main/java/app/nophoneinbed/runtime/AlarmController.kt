package app.nophoneinbed.runtime

import android.media.AudioManager
import android.media.ToneGenerator
import app.nophoneinbed.domain.TrackerState
import java.io.Closeable

interface ToneOutput : Closeable {
    fun startLoop()
    fun stop()
    fun playFault()
}

class AndroidToneOutput : ToneOutput {
    private val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)

    override fun startLoop() {
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD)
    }

    override fun stop() {
        tone.stopTone()
    }

    override fun playFault() {
        tone.startTone(ToneGenerator.TONE_PROP_NACK, 450)
    }

    override fun close() {
        tone.stopTone()
        tone.release()
    }
}

class AlarmController(private val output: ToneOutput) : Closeable {
    private var state = TrackerState.CLEAR
    private var lastFaultToneAtMs: Long? = null

    fun apply(next: TrackerState, nowMs: Long) {
        require(nowMs >= 0)
        if (state == TrackerState.ALARM && next != TrackerState.ALARM) output.stop()
        if (next == TrackerState.ALARM && state != TrackerState.ALARM) output.startLoop()
        if (next == TrackerState.FAULT) {
            val last = lastFaultToneAtMs
            if (last == null || nowMs - last >= FAULT_TONE_INTERVAL_MS) {
                output.playFault()
                lastFaultToneAtMs = nowMs
            }
        }
        state = next
    }

    override fun close() {
        if (state == TrackerState.ALARM) output.stop()
        output.close()
        state = TrackerState.CLEAR
    }

    companion object {
        private const val FAULT_TONE_INTERVAL_MS = 30_000L
    }
}
