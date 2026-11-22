//SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecord
import android.telephony.Rlog

object SipAudioRecordRouting {
    fun useSystemSelectedInput(
        logTag: String,
        context: Context,
        audioRecord: AudioRecord,
    ): AudioManager {
        val audioManager = context.getSystemService(AudioManager::class.java)
        Rlog.i(
            logTag,
            "Leaving AudioRecord input selection to Android/Telecom: " +
                "preferredDevice=${audioRecord.preferredDevice?.type} " +
                "routedDevice=${audioRecord.routedDevice?.type}",
        )
        return audioManager
    }
}
