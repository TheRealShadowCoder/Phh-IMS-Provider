//SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaCodec
import android.telephony.Rlog

object SipAudioRecordStarter {
    fun startForImsUplink(
        logTag: String,
        audioManager: AudioManager,
        audioRecord: AudioRecord,
        encoder: MediaCodec,
    ): Int? {
        val previousAudioMode = audioManager.mode
        Rlog.i(
            logTag,
            "Using Telecom-managed audio mode=$previousAudioMode; PhhIms will not change global audio mode",
        )
        try {
            audioRecord.startRecording()
        } catch (t: Throwable) {
            Rlog.e(logTag, "AudioRecord.startRecording failed", t)
            try {
                audioRecord.release()
            } catch (_: Throwable) {
            }
            try {
                encoder.stop()
            } catch (_: Throwable) {
            }
            try {
                encoder.release()
            } catch (_: Throwable) {
            }
            return null
        }
        Rlog.d(
            logTag,
            "AudioRecord started, state=${audioRecord.recordingState} " +
                "audioMode=${audioManager.mode} " +
                "preferredDevice=${audioRecord.preferredDevice?.type} " +
                "routedDevice=${audioRecord.routedDevice?.type}"
        )
        return previousAudioMode
    }
}
