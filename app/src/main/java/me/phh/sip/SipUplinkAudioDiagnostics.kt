// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.media.AudioRecord
import android.os.Build
import android.telephony.Rlog
import java.util.Locale
import kotlin.math.sqrt

internal object SipPcmRms {
    fun normalizedPcm16(buffer: ByteArray, size: Int): Double {
        val safeSize = size.coerceIn(0, buffer.size) and -2
        if (safeSize == 0) return 0.0

        var sumSquares = 0.0
        var sampleCount = 0
        var index = 0
        while (index < safeSize) {
            val sampleBits = (buffer[index].toInt() and 0xff) or
                (buffer[index + 1].toInt() shl 8)
            val sample = sampleBits.toShort().toInt() / 32768.0
            sumSquares += sample * sample
            sampleCount++
            index += 2
        }
        return sqrt(sumSquares / sampleCount)
    }
}

internal object SipUplinkAudioDiagnostics {
    fun logCaptureState(
        logTag: String,
        audioRecord: AudioRecord,
        buffer: ByteArray,
        size: Int,
    ) {
        val recordingConfiguration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                audioRecord.activeRecordingConfiguration
            } catch (t: Throwable) {
                Rlog.w(logTag, "Could not query AudioRecordingConfiguration", t)
                null
            }
        } else {
            null
        }
        val routedDevice = try {
            audioRecord.routedDevice
        } catch (t: Throwable) {
            Rlog.w(logTag, "Could not query AudioRecord routed input", t)
            null
        }
        val rms = SipPcmRms.normalizedPcm16(buffer, size)
        val rmsText = String.format(Locale.US, "%.5f", rms)
        val silenced = recordingConfiguration?.isClientSilenced?.toString() ?: "unknown"
        val source = recordingConfiguration?.clientAudioSource?.toString() ?: "unknown"

        Rlog.i(
            logTag,
            "UPLINK_CAPTURE_DIAGNOSTIC sessionId=${audioRecord.audioSessionId} " +
                "clientSilenced=$silenced clientSource=$source " +
                "routedInput=${routedDevice?.type} routedId=${routedDevice?.id} " +
                "routedName=${routedDevice?.productName} preferredInput=${audioRecord.preferredDevice?.type} " +
                "readBytes=$size pcmSamples=${size.coerceIn(0, buffer.size) / 2} pcmRms=$rmsText",
        )
    }
}
