//SPDX-License-Identifier: GPL-2.0
package me.phh.sip

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.Settings
import android.os.SystemClock
import android.telephony.Rlog
import android.telephony.ims.ImsManager

class WfcSubscriptionSettingMonitor(
    private val tag: String,
    private val ctxt: Context,
    handler: Handler,
    private val subId: Int,
    private val onWfcDisabled: (String) -> Unit,
    private val onWfcPreferenceChanged: (String, Int?, Int?) -> Unit,
    private val onAirplaneModeDisabled: (String) -> Unit,
) {
    private data class WfcState(
        val enabled: Boolean?,
        val mode: Int?,
    )

    private val simInfoUri = Uri.parse("content://telephony/siminfo")
    @Volatile private var observedWfcEnabled: Boolean? = null
    @Volatile private var observedWfcMode: Int? = null
    @Volatile private var lastObservedChangeUptimeMs: Long = SystemClock.uptimeMillis()

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handleChanged("siminfo observer")
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            handleChanged("siminfo observer uri=$uri")
        }
    }


    private val airplaneModeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                return
            }

            val airplaneOn = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0,
            ) != 0

            if (!airplaneOn) {
                val reason = "airplane mode disabled"
                Rlog.w(tag, "Airplane mode disabled; requesting IMS access refresh")
                handler.post {
                    onAirplaneModeDisabled(reason)
                }
            }
        }
    }

    fun start() {
        try {
            ctxt.registerReceiver(
                airplaneModeReceiver,
                IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            )
        } catch (t: Throwable) {
            Rlog.d(tag, "Failed to register airplane mode receiver", t)
        }

        val state = readState()
        observedWfcEnabled = state.enabled
        observedWfcMode = state.mode
        lastObservedChangeUptimeMs = SystemClock.uptimeMillis()
        Rlog.d(tag, "Initial WFC subscription setting enabled=${state.enabled} mode=${state.mode}")
        try {
            ctxt.contentResolver.registerContentObserver(simInfoUri, true, observer)
            Rlog.d(tag, "Registered WFC subscription setting observer")
        } catch (t: Throwable) {
            Rlog.d(tag, "Registering WFC subscription setting observer failed", t)
        }
    }

    fun stop() {
        try {
            ctxt.unregisterReceiver(airplaneModeReceiver)
        } catch (_: Throwable) {
        }

        try {
            ctxt.contentResolver.unregisterContentObserver(observer)
            Rlog.d(tag, "Unregistered WFC subscription setting observer for subId=$subId")
        } catch (t: Throwable) {
            Rlog.d(tag, "Unregistering WFC subscription setting observer failed", t)
        }
    }

    fun isWifiOnly(): Boolean =
        WfcImsAccessPolicy.isWifiOnly(observedWfcEnabled, observedWfcMode)

    fun isEnabled(): Boolean = observedWfcEnabled == true

    fun modeForDiagnostics(): Int? = observedWfcMode

    fun isWifiPreferredOrOnly(): Boolean =
        WfcImsAccessPolicy.isWifiPreferredOrOnly(observedWfcEnabled, observedWfcMode)

    fun lastChangeUptimeMs(): Long = lastObservedChangeUptimeMs

    private fun readState(): WfcState {
        return try {
            // The telephony provider restricts direct SIMINFO queries to the phone/system UID.
            // A privileged IMS service has an approved per-subscription API for these settings;
            // querying the provider as the app UID throws SecurityException and silently makes
            // WFC appear disabled, preventing the IMS bearer request from ever being made.
            val imsManager = ctxt.getSystemService(ImsManager::class.java)
                ?: return WfcState(enabled = null, mode = null)
            val mmTelManager = imsManager.getImsMmTelManager(subId)
            WfcState(
                enabled = mmTelManager.isVoWiFiSettingEnabled,
                mode = mmTelManager.getVoWiFiModeSetting(),
            )
        } catch (t: Throwable) {
            Rlog.w(tag, "Reading WFC subscription setting via ImsMmTelManager failed subId=$subId", t)
            WfcState(enabled = null, mode = null)
        }
    }

    private fun handleChanged(reason: String) {
        val state = readState()
        val enabled = state.enabled ?: return
        val oldEnabled = observedWfcEnabled
        val oldMode = observedWfcMode
        observedWfcEnabled = enabled
        observedWfcMode = state.mode

        if (oldEnabled == enabled && oldMode == state.mode) return

        lastObservedChangeUptimeMs = SystemClock.uptimeMillis()
        Rlog.d(
            tag,
            "WFC subscription setting changed oldEnabled=$oldEnabled enabled=$enabled " +
                "oldMode=$oldMode mode=${state.mode} reason=$reason",
        )

        if (oldEnabled == true && !enabled) {
            onWfcDisabled(reason)
        }

        if (enabled && (oldEnabled != true || oldMode != state.mode)) {
            onWfcPreferenceChanged(reason, oldMode, state.mode)
        }
    }

}
