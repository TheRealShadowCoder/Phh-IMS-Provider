// SPDX-License-Identifier: GPL-2.0
package me.phh.ims

/** Decides when the MMTEL feature can start SIP registration. */
internal object PhhImsReadinessPolicy {
    fun isReady(
        cellularUnavailable: Boolean,
        cellularRegistered: Boolean,
        iwlanRegistered: Boolean,
        wifiInternetAvailable: Boolean,
    ): Boolean {
        if (cellularRegistered) return true

        // IWLAN is a valid IMS access network when cellular service is absent,
        // including ordinary out-of-coverage operation and airplane mode. Do
        // not accept IWLAN alone while cellular is in service: some devices
        // report an early WLAN registration before the IMS bearer is usable.
        return cellularUnavailable && (iwlanRegistered || wifiInternetAvailable)
    }
}
