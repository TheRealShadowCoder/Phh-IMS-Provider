//SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.content.Context
import android.media.AudioManager
import android.telephony.Rlog

object SipAudioModeRestorer {
    fun restoreAfterImsCall(
        logTag: String,
        context: Context,
        reason: String,
        previousMode: Int? = null
    ) {
        val audioManager = try {
            context.getSystemService(AudioManager::class.java)
        } catch (t: Throwable) {
            Rlog.d(logTag, "Audio mode restore skipped; AudioManager unavailable: $reason", t)
            return
        }

        Rlog.i(
            logTag,
            "Leaving audio mode unchanged; Telecom owns call mode: " +
                "reason=$reason currentMode=${audioManager.mode} previousMode=$previousMode",
        )
    }
}
